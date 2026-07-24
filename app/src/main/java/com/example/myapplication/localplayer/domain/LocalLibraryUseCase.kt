package com.example.myapplication.localplayer.domain

import android.content.Context
import android.net.Uri
import com.example.myapplication.localplayer.data.LocalSourceStore
import com.example.myapplication.localplayer.model.LocalEpisode
import com.example.myapplication.localplayer.model.LocalSource
import java.util.Locale

/**
 * Оркестратор локальной библиотеки одного тайтла: SAF-скан → разбор номеров (алгоритм) →
 * ИИ-фолбэк для нераспознанного → сборка отсортированного списка с переименованными подписями →
 * сохранение в [LocalSourceStore].
 *
 * Изоляция: зависит только от собственных доменных классов + общего [AiLlmFallbackRouter]
 * (через [AiEpisodeResolver]) и [LocalSourceStore]. Ничего в остальном приложении не меняет.
 */
class LocalLibraryUseCase(
    private val context: Context,
    private val store: LocalSourceStore,
    aiRouter: com.example.myapplication.data.ai.AiLlmFallbackRouter,
) {
    private val guard = GeneralFolderGuard()
    private val scanner = EpisodeScanner(context)
    private val parser = EpisodeFilenameParser()
    private val metadataReader = FileMetadataReader(context)
    private val aiResolver = AiEpisodeResolver(aiRouter)

    sealed interface Result {
        data class Success(val source: LocalSource, val usedAi: Boolean) : Result
        data object TooGeneralFolder : Result
        data object NoVideoFiles : Result
    }

    suspend fun build(animeId: String, animeTitle: String, treeUri: Uri): Result {
        if (guard.isTooGeneral(treeUri)) return Result.TooGeneralFolder

        val scanned = scanner.scan(treeUri)
        if (scanned.isEmpty()) return Result.NoVideoFiles

        val slug = slugify(animeTitle)

        // 1) Алгоритм: номер + сезон из имени файла.
        val byName = scanned.associateWith { parser.parse(it.name) }

        // 2) Метаданные контейнера — фолбэк для файлов, которым имя не дало номер (название/теги).
        val byMeta = HashMap<ScannedFile, EpisodeFilenameParser.ParsedFilename>()
        for (f in scanned) {
            if (byName[f]?.episodeNumber != null) continue
            val meta = metadataReader.read(f.uri)
            val fromTitle = meta.title?.let { parser.parse(it) }
            val episode = fromTitle?.episodeNumber ?: meta.episode
            val season = fromTitle?.season ?: meta.season
            if (episode != null || season != null) {
                byMeta[f] = EpisodeFilenameParser.ParsedFilename(episode, season, fromTitle?.cleanTitle)
            }
        }

        fun episodeOf(f: ScannedFile): Int? = byName[f]?.episodeNumber ?: byMeta[f]?.episodeNumber
        fun seasonOf(f: ScannedFile): Int? = byName[f]?.season ?: byMeta[f]?.season

        // 3) ИИ-фолбэк только для всё ещё нераспознанных.
        val unresolved = scanned.filter { episodeOf(it) == null }.map { it.name }
        var usedAi = false
        val aiNumbers: Map<String, Int> = if (unresolved.isNotEmpty()) {
            when (val out = aiResolver.resolve(animeTitle, unresolved)) {
                is AiEpisodeResolver.Outcome.Resolved -> {
                    usedAi = out.numbers.isNotEmpty()
                    out.numbers
                }
                else -> emptyMap()
            }
        } else {
            emptyMap()
        }

        // Показывать сезон в подписи, если папка мультисезонная (есть сезон > 1 или несколько сезонов).
        val seasons = scanned.mapNotNull { seasonOf(it) }
        val multiSeason = seasons.any { it > 1 } || seasons.distinct().size > 1

        // 4) Сборка + сортировка: сезон, затем номер; неопределённые — в конце по имени.
        val episodes = scanned.map { f ->
            val number = episodeOf(f) ?: aiNumbers[f.name]
            val season = seasonOf(f)
            LocalEpisode(
                documentUri = f.uri.toString(),
                originalName = f.name,
                displayName = buildDisplayName(slug, season, number, multiSeason),
                episodeNumber = number,
                season = season,
                sizeBytes = f.sizeBytes,
            )
        }.sortedWith(
            compareBy(
                { it.episodeNumber == null },
                { it.season ?: 1 },
                { it.episodeNumber ?: Int.MAX_VALUE },
                { it.originalName.lowercase() },
            ),
        )

        val source = LocalSource(
            animeId = animeId,
            treeUri = treeUri.toString(),
            folderLabel = folderLabel(treeUri),
            episodes = episodes,
            updatedAt = System.currentTimeMillis(),
        )
        store.put(source)
        return Result.Success(source, usedAi)
    }

    private fun buildDisplayName(slug: String, season: Int?, number: Int?, multiSeason: Boolean): String = when {
        number == null -> slug
        multiSeason && season != null -> String.format(Locale.US, "%s_[s%02de%02d]", slug, season, number)
        else -> String.format(Locale.US, "%s_[ep.%02d]", slug, number)
    }

    private fun slugify(title: String): String =
        title.trim().lowercase(Locale.US)
            .replace(Regex("""[^a-zа-я0-9]+"""), "_")
            .trim('_')
            .ifEmpty { "episode" }

    private fun folderLabel(treeUri: Uri): String {
        val decoded = Uri.decode(treeUri.toString())
        val afterColon = decoded.substringAfterLast(':', "")
        return afterColon.substringAfterLast('/').ifEmpty { afterColon.ifEmpty { "Local folder" } }
    }
}
