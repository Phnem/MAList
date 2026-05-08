package com.example.myapplication.domain.updates

import android.util.Log
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.domain.normalizeForSearch
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.sync.TitleMatcher
import kotlinx.coroutines.delay

class BatchEpisodeCheckUseCase(
    private val repository: AnimeRepository,
    private val localDataSource: AnimeLocalDataSource
) {

    suspend operator fun invoke(
        animeList: List<Anime>,
        language: AppLanguage
    ): List<AnimeUpdate> {
        return when (language) {
            AppLanguage.RU -> buildUpdatesRu(animeList)
            AppLanguage.EN -> buildUpdatesEn(animeList)
        }
    }

    private suspend fun buildUpdatesRu(animeList: List<Anime>): List<AnimeUpdate> {
        val updates = mutableListOf<AnimeUpdate>()
        for (chunk in animeList.chunked(CHUNK_SIZE)) {
            for (anime in chunk) {
                val remote = resolveShikimori(anime) ?: continue
                if (remote.episodes > anime.episodes) {
                    Log.d(TAG, "Update detected [RU]: ${anime.title} ${anime.episodes} -> ${remote.episodes} via ${remote.source}")
                    updates += AnimeUpdate(
                        animeId = anime.id,
                        title = anime.title,
                        currentEpisodes = anime.episodes,
                        newEpisodes = remote.episodes,
                        source = remote.source
                    )
                }
            }
            delay(CHUNK_DELAY_MS)
        }
        return updates
    }

    private suspend fun buildUpdatesEn(animeList: List<Anime>): List<AnimeUpdate> {
        val updates = mutableListOf<AnimeUpdate>()
        val unresolved = mutableListOf<Anime>()

        for (chunk in animeList.chunked(CHUNK_SIZE)) {
            for (anime in chunk) {
                when (val result = resolveAniList(anime)) {
                    is ResolveResult.Found -> {
                        if (result.remote.episodes > anime.episodes) {
                            Log.d(TAG, "Update detected [EN]: ${anime.title} ${anime.episodes} -> ${result.remote.episodes} via ${result.remote.source}")
                            updates += AnimeUpdate(
                                animeId = anime.id,
                                title = anime.title,
                                currentEpisodes = anime.episodes,
                                newEpisodes = result.remote.episodes,
                                source = result.remote.source
                            )
                        }
                    }
                    ResolveResult.NotFound -> unresolved += anime
                    ResolveResult.Skipped -> Unit
                }
            }
            delay(CHUNK_DELAY_MS)
        }

        for (chunk in unresolved.chunked(MAL_CHUNK_SIZE)) {
            for (anime in chunk) {
                val remote = resolveMalFallback(anime) ?: continue
                if (remote.episodes > anime.episodes) {
                    Log.d(TAG, "Update detected [EN fallback]: ${anime.title} ${anime.episodes} -> ${remote.episodes} via ${remote.source}")
                    updates += AnimeUpdate(
                        animeId = anime.id,
                        title = anime.title,
                        currentEpisodes = anime.episodes,
                        newEpisodes = remote.episodes,
                        source = remote.source
                    )
                }
                delay(MAL_ITEM_DELAY_MS)
            }
            delay(MAL_CHUNK_DELAY_MS)
        }

        return updates
    }

    private suspend fun resolveAniList(anime: Anime): ResolveResult {
        anime.anilistId?.let { id ->
            val byIdResult = retry429 { repository.mediaByAnilistId(id) }
            byIdResult.getOrNull()?.let { remote ->
                val matched = remote.episodes > 0 && isTitleMatch(anime.title, remote)
                Log.d(TAG, "AniList byId: local=\"${anime.title}\" remote=\"${remote.title}\" eps=${remote.episodes} matched=$matched")
                if (matched) return ResolveResult.Found(RemoteEpisodes(remote.episodes, "AniList"))
            }

            if (byIdResult.isFailure && isTransientFailure(byIdResult.exceptionOrNull())) {
                return ResolveResult.Skipped
            }
        }

        if (!shouldRetryNotFound(anime.anilistNotFoundAt)) return ResolveResult.NotFound

        val foundResult = retry429 {
            repository.searchAnimeAniListOnly(query = anime.title, language = AppLanguage.EN, limit = 5)
        }
        val found = pickMatchedResult(anime.title, foundResult.getOrNull())
            ?.takeIf { it.result.episodes > 0 }

        return if (found != null) {
            found.result.externalId?.toIntOrNull()?.let { localDataSource.setAnilistId(anime.id, it) }
            if (found.score >= DIRECT_NOTIFY_FROM_SEARCH_SCORE) {
                ResolveResult.Found(RemoteEpisodes(found.result.episodes, "AniList"))
            } else {
                ResolveResult.Skipped
            }
        } else if (foundResult.isFailure && isTransientFailure(foundResult.exceptionOrNull())) {
            ResolveResult.Skipped
        } else {
            localDataSource.markAnilistNotFound(anime.id, System.currentTimeMillis())
            ResolveResult.NotFound
        }
    }

    private suspend fun resolveShikimori(anime: Anime): RemoteEpisodes? {
        anime.shikimoriId?.let { id ->
            val byIdResult = retry429 { repository.shikimoriById(id, AppLanguage.RU) }
            byIdResult.getOrNull()?.let { remote ->
                val matched = remote.episodes > 0 && isTitleMatch(anime.title, remote)
                Log.d(TAG, "Shikimori byId: local=\"${anime.title}\" remote=\"${remote.title}\" eps=${remote.episodes} matched=$matched")
                if (matched) return RemoteEpisodes(remote.episodes, "Shikimori")
            }
            if (byIdResult.isFailure && isTransientFailure(byIdResult.exceptionOrNull())) {
                return null
            }
        }

        if (!shouldRetryNotFound(anime.shikimoriNotFoundAt)) return null

        val foundResult = retry429 {
            repository.searchAnimeShikimoriOnly(query = anime.title, language = AppLanguage.RU, allowZeroEpisodes = true)
        }
        val found = pickMatchedResult(anime.title, foundResult.getOrNull())
            ?.takeIf { it.result.episodes > 0 }

        return if (found != null) {
            found.result.externalId?.toIntOrNull()?.let { localDataSource.setShikimoriId(anime.id, it) }
            if (found.score >= DIRECT_NOTIFY_FROM_SEARCH_SCORE) {
                RemoteEpisodes(found.result.episodes, "Shikimori")
            } else {
                null
            }
        } else if (foundResult.isFailure && isTransientFailure(foundResult.exceptionOrNull())) {
            null
        } else {
            localDataSource.markShikimoriNotFound(anime.id, System.currentTimeMillis())
            null
        }
    }

    private suspend fun resolveMalFallback(anime: Anime): RemoteEpisodes? {
        anime.malId?.let { id ->
            val byIdResult = retry429 { repository.malById(id, AppLanguage.EN) }
            byIdResult.getOrNull()?.let { remote ->
                val matched = remote.episodes > 0 && isTitleMatch(anime.title, remote)
                Log.d(TAG, "MAL byId: local=\"${anime.title}\" remote=\"${remote.title}\" eps=${remote.episodes} matched=$matched")
                if (matched) return RemoteEpisodes(remote.episodes, "MAL")
            }
            if (byIdResult.isFailure && isTransientFailure(byIdResult.exceptionOrNull())) {
                return null
            }
        }

        if (!shouldRetryNotFound(anime.malNotFoundAt)) return null

        val foundResult = retry429 {
            repository.searchAnimeMalOnly(query = anime.title, language = AppLanguage.EN, limit = 5)
        }
        val found = pickMatchedResult(anime.title, foundResult.getOrNull())
            ?.takeIf { it.result.episodes > 0 }

        return if (found != null) {
            found.result.externalId?.toIntOrNull()?.let { localDataSource.setMalId(anime.id, it) }
            if (found.score >= DIRECT_NOTIFY_FROM_SEARCH_SCORE) {
                RemoteEpisodes(found.result.episodes, "MAL")
            } else {
                null
            }
        } else if (foundResult.isFailure && isTransientFailure(foundResult.exceptionOrNull())) {
            null
        } else {
            localDataSource.markMalNotFound(anime.id, System.currentTimeMillis())
            null
        }
    }

    private suspend fun <T> retry429(
        maxAttempts: Int = MAX_ATTEMPTS,
        block: suspend () -> Result<T>
    ): Result<T> {
        var delayMs = RETRY_BASE_DELAY_MS
        var attempt = 1
        var last: Result<T> = Result.failure(IllegalStateException("No attempts executed"))
        while (attempt <= maxAttempts) {
            last = block()
            if (last.isSuccess) return last
            val is429 = last.exceptionOrNull()
                ?.message
                ?.contains("429", ignoreCase = true) == true
            if (!is429 || attempt == maxAttempts) return last
            delay(delayMs)
            delayMs *= 2
            attempt++
        }
        return last
    }

    private fun shouldRetryNotFound(notFoundAt: Long?): Boolean {
        val ts = notFoundAt ?: return true
        return (System.currentTimeMillis() - ts) >= NOT_FOUND_TTL_MS
    }

    private fun pickMatchedResult(
        localTitle: String,
        results: List<ApiSearchResult>?
    ): MatchedResult? {
        val best = results.orEmpty()
            .filter { it.episodes > 0 }
            .map { candidate ->
                val score = bestTitleScore(localTitle, candidate)
                candidate to score
            }
            .maxByOrNull { it.second }

        if (best == null) return null
        val (candidate, score) = best
        val matched = isTitleMatch(localTitle, candidate)
        if (matched) {
            Log.d(
                TAG,
                "Title match accepted: local=\"$localTitle\" remote=\"${candidate.title}\" alt=\"${candidate.altTitle}\" score=$score"
            )
            return MatchedResult(candidate, score)
        }
        Log.d(
            TAG,
            "Title match rejected: local=\"$localTitle\" remote=\"${candidate.title}\" alt=\"${candidate.altTitle}\" score=$score"
        )
        return null
    }

    private fun isTitleMatch(localTitle: String, remote: ApiSearchResult): Boolean {
        val score = bestTitleScore(localTitle, remote)
        val localNorm = localTitle.normalizeForSearch()
        val remoteNorm = remote.title.normalizeForSearch()
        val shorter = minOf(localNorm.length, remoteNorm.length).toDouble()
        val longer = maxOf(localNorm.length, remoteNorm.length).toDouble()
        val lenRatio = if (longer == 0.0) 0.0 else shorter / longer

        // Reject very short/partial aliases that can score high accidentally.
        if (lenRatio < MIN_LENGTH_RATIO && score < VERY_HIGH_MATCH_SCORE) return false
        if (score >= STRICT_MATCH_SCORE) return true

        // Allow strong containment for long titles even with suffixes/subtitles.
        val strongContains = localNorm.length >= MIN_CONTAINS_LEN &&
            remoteNorm.length >= MIN_CONTAINS_LEN &&
            (localNorm.contains(remoteNorm) || remoteNorm.contains(localNorm))

        return strongContains && score >= CONTAINS_MATCH_SCORE
    }

    private fun bestTitleScore(localTitle: String, remote: ApiSearchResult): Double {
        val candidates = listOfNotNull(remote.title, remote.altTitle)
        return TitleMatcher.bestScore(localTitle, candidates)
    }

    private fun isTransientFailure(throwable: Throwable?): Boolean {
        val msg = throwable?.message?.lowercase().orEmpty()
        return msg.contains("429")
            || msg.contains("too many requests")
            || msg.contains("timeout")
            || msg.contains("tempor")
            || msg.contains("unavailable")
            || msg.contains("network")
            || msg.contains("ioexception")
    }

    private data class RemoteEpisodes(
        val episodes: Int,
        val source: String
    )

    private data class MatchedResult(
        val result: ApiSearchResult,
        val score: Double
    )

    private sealed interface ResolveResult {
        data class Found(val remote: RemoteEpisodes) : ResolveResult
        data object NotFound : ResolveResult
        data object Skipped : ResolveResult
    }

    private companion object {
        private const val TAG = "BatchEpisodeCheck"
        private const val CHUNK_SIZE = 50
        private const val CHUNK_DELAY_MS = 1_200L
        private const val MAL_CHUNK_SIZE = 20
        private const val MAL_CHUNK_DELAY_MS = 2_000L
        private const val MAL_ITEM_DELAY_MS = 450L
        private const val STRICT_MATCH_SCORE = 0.91
        private const val VERY_HIGH_MATCH_SCORE = 0.97
        private const val CONTAINS_MATCH_SCORE = 0.88
        private const val DIRECT_NOTIFY_FROM_SEARCH_SCORE = 0.96
        private const val MIN_LENGTH_RATIO = 0.55
        private const val MIN_CONTAINS_LEN = 8
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BASE_DELAY_MS = 800L
        private const val NOT_FOUND_TTL_MS = 14L * 24L * 60L * 60L * 1000L
    }
}
