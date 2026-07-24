package com.example.myapplication.domain.seasons

import android.util.Log
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.local.SeasonEpisodesStore
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.EpisodeCheckMedia
import com.example.myapplication.sync.TitleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Резолвер «серии по сезонам»: для каждого тайтла строит упорядоченную цепочку сезонов
 * франшизы и гарантированно достаёт число серий каждого сезона.
 *
 * Каскад (от дешёвого к дорогому, язык не важен):
 *  1. Сид-узел AniList: по anilistId → по malId/shikimoriId (idMal_in) → поиском по названиям
 *     (AniList → Shikimori → MAL, сопоставление TitleMatcher ≥ 0.91). Найденные id пишутся в БД.
 *  2. Цепочка: BFS по PREQUEL/SEQUEL (батчи episodeCheckByAnilistIds), только сезонные форматы
 *     (TV/TV_SHORT/ONA), порядок восстанавливается по рёбрам.
 *  3. Число серий узла: AniList totalEpisodes → airedEpisodes; дырки дозаполняются точечными
 *     Shikimori byId → MAL byId (Shikimori id ≡ MAL id).
 *  4. Совсем без AniList-узла (нет ни id, ни совпадений) — одиночный «сезон 1» из данных
 *     Shikimori/MAL самой записи, чтобы хоть что-то показать.
 *
 * Неполные записи (сезон без счётчика или онгоинг) хранятся с коротким TTL — следующий фоновый
 * проход дорезолвит. Вызывается из AnimeUpdateWorker после проверки новых серий.
 */
