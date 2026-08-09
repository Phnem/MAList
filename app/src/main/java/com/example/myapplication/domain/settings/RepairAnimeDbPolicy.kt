package com.example.myapplication.domain.settings

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.LookupResult

private const val NOT_FOUND_TTL_MS = 14L * 24 * 60 * 60 * 1000

/** Pure gap classification shared by full repair and live maintenance. */
internal fun classifyRepairGaps(
    anime: Anime,
    hasImage: Boolean,
    nowMillis: Long,
): RepairAnimeDbUseCase.FieldGaps {
    val isMovieSeries = anime.mediaType == MediaType.MOVIE || anime.mediaType == MediaType.SERIES
    val missingKinopoisk = isMovieSeries && anime.kinopoiskId == null
    val kinopoiskRetryable = missingKinopoisk && (
        anime.kinopoiskNotFoundAt == null ||
            nowMillis - anime.kinopoiskNotFoundAt >= NOT_FOUND_TTL_MS
        )
    return RepairAnimeDbUseCase.FieldGaps(
        missingImage = !hasImage,
        missingTags = anime.tags.isEmpty(),
        missingRating = anime.rating <= 0,
        missingAnimeExternalId = !isMovieSeries &&
            anime.anilistId == null && anime.malId == null && anime.shikimoriId == null,
        missingTmdb = isMovieSeries && anime.tmdbId == null,
        missingKinopoisk = missingKinopoisk,
        kinopoiskRetryable = kinopoiskRetryable,
        missingCategoryType = anime.categoryType.isBlank(),
        missingEpisodes = anime.mediaType != MediaType.SERIES && anime.episodes <= 0,
    )
}

/** SERIES is owned exclusively by SeriesEpisodeCheckUseCase; repair may initialize MOVIE only. */
internal fun repairedEpisodeCount(
    anime: Anime,
    candidates: List<ApiSearchResult>,
    gaps: RepairAnimeDbUseCase.FieldGaps,
): Int = when {
    anime.mediaType == MediaType.SERIES -> anime.episodes
    anime.mediaType == MediaType.MOVIE && gaps.missingEpisodes -> 1
    gaps.missingEpisodes -> candidates.firstNotNullOfOrNull { it.episodes.takeIf { count -> count > 0 } } ?: 1
    else -> anime.episodes
}

/** Only a successful empty search is evidence that a provider has no matching entity. */
internal fun notFoundTimestampFor(result: LookupResult<*>, nowMillis: Long): Long? =
    nowMillis.takeIf { result is LookupResult.NoMatch }

internal data class RepairExternalIds(
    val anilist: Int?,
    val mal: Int?,
    val shikimori: Int?,
    val tmdb: Int?,
    val kinopoisk: Int?,
)

/** Collects both legacy anime ids and typed movie ids without source checks for MOVIE/SERIES. */
internal fun mergedRepairExternalIds(
    anime: Anime,
    candidates: List<ApiSearchResult>,
): RepairExternalIds {
    var merged = RepairExternalIds(
        anilist = anime.anilistId,
        mal = anime.malId,
        shikimori = anime.shikimoriId,
        tmdb = anime.tmdbId,
        kinopoisk = anime.kinopoiskId,
    )
    candidates.forEach { result ->
        val legacyId = result.externalId?.toIntOrNull()
        val candidate = when {
            result.source.equals("Shikimori", ignoreCase = true) ->
                RepairExternalIds(null, result.malId, legacyId, null, null)
            result.source.equals("AniList", ignoreCase = true) ->
                RepairExternalIds(legacyId, result.malId, null, null, null)
            result.source.equals("MAL", ignoreCase = true) ||
                result.source.equals("Jikan", ignoreCase = true) ->
                RepairExternalIds(null, legacyId ?: result.malId, null, null, null)
            else -> RepairExternalIds(null, result.malId, null, null, null)
        }.copy(tmdb = result.externalIds.tmdb, kinopoisk = result.externalIds.kinopoisk)
        merged = RepairExternalIds(
            anilist = merged.anilist ?: candidate.anilist,
            mal = merged.mal ?: candidate.mal,
            shikimori = merged.shikimori ?: candidate.shikimori,
            tmdb = merged.tmdb ?: candidate.tmdb,
            kinopoisk = merged.kinopoisk ?: candidate.kinopoisk,
        )
    }
    return merged
}
