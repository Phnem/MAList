package com.example.myapplication.network.movie

import com.example.myapplication.network.AnimeDetails
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.ExternalIds
import com.example.myapplication.network.LookupResult
import com.example.myapplication.network.kinopoisk.KinopoiskDetails
import com.example.myapplication.network.kinopoisk.KinopoiskRemoteDataSource
import com.example.myapplication.network.tmdb.SeriesEpisodeState
import com.example.myapplication.network.tmdb.TmdbRemoteDataSource
import com.example.myapplication.network.tmdb.toAnimeDetails
import java.time.Clock

/**
 * Глубокий модуль MOVIE/SERIES: вызывающий код знает только операции уровня продукта, а
 * языковой роутинг, fallback источников, дедуп и восстановление протухших id остаются здесь.
 * Локальные модели/БД приложения намеренно не пересекают этот seam.
 */
class MovieSeriesRepository internal constructor(
    private val tmdb: TmdbMovieGateway,
    private val kinopoisk: KinopoiskMovieGateway,
) {
    constructor(
        tmdb: TmdbRemoteDataSource,
        kinopoisk: KinopoiskRemoteDataSource,
    ) : this(TmdbRemoteGateway(tmdb), KinopoiskRemoteGateway(kinopoisk))

    /** RU: Kinopoisk первичен, TMDB заполняет пробелы; EN: только TMDB. */
    suspend fun search(
        query: String,
        contentType: AppContentType,
        language: AppLanguage,
    ): LookupResult<List<ApiSearchResult>> {
        requireMovieOrSeries(contentType)
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return LookupResult.Found(emptyList())

        val tmdbResult = tmdb.search(normalizedQuery, contentType, language, year = null)
        if (language == AppLanguage.EN) return tmdbResult

        val kinopoiskResult = kinopoisk.search(normalizedQuery, contentType, year = null)
        val candidates = buildList {
            if (kinopoiskResult is LookupResult.Found) addAll(kinopoiskResult.value)
            if (tmdbResult is LookupResult.Found) addAll(tmdbResult.value)
        }
        if (candidates.isNotEmpty()) return LookupResult.Found(MovieResultDeduper.merge(candidates))

        return when {
            kinopoiskResult is LookupResult.Failure -> kinopoiskResult
            tmdbResult is LookupResult.Failure -> tmdbResult
            else -> LookupResult.NoMatch
        }
    }

    /**
     * Сохранённый TMDB id считается протухшим только после явного NotFoundById. Сетевой сбой
     * сохраняет прежний id; отсутствие id запускает Kinopoisk-мост, затем прямой TMDB-поиск.
     */
    suspend fun resolveTmdbId(
        externalIds: ExternalIds,
        title: String,
        contentType: AppContentType,
        year: Int?,
    ): Int? {
        requireMovieOrSeries(contentType)
        externalIds.tmdb?.let { id ->
            return when (tmdb.checkExists(id, contentType)) {
                is LookupResult.NotFoundById -> resolveByTitle(title, contentType, year)
                else -> id
            }
        }

        externalIds.kinopoisk?.let { id ->
            val details = kinopoisk.details(id)
            if (details is LookupResult.Found && details.value.externalTmdbId != null) {
                return details.value.externalTmdbId
            }
        }
        return resolveByTitle(title, contentType, year)
    }

    /** Карточка не переносит known episode count в episodesAired/episodesTotal для SERIES. */
    suspend fun fetchDetails(
        contentType: AppContentType,
        language: AppLanguage,
        externalIds: ExternalIds,
        title: String,
        year: Int? = null,
    ): LookupResult<AnimeDetails> {
        requireMovieOrSeries(contentType)
        val tmdbId = resolveTmdbId(externalIds, title, contentType, year)
        val tmdbDetails = tmdbId?.let { tmdb.details(it, contentType, language) } ?: LookupResult.NoMatch
        val kinopoiskDetails = if (language == AppLanguage.RU) {
            resolveKinopoiskDetails(externalIds, tmdbId, title, contentType, year)
        } else {
            LookupResult.NoMatch
        }

        if (tmdbDetails is LookupResult.Found) {
            return LookupResult.Found(
                if (kinopoiskDetails is LookupResult.Found) {
                    mergeDetails(tmdbDetails.value, kinopoiskDetails.value, contentType)
                        .withoutSeriesEpisodeCounts(contentType)
                } else {
                    tmdbDetails.value.withoutSeriesEpisodeCounts(contentType)
                }
            )
        }
        if (kinopoiskDetails is LookupResult.Found) {
            return LookupResult.Found(kinopoiskDetails.value.toAnimeDetails(contentType))
        }
        return when {
            tmdbDetails is LookupResult.Failure -> tmdbDetails
            kinopoiskDetails is LookupResult.Failure -> kinopoiskDetails
            else -> LookupResult.NoMatch
        }
    }

    suspend fun episodeState(
        tmdbId: Int,
        clock: Clock,
        language: AppLanguage = AppLanguage.EN,
    ): LookupResult<SeriesEpisodeState> = tmdb.episodeState(tmdbId, language, clock)

    /** Совместимый адаптер для старого findTotalEpisodes; SERIES возвращает только released. */
    suspend fun findTotalEpisodes(
        title: String,
        contentType: AppContentType,
        language: AppLanguage = AppLanguage.EN,
        clock: Clock = Clock.systemUTC(),
    ): LookupResult<Pair<Int, String>> {
        requireMovieOrSeries(contentType)
        if (contentType == AppContentType.MOVIE) {
            return when (val result = tmdb.search(title.trim(), contentType, language, year = null)) {
                is LookupResult.Found -> if (result.value.isEmpty()) LookupResult.NoMatch else LookupResult.Found(1 to "TMDB")
                is LookupResult.NoMatch -> LookupResult.NoMatch
                is LookupResult.NotFoundById -> LookupResult.NotFoundById
                is LookupResult.Failure -> result
            }
        }

        val id = resolveByTitle(title, contentType, year = null) ?: return LookupResult.NoMatch
        return when (val state = episodeState(id, clock, language)) {
            is LookupResult.Found -> if (state.value.releasedEpisodes > 0) {
                LookupResult.Found(state.value.releasedEpisodes to "TMDB")
            } else {
                LookupResult.NoMatch
            }
            is LookupResult.NoMatch -> LookupResult.NoMatch
            is LookupResult.NotFoundById -> LookupResult.NotFoundById
            is LookupResult.Failure -> state
        }
    }

    private suspend fun resolveByTitle(
        title: String,
        contentType: AppContentType,
        year: Int?,
    ): Int? {
        val result = tmdb.search(title.trim(), contentType, AppLanguage.EN, year)
        val candidates = (result as? LookupResult.Found)?.value.orEmpty()
        return pickBestMatch(title, year, candidates)?.externalIds?.tmdb
    }

    private suspend fun resolveKinopoiskDetails(
        externalIds: ExternalIds,
        tmdbId: Int?,
        title: String,
        contentType: AppContentType,
        year: Int?,
    ): LookupResult<KinopoiskDetails> {
        externalIds.kinopoisk?.let { return kinopoisk.details(it) }
        val candidates = when (val result = kinopoisk.search(title, contentType, year)) {
            is LookupResult.Found -> result.value
            is LookupResult.Failure -> return result
            else -> return LookupResult.NoMatch
        }
        val match = candidates.firstOrNull { tmdbId != null && it.externalIds.tmdb == tmdbId }
            ?: pickBestMatch(title, year, candidates)
            ?: return LookupResult.NoMatch
        val kinopoiskId = match.externalIds.kinopoisk ?: return LookupResult.NoMatch
        return kinopoisk.details(kinopoiskId)
    }

    private fun mergeDetails(
        tmdb: AnimeDetails,
        kinopoisk: KinopoiskDetails,
        contentType: AppContentType,
    ): AnimeDetails = tmdb.copy(
        title = kinopoisk.name.ifBlank { tmdb.title },
        altTitle = tmdb.title.takeIf { it.isNotBlank() && it != kinopoisk.name } ?: tmdb.altTitle,
        description = kinopoisk.description.ifBlank { tmdb.description },
        episodesAired = if (contentType == AppContentType.MOVIE) 1 else tmdb.episodesAired,
        episodesTotal = if (contentType == AppContentType.MOVIE) 1 else null,
        genres = (kinopoisk.genres + tmdb.genres).distinctBy { it.lowercase() },
        rating = kinopoisk.ratingKp?.let { (it * 10).toInt() } ?: tmdb.rating,
        posterUrl = kinopoisk.posterUrl ?: tmdb.posterUrl,
        source = "Kinopoisk+TMDB",
    )

    private fun KinopoiskDetails.toAnimeDetails(contentType: AppContentType): AnimeDetails = AnimeDetails(
        title = name,
        altTitle = null,
        description = description,
        type = if (contentType == AppContentType.MOVIE) "Movie" else "TV",
        status = "",
        episodesAired = if (contentType == AppContentType.MOVIE) 1 else 0,
        episodesTotal = if (contentType == AppContentType.MOVIE) 1 else null,
        nextEpisode = null,
        genres = genres,
        rating = ratingKp?.let { (it * 10).toInt() },
        posterUrl = posterUrl,
        source = "Kinopoisk",
    )

    private fun AnimeDetails.withoutSeriesEpisodeCounts(contentType: AppContentType): AnimeDetails =
        if (contentType == AppContentType.SERIES) copy(episodesAired = 0, episodesTotal = null) else this

    private fun pickBestMatch(
        title: String,
        year: Int?,
        candidates: List<ApiSearchResult>,
    ): ApiSearchResult? {
        val eligible = if (year == null) candidates else candidates.filter { it.seasonYear == year }
        return eligible
            .map { it to MovieTitleMatcher.bestScore(title, it.titleVariants()) }
            .filter { (_, score) -> score >= MovieTitleMatcher.MATCH_THRESHOLD }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun ApiSearchResult.titleVariants(): List<String> = listOfNotNull(title, altTitle)

    private fun requireMovieOrSeries(contentType: AppContentType) {
        require(contentType == AppContentType.MOVIE || contentType == AppContentType.SERIES) {
            "MovieSeriesRepository supports only MOVIE/SERIES, got $contentType"
        }
    }

}

internal interface TmdbMovieGateway {
    suspend fun search(
        query: String,
        contentType: AppContentType,
        language: AppLanguage,
        year: Int?,
    ): LookupResult<List<ApiSearchResult>>

    suspend fun checkExists(id: Int, contentType: AppContentType): LookupResult<Unit>

    suspend fun details(
        id: Int,
        contentType: AppContentType,
        language: AppLanguage,
    ): LookupResult<AnimeDetails>

    suspend fun episodeState(
        tmdbId: Int,
        language: AppLanguage,
        clock: Clock,
    ): LookupResult<SeriesEpisodeState>
}

internal interface KinopoiskMovieGateway {
    suspend fun search(
        query: String,
        contentType: AppContentType,
        year: Int?,
    ): LookupResult<List<ApiSearchResult>>

    suspend fun details(id: Int): LookupResult<KinopoiskDetails>
}

private class TmdbRemoteGateway(private val source: TmdbRemoteDataSource) : TmdbMovieGateway {
    override suspend fun search(
        query: String,
        contentType: AppContentType,
        language: AppLanguage,
        year: Int?,
    ): LookupResult<List<ApiSearchResult>> = when (contentType) {
        AppContentType.MOVIE -> source.searchMovie(query, language, year)
        AppContentType.SERIES -> source.searchTv(query, language, year)
        else -> error("Unsupported content type: $contentType")
    }

    override suspend fun checkExists(id: Int, contentType: AppContentType): LookupResult<Unit> =
        if (contentType == AppContentType.MOVIE) source.checkMovieExists(id) else source.checkTvExists(id)

    override suspend fun details(
        id: Int,
        contentType: AppContentType,
        language: AppLanguage,
    ): LookupResult<AnimeDetails> = when (contentType) {
        AppContentType.MOVIE -> source.movieDetails(id, language)
        AppContentType.SERIES -> when (val result = source.tvDetails(id, language)) {
            is LookupResult.Found -> LookupResult.Found(result.value.toAnimeDetails())
            is LookupResult.NoMatch -> LookupResult.NoMatch
            is LookupResult.NotFoundById -> LookupResult.NotFoundById
            is LookupResult.Failure -> result
        }
        else -> error("Unsupported content type: $contentType")
    }

    override suspend fun episodeState(
        tmdbId: Int,
        language: AppLanguage,
        clock: Clock,
    ): LookupResult<SeriesEpisodeState> = source.episodeState(tmdbId, language, clock)
}

private class KinopoiskRemoteGateway(private val source: KinopoiskRemoteDataSource) : KinopoiskMovieGateway {
    override suspend fun search(
        query: String,
        contentType: AppContentType,
        year: Int?,
    ): LookupResult<List<ApiSearchResult>> = when (contentType) {
        AppContentType.MOVIE -> source.searchMovie(query, year)
        AppContentType.SERIES -> source.searchSeries(query, year)
        else -> error("Unsupported content type: $contentType")
    }

    override suspend fun details(id: Int): LookupResult<KinopoiskDetails> = source.details(id)
}