class SeasonEpisodesResolver(
    private val repository: AnimeRepository,
    private val localDataSource: AnimeLocalDataSource,
    private val store: SeasonEpisodesStore,
) {

    /**
     * Фоновый проход: перерезолвить протухшие записи коллекции (бюджет [budget] тайтлов
     * за проход, чтобы не душить API). Заодно чистит записи удалённых тайтлов.
     */
    suspend fun refreshStale(budget: Int = DEFAULT_BUDGET) {
        store.ensureLoaded()
        val animeOnly = localDataSource.getAllAnimeList().filter { it.mediaType == MediaType.ANIME }
        runCatching { store.retainOnly(animeOnly.map { it.id }.toSet()) }

        var resolved = 0
        for (anime in animeOnly) {
            if (resolved >= budget) break
            if (store.isFresh(anime.id)) continue
            val entry = runCatching { resolve(anime) }
                .onFailure { Log.w(TAG, "resolve failed for \"${anime.title}\": ${it.message}") }
                .getOrNull()
            if (entry != null) {
                store.put(entry)
                resolved++
                Log.i(TAG, "Seasons for \"${anime.title}\": ${entry.seasons.map { "S${it.seasonNumber}=${it.episodes}" }} complete=${entry.complete}")
            }
            delay(ITEM_DELAY_MS)
        }
    }

    /**
     * Точечный резолв одного тайтла вне очереди (открытие Details): если запись протухла
     * или отсутствует — резолвим сразу, UI обновится реактивно через стор.
     */
    suspend fun ensureResolved(animeId: String) = withContext(Dispatchers.IO) {
        store.ensureLoaded()
        if (store.isFresh(animeId)) return@withContext
        val anime = localDataSource.getAnimeById(animeId) ?: return@withContext
        if (anime.mediaType != MediaType.ANIME) return@withContext
        runCatching { resolve(anime) }
            .onFailure { Log.w(TAG, "on-demand resolve failed for \"${anime.title}\": ${it.message}") }
            .getOrNull()
            ?.let { store.put(it) }
    }

    /** Полный резолв одного тайтла. null = сид не найден и фолбэки пусты (перепробуем позже). */
    suspend fun resolve(anime: Anime): SeasonEpisodesEntry? {
        val self = findSeedNode(anime)
            ?: return fallbackSingleSeason(anime)

        val nodes = gatherFranchise(self)
        val ordered = orderByRelations(nodes.filterValues { it.isSeasonFormat() })
            .ifEmpty { listOf(self) }

        val seasons = ArrayList<SeasonInfo>(ordered.size)
        var allResolved = true
        for ((index, node) in ordered.withIndex()) {
            val ongoing = node.status == "RELEASING"
            var episodes = node.totalEpisodes ?: node.airedEpisodes.takeIf { it > 0 }
            var source = "AniList"

            // Дырка у AniList (анонс без числа серий и т.п.) — точечно спрашиваем Shikimori/MAL.
            if (episodes == null && node.malId != null) {
                fillFromShikimoriOrMal(node.malId!!)?.let { (eps, src) ->
                    episodes = eps
                    source = src
                }
            }

            if (episodes == null) {
                allResolved = false
                continue
            }
            seasons += SeasonInfo(
                seasonNumber = index + 1,
                episodes = episodes!!,
                ongoing = ongoing,
                anilistId = node.anilistId,
                malId = node.malId,
                source = source,
                title = node.titleEnglish ?: node.titleRomaji,
            )
        }
        if (seasons.isEmpty()) return fallbackSingleSeason(anime)

        // Пропуск в середине цепочки съедает нумерацию — перенумеруем по порядку.
        val renumbered = seasons.mapIndexed { i, s -> s.copy(seasonNumber = i + 1) }
        return SeasonEpisodesEntry(
            animeId = anime.id,
            seasons = renumbered,
            complete = allResolved && renumbered.none { it.ongoing },
            resolvedAt = System.currentTimeMillis(),
        )
    }

    // ==========================================================
    // Сид: AniList-узел записи
    // ==========================================================

    private suspend fun findSeedNode(anime: Anime): EpisodeCheckMedia? {
        anime.anilistId?.let { id ->
            retry429 { repository.episodeCheckByAnilistIds(listOf(id)) }
                .getOrNull()?.firstOrNull()?.let { return it }
        }
        (anime.malId ?: anime.shikimoriId)?.let { mal ->
            retry429 { repository.episodeCheckByMalIds(listOf(mal)) }
                .getOrNull()?.firstOrNull()?.let {
                    persistIds(anime, it)
                    return it
                }
        }
        return seedByTitleSearch(anime)
    }

    /** Поиск сида по названиям: AniList → Shikimori → MAL; совпадение ≥ [MATCH_SCORE]. */
    private suspend fun seedByTitleSearch(anime: Anime): EpisodeCheckMedia? {
        val queries = listOfNotNull(
            anime.title.takeIf { it.isNotBlank() },
            anime.titleEn?.takeIf { it.isNotBlank() },
            anime.titleRu?.takeIf { it.isNotBlank() },
        ).distinctBy { it.lowercase() }
        if (queries.isEmpty()) return null

        // AniList: латиница.
        for (q in queries.filter { it.hasLatin() }) {
            val found = retry429 { repository.searchAnimeAniListOnly(q, AppLanguage.EN, limit = 5) }
                .getOrNull()?.let { pickMatch(anime, it) } ?: continue
            val anilistId = found.externalId?.toIntOrNull() ?: continue
            delay(ITEM_DELAY_MS)
            return retry429 { repository.episodeCheckByAnilistIds(listOf(anilistId)) }
                .getOrNull()?.firstOrNull()?.also { persistIds(anime, it) }
        }
        // Shikimori: понимает кириллицу; отдаёт malId.
        for (q in queries) {
            val found = retry429 { repository.searchAnimeShikimoriOnly(q, AppLanguage.RU, allowZeroEpisodes = true) }
                .getOrNull()?.let { pickMatch(anime, it) } ?: continue
            val mal = found.malId ?: found.externalId?.toIntOrNull() ?: continue
            delay(ITEM_DELAY_MS)
            retry429 { repository.episodeCheckByMalIds(listOf(mal)) }
                .getOrNull()?.firstOrNull()?.let {
                    persistIds(anime, it)
                    return it
                }
        }
        // MAL (Jikan): латиница.
        for (q in queries.filter { it.hasLatin() }) {
            val found = retry429 { repository.searchAnimeMalOnly(q, AppLanguage.EN, limit = 5) }
                .getOrNull()?.let { pickMatch(anime, it) } ?: continue
            val mal = found.externalId?.toIntOrNull() ?: continue
            delay(ITEM_DELAY_MS)
            retry429 { repository.episodeCheckByMalIds(listOf(mal)) }
                .getOrNull()?.firstOrNull()?.let {
                    persistIds(anime, it)
                    return it
                }
        }
        return null
    }

    /**
     * AniList недоступен для тайтла вовсе — одиночный «сезон 1» из родных данных
     * Shikimori/MAL (byId, потом поиск), чтобы запись не оставалась пустой.
     */
    private suspend fun fallbackSingleSeason(anime: Anime): SeasonEpisodesEntry? {
        val mal = anime.malId ?: anime.shikimoriId
        if (mal != null) {
            fillFromShikimoriOrMal(mal)?.let { (eps, src) ->
                return singleSeasonEntry(anime, eps, src)
            }
        }
        // Последний рубеж: Shikimori-поиск (кириллица дружелюбна) — счётчик из выдачи.
        val queries = listOfNotNull(
            anime.title.takeIf { it.isNotBlank() },
            anime.titleRu?.takeIf { it.isNotBlank() },
            anime.titleEn?.takeIf { it.isNotBlank() },
        ).distinctBy { it.lowercase() }
        for (q in queries) {
            val found = retry429 { repository.searchAnimeShikimoriOnly(q, AppLanguage.RU, allowZeroEpisodes = true) }
                .getOrNull()?.let { pickMatch(anime, it) } ?: continue
            val eps = found.totalEpisodes ?: found.airedEpisodes ?: found.episodes.takeIf { it > 0 } ?: continue
            return singleSeasonEntry(anime, eps, "Shikimori", ongoing = found.isOngoing == true)
        }
        return null
    }

    private fun singleSeasonEntry(
        anime: Anime,
        episodes: Int,
        source: String,
        ongoing: Boolean = false,
    ): SeasonEpisodesEntry = SeasonEpisodesEntry(
        animeId = anime.id,
        seasons = listOf(
            SeasonInfo(
                seasonNumber = 1,
                episodes = episodes,
                ongoing = ongoing,
                anilistId = anime.anilistId,
                malId = anime.malId ?: anime.shikimoriId,
                source = source,
                title = null,
            ),
        ),
        // Одиночный сезон без графа франшизы полным не считаем: пусть следующий проход
        // попробует построить цепочку (id могли дозаполниться другими фичами).
        complete = false,
        resolvedAt = System.currentTimeMillis(),
    )

    // ==========================================================
    // Дозаполнение серий сезона: Shikimori → MAL (по malId)
    // ==========================================================

    private suspend fun fillFromShikimoriOrMal(malId: Int): Pair<Int, String>? {
        delay(ITEM_DELAY_MS)
        retry429 { repository.shikimoriById(malId, AppLanguage.RU) }.getOrNull()?.let { r ->
            val eps = r.totalEpisodes ?: r.airedEpisodes ?: r.episodes.takeIf { it > 0 }
            if (eps != null && eps > 0) return eps to "Shikimori"
        }
        delay(ITEM_DELAY_MS)
        retry429 { repository.malById(malId, AppLanguage.EN) }.getOrNull()?.let { r ->
            val eps = r.totalEpisodes ?: r.airedEpisodes ?: r.episodes.takeIf { it > 0 }
            if (eps != null && eps > 0) return eps to "MAL"
        }
        return null
    }

    // ==========================================================
    // Франшиза: BFS + порядок (по образцу FranchiseEpisodeMapper)
    // ==========================================================

    private suspend fun gatherFranchise(self: EpisodeCheckMedia): Map<Int, EpisodeCheckMedia> {
        val result = LinkedHashMap<Int, EpisodeCheckMedia>()
        result[self.anilistId] = self
        var frontier = self.seasonNeighbors() - result.keys
        var level = 0
        while (frontier.isNotEmpty() && result.size < MAX_NODES && level++ < MAX_LEVELS) {
            val fetched = retry429 { repository.episodeCheckByAnilistIds(frontier.toList()) }
                .getOrNull().orEmpty()
            val next = HashSet<Int>()
            for (m in fetched) {
                if (!m.isSeasonFormat()) continue
                result[m.anilistId] = m
                next += m.seasonNeighbors()
            }
            frontier = next - result.keys
        }
        return result
    }

    private fun orderByRelations(nodes: Map<Int, EpisodeCheckMedia>): List<EpisodeCheckMedia> {
        if (nodes.isEmpty()) return emptyList()
        val next = HashMap<Int, Int>()
        val prev = HashMap<Int, Int>()
        for (m in nodes.values) {
            for (r in m.relations) {
                if (r.anilistId !in nodes) continue
                when (r.relationType) {
                    "SEQUEL" -> { next[m.anilistId] = r.anilistId; prev[r.anilistId] = m.anilistId }
                    "PREQUEL" -> { prev[m.anilistId] = r.anilistId; next[r.anilistId] = m.anilistId }
                }
            }
        }
        val root = nodes.keys.firstOrNull { it !in prev } ?: nodes.keys.first()
        val ordered = ArrayList<EpisodeCheckMedia>()
        val seen = HashSet<Int>()
        var cur: Int? = root
        while (cur != null && cur !in seen) {
            seen += cur
            nodes[cur]?.let { ordered += it }
            cur = next[cur]
        }
        nodes.values.filter { it.anilistId !in seen }.sortedBy { it.anilistId }.forEach { ordered += it }
        return ordered
    }

    private fun EpisodeCheckMedia.seasonNeighbors(): Set<Int> =
        relations.filter { it.relationType == "PREQUEL" || it.relationType == "SEQUEL" }
            .map { it.anilistId }
            .toSet()

    private fun EpisodeCheckMedia.isSeasonFormat(): Boolean =
        format == null || format in SEASON_FORMATS

    // ==========================================================
    // Сопоставление и сетевые мелочи
    // ==========================================================

    private fun localTitles(anime: Anime): List<String> =
        listOfNotNull(anime.title, anime.titleEn, anime.titleRu).filter { it.isNotBlank() }

    private fun pickMatch(anime: Anime, results: List<ApiSearchResult>?): ApiSearchResult? =
        results.orEmpty()
            .map { candidate ->
                val remotes = listOfNotNull(candidate.title, candidate.altTitle)
                candidate to (localTitles(anime).maxOfOrNull { TitleMatcher.bestScore(it, remotes) } ?: 0.0)
            }
            .filter { (_, score) -> score >= MATCH_SCORE }
            .maxByOrNull { (_, score) -> score }
            ?.first

    /** Найденные id — в БД: остальные фичи (проверка серий, AniSkip) пойдут быстрой веткой. */
    private suspend fun persistIds(anime: Anime, media: EpisodeCheckMedia) {
        runCatching {
            if (anime.anilistId == null) localDataSource.setAnilistId(anime.id, media.anilistId)
            val mal = media.malId
            if (anime.malId == null && mal != null) localDataSource.setMalId(anime.id, mal)
        }
    }

    private suspend fun <T> retry429(
        maxAttempts: Int = MAX_ATTEMPTS,
        block: suspend () -> Result<T>,
    ): Result<T> {
        var delayMs = RETRY_BASE_DELAY_MS
        var attempt = 1
        var last: Result<T> = Result.failure(IllegalStateException("No attempts executed"))
        while (attempt <= maxAttempts) {
            last = block()
            if (last.isSuccess) return last
            val is429 = last.exceptionOrNull()?.message?.contains("429", ignoreCase = true) == true
            if (!is429 || attempt == maxAttempts) return last
            delay(delayMs)
            delayMs *= 2
            attempt++
        }
        return last
    }

    private fun String.hasLatin(): Boolean = any { it in 'a'..'z' || it in 'A'..'Z' }

    private companion object {
        const val TAG = "SeasonEpisodes"
        const val DEFAULT_BUDGET = 8
        const val ITEM_DELAY_MS = 350L
        const val MATCH_SCORE = 0.91
        const val MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 800L
        const val MAX_NODES = 16
        const val MAX_LEVELS = 8
        val SEASON_FORMATS = setOf("TV", "TV_SHORT", "ONA")
    }
}
