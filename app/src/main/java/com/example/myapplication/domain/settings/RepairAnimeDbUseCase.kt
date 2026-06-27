package com.example.myapplication.domain.settings

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.domain.addedit.SaveAnimeParams
import com.example.myapplication.domain.addedit.SaveAnimeUseCase
import com.example.myapplication.domain.search.mapApiGenresToTagIds
import com.example.myapplication.domain.search.rating10To5
import com.example.myapplication.network.AnimeDetails
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.sync.TitleMatcher
import kotlinx.coroutines.delay

data class RepairAnimeDbResult(
    val scannedCount: Int,
    val repairedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
)

private enum class RepairPass {
    PRIMARY,
    FALLBACK,
}

/**
 * Проходит по коллекции и заполняет отсутствующие поля (обложка, жанры, рейтинг, ID и пр.)
 * через API с учётом языка приложения.
 *
 * Два прохода:
 * 1. Основной — Shikimori (RU) или AniList/MAL (EN).
 * 2. Fallback — AniList и MAL для оставшихся записей (+ Shikimori без строгого матча).
 */
class RepairAnimeDbUseCase(
    private val repository: AnimeRepository,
    private val saveAnimeUseCase: SaveAnimeUseCase,
    private val imageStorage: ImageStorageRepository,
    private val genreRepository: GenreRepository,
) {
    suspend operator fun invoke(
        language: AppLanguage,
        contentType: AppContentType,
        sessionLog: RepairDbSessionLog,
    ): RepairAnimeDbResult {
        val all = repository.getAllAnimeSnapshot()
        val initiallyNeeding = all.count { detectGaps(it, language).needsRepair }

        sessionLog.info(
            "Start pass 1 (primary): ${all.size} entries, language=$language, " +
                "contentType=$contentType, needRepair=$initiallyNeeding",
        )
        runPass(all, language, contentType, sessionLog, RepairPass.PRIMARY)

        val afterPrimary = repository.getAllAnimeSnapshot()
        val stillNeeding = afterPrimary.filter { detectGaps(it, language).needsRepair }
        if (stillNeeding.isNotEmpty()) {
            sessionLog.info(
                "Start pass 2 (AniList/MAL fallback): ${stillNeeding.size} entries still incomplete",
            )
            runPass(stillNeeding, language, contentType, sessionLog, RepairPass.FALLBACK)
        }

        val finalNeeding = repository.getAllAnimeSnapshot().count { detectGaps(it, language).needsRepair }
        sessionLog.info(
            "Repair done: scanned=${all.size}, repaired=${initiallyNeeding - finalNeeding}, " +
                "failed=$finalNeeding, skipped=${all.size - initiallyNeeding}",
        )

        return RepairAnimeDbResult(
            scannedCount = all.size,
            repairedCount = initiallyNeeding - finalNeeding,
            failedCount = finalNeeding,
            skippedCount = all.size - initiallyNeeding,
        )
    }

    private suspend fun runPass(
        items: List<Anime>,
        language: AppLanguage,
        contentType: AppContentType,
        sessionLog: RepairDbSessionLog,
        pass: RepairPass,
    ) {
        for ((index, anime) in items.withIndex()) {
            val gaps = detectGaps(anime, language)
            if (!gaps.needsRepair) continue

            sessionLog.debug(
                "[${pass.name}] Needs repair \"${anime.title}\": image=${gaps.missingImage}, " +
                    "tags=${gaps.missingTags}, rating=${gaps.missingRating}, id=${gaps.missingExternalId}, " +
                    "type=${gaps.missingCategoryType}, episodes=${gaps.missingEpisodes}",
            )

            val remote = when (pass) {
                RepairPass.PRIMARY -> fetchRemotePrimary(anime, language, contentType, sessionLog)
                RepairPass.FALLBACK -> fetchRemoteFallback(anime, language, sessionLog)
            }

            if (remote == null) {
                sessionLog.warn("[${pass.name}] No API data for \"${anime.title}\"")
            } else {
                sessionLog.debug(
                    "[${pass.name}] API match \"${anime.title}\" -> \"${remote.title}\" via ${remote.source}, " +
                        "genres=${remote.genres}, poster=${remote.posterUrl != null}",
                )
                val changed = runCatching { applyRepair(anime, remote, gaps, sessionLog) }
                    .onFailure { e -> sessionLog.warn("Repair error for \"${anime.title}\"", e) }
                    .getOrDefault(false)
                if (changed) {
                    sessionLog.info("[${pass.name}] Repaired \"${anime.title}\" via ${remote.source}")
                } else {
                    sessionLog.warn("[${pass.name}] Matched \"${anime.title}\" but gaps remain")
                }
            }

            if (index < items.lastIndex) delay(ITEM_DELAY_MS)
        }
    }

    private data class FieldGaps(
        val missingImage: Boolean,
        val missingTags: Boolean,
        val missingRating: Boolean,
        val missingExternalId: Boolean,
        val missingCategoryType: Boolean,
        val missingEpisodes: Boolean,
    ) {
        val needsRepair: Boolean =
            missingImage || missingTags || missingRating || missingExternalId ||
                missingCategoryType || missingEpisodes
    }

    private fun detectGaps(anime: Anime, language: AppLanguage): FieldGaps {
        val hasImage = !anime.imageFileName.isNullOrBlank() &&
            imageStorage.hasLocalImage(anime.imageFileName)
        val missingExternalId = when (language) {
            AppLanguage.RU -> anime.shikimoriId == null
            AppLanguage.EN -> anime.anilistId == null && anime.malId == null
        }
        return FieldGaps(
            missingImage = !hasImage,
            missingTags = anime.tags.isEmpty(),
            missingRating = anime.rating <= 0,
            missingExternalId = missingExternalId,
            missingCategoryType = anime.categoryType.isBlank(),
            missingEpisodes = anime.episodes <= 0,
        )
    }

    private suspend fun fetchRemotePrimary(
        anime: Anime,
        language: AppLanguage,
        contentType: AppContentType,
        sessionLog: RepairDbSessionLog,
    ): ApiSearchResult? {
        val isAnimeEntry = anime.categoryType.isBlank() ||
            anime.categoryType.equals("ANIME", ignoreCase = true)
        if (!isAnimeEntry) {
            val ct = when {
                anime.categoryType.equals("MOVIE", ignoreCase = true) -> AppContentType.MOVIE
                anime.categoryType.equals("SERIES", ignoreCase = true) -> AppContentType.SERIES
                else -> contentType
            }
            val results = repository.searchApi(anime.title, ct, language).getOrNull().orEmpty()
            return pickBestMatch(anime.title, results) ?: results.firstOrNull()
        }

        return when (language) {
            AppLanguage.RU -> fetchFromShikimori(anime, language, sessionLog, strict = true)
            AppLanguage.EN -> fetchFromAniList(anime, language, sessionLog, strict = true)
                ?: fetchFromMal(anime, language, sessionLog, strict = true)
        }
    }

    /** AniList → MAL → Shikimori (relaxed) для оставшихся записей. */
    private suspend fun fetchRemoteFallback(
        anime: Anime,
        language: AppLanguage,
        sessionLog: RepairDbSessionLog,
    ): ApiSearchResult? {
        fetchFromAniList(anime, language, sessionLog, strict = false)?.let { return it }
        fetchFromMal(anime, language, sessionLog, strict = false)?.let { return it }
        return fetchFromShikimori(anime, language, sessionLog, strict = false)
    }

    private suspend fun fetchFromShikimori(
        anime: Anime,
        language: AppLanguage,
        sessionLog: RepairDbSessionLog,
        strict: Boolean,
    ): ApiSearchResult? {
        anime.shikimoriId?.let { id ->
            repository.shikimoriById(id, language).getOrNull()?.let {
                sessionLog.debug("Shikimori byId=$id for \"${anime.title}\"")
                return it
            }
        }

        val results = repository.searchAnimeShikimoriOnly(
            query = anime.title,
            language = language,
            allowZeroEpisodes = true,
        ).getOrNull().orEmpty()

        if (strict) {
            pickBestMatch(anime.title, results)?.let { return it }
            repository.fetchDetails(anime.title, language, isManga = anime.mediaType == com.example.myapplication.data.models.MediaType.MANGA).getOrNull()?.toApiSearchResult()?.let {
                sessionLog.debug("fetchDetails fallback for \"${anime.title}\" via ${it.source}")
                return it
            }
            return null
        }

        return pickRelaxed(anime.title, results).also {
            if (it != null) sessionLog.debug("Shikimori relaxed match for \"${anime.title}\"")
        }
    }

    private suspend fun fetchFromAniList(
        anime: Anime,
        language: AppLanguage,
        sessionLog: RepairDbSessionLog,
        strict: Boolean,
    ): ApiSearchResult? {
        anime.anilistId?.let { id ->
            repository.mediaByAnilistId(id).getOrNull()?.let {
                sessionLog.debug("AniList byId=$id for \"${anime.title}\"")
                return it
            }
        }

        val results = repository.searchAnimeAniListOnly(
            query = anime.title,
            language = language,
            limit = 10,
        ).getOrNull().orEmpty()

        if (strict) {
            pickBestMatch(anime.title, results)?.let { return it }
            repository.fetchDetails(anime.title, language, isManga = anime.mediaType == com.example.myapplication.data.models.MediaType.MANGA).getOrNull()?.toApiSearchResult()?.let {
                sessionLog.debug("fetchDetails fallback for \"${anime.title}\" via ${it.source}")
                return it
            }
            return null
        }

        return pickRelaxed(anime.title, results).also {
            if (it != null) sessionLog.debug("AniList relaxed match for \"${anime.title}\"")
        }
    }

    private suspend fun fetchFromMal(
        anime: Anime,
        language: AppLanguage,
        sessionLog: RepairDbSessionLog,
        strict: Boolean,
    ): ApiSearchResult? {
        anime.malId?.let { id ->
            repository.malById(id, language).getOrNull()?.let {
                sessionLog.debug("MAL byId=$id for \"${anime.title}\"")
                return it
            }
        }

        val results = repository.searchAnimeMalOnly(
            query = anime.title,
            language = language,
            limit = 10,
        ).getOrNull().orEmpty()

        if (strict) {
            return pickBestMatch(anime.title, results)
        }

        return pickRelaxed(anime.title, results).also {
            if (it != null) sessionLog.debug("MAL relaxed match for \"${anime.title}\"")
        }
    }

    /** @return true если в БД реально записаны новые данные */
    private suspend fun applyRepair(
        anime: Anime,
        remote: ApiSearchResult,
        gaps: FieldGaps,
        sessionLog: RepairDbSessionLog,
    ): Boolean {
        var changed = false

        val imageFileName = when {
            !gaps.missingImage -> anime.imageFileName
            else -> {
                val url = remote.posterUrl
                if (url.isNullOrBlank()) {
                    sessionLog.warn("No poster URL for \"${anime.title}\"")
                    anime.imageFileName
                } else {
                    imageStorage.saveImageFromUrl(url, anime.id).fold(
                        onSuccess = { name ->
                            changed = true
                            sessionLog.debug("Poster saved for \"${anime.title}\": $name")
                            name
                        },
                        onFailure = { e ->
                            sessionLog.warn("Poster download failed for \"${anime.title}\": $url", e)
                            anime.imageFileName
                        },
                    )
                }
            }
        }

        val tags = if (gaps.missingTags) {
            mapApiGenresToTagIds(remote.genres, genreRepository).also { mapped ->
                if (mapped.isNotEmpty()) {
                    changed = true
                    sessionLog.debug("Tags mapped for \"${anime.title}\": $mapped")
                } else if (remote.genres.isNotEmpty()) {
                    sessionLog.warn(
                        "Genres from API not mapped for \"${anime.title}\": ${remote.genres}",
                    )
                }
            }
        } else {
            anime.tags
        }

        val rating = if (gaps.missingRating) {
            val rating10 = (remote.rating ?: 0).let { if (it > 10) it / 10 else it }
            if (rating10 > 0) {
                changed = true
                rating10To5(rating10)
            } else {
                anime.rating
            }
        } else {
            anime.rating
        }

        val episodes = if (gaps.missingEpisodes && remote.episodes > 0) {
            changed = true
            remote.episodes
        } else if (gaps.missingEpisodes) {
            changed = true
            1
        } else {
            anime.episodes
        }

        val categoryType = if (gaps.missingCategoryType) {
            changed = true
            remote.categoryType.ifBlank { "ANIME" }
        } else {
            anime.categoryType
        }

        val (anilistId, malId, shikimoriId) = externalIdsFrom(remote)
        val newAnilistId = anime.anilistId ?: anilistId
        val newMalId = anime.malId ?: malId
        val newShikimoriId = anime.shikimoriId ?: shikimoriId
        if (gaps.missingExternalId &&
            (newAnilistId != anime.anilistId || newMalId != anime.malId || newShikimoriId != anime.shikimoriId)
        ) {
            changed = true
        }

        if (!changed) return false

        val params = SaveAnimeParams(
            animeId = anime.id,
            title = anime.title,
            episodes = episodes,
            rating = rating,
            imageUri = null,
            currentImageFileName = imageFileName,
            orderIndex = anime.orderIndex,
            dateAdded = anime.dateAdded,
            isFavorite = anime.isFavorite,
            selectedTags = tags,
            categoryType = categoryType,
            comment = anime.comment,
            anilistId = newAnilistId,
            malId = newMalId,
            shikimoriId = newShikimoriId,
            anilistNotFoundAt = anime.anilistNotFoundAt,
            malNotFoundAt = anime.malNotFoundAt,
            shikimoriNotFoundAt = anime.shikimoriNotFoundAt,
        )
        saveAnimeUseCase(params).getOrThrow()
        return true
    }

    private fun externalIdsFrom(result: ApiSearchResult): Triple<Int?, Int?, Int?> {
        val extId = result.externalId?.toIntOrNull() ?: return Triple(null, null, null)
        return when {
            result.source.equals("Shikimori", ignoreCase = true) -> Triple(null, null, extId)
            result.source.equals("AniList", ignoreCase = true) -> Triple(extId, null, null)
            result.source.equals("MAL", ignoreCase = true) ||
                result.source.equals("Jikan", ignoreCase = true) -> Triple(null, extId, null)
            else -> Triple(null, null, null)
        }
    }

    private fun pickBestMatch(localTitle: String, results: List<ApiSearchResult>): ApiSearchResult? {
        if (results.isEmpty()) return null
        return results
            .map { result ->
                val candidates = listOfNotNull(result.title, result.altTitle)
                result to TitleMatcher.bestScore(localTitle, candidates)
            }
            .filter { (_, score) -> score >= TitleMatcher.MATCH_THRESHOLD }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun pickRelaxed(localTitle: String, results: List<ApiSearchResult>): ApiSearchResult? {
        if (results.isEmpty()) return null
        return pickBestMatch(localTitle, results) ?: results.firstOrNull()
    }

    private fun AnimeDetails.toApiSearchResult(): ApiSearchResult = ApiSearchResult(
        title = title,
        altTitle = altTitle,
        posterUrl = posterUrl,
        episodes = episodesAired.coerceAtLeast(1),
        description = description,
        type = type,
        genres = genres,
        rating = rating,
        source = source,
        categoryType = "ANIME",
        externalId = null,
    )

    private companion object {
        private const val ITEM_DELAY_MS = 450L
    }
}
