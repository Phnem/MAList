package com.example.myapplication.network.movie

import com.example.myapplication.network.AnimeDetails
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.ExternalIds
import com.example.myapplication.network.LookupResult
import com.example.myapplication.network.kinopoisk.KinopoiskDetails
import com.example.myapplication.network.tmdb.SeriesEpisodeState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock

class MovieSeriesRepositoryTest {

    @Test
    fun `repair lookup preserves provider outcomes and prefers kinopoisk candidates in ru`() = runBlocking {
        val tmdbCandidate = result("The Intouchables", 2011, tmdb = 77338).copy(rating = 82)
        val kinopoiskCandidate = result("1+1", 2011, kinopoisk = 535341).copy(rating = 76)
        val repository = MovieSeriesRepository(
            FakeTmdbGateway(searchResult = LookupResult.Found(listOf(tmdbCandidate))),
            FakeKinopoiskGateway(searchResult = LookupResult.Found(listOf(kinopoiskCandidate))),
        )

        val lookup = repository.lookupForRepair("1+1", AppContentType.MOVIE, AppLanguage.RU)

        assertEquals(535341, lookup.candidates.first().externalIds.kinopoisk)
        assertEquals(76, lookup.candidates.first().rating)
        assertEquals(true, lookup.tmdb is LookupResult.Found<*>)
        assertEquals(true, lookup.kinopoisk is LookupResult.Found<*>)
    }

    @Test
    fun `repair lookup does not collapse provider failure into no match`() = runBlocking {
        val failure = LookupResult.Failure(IllegalStateException("offline"), retryable = true)
        val repository = MovieSeriesRepository(
            FakeTmdbGateway(searchResult = failure),
            FakeKinopoiskGateway(searchResult = LookupResult.NoMatch),
        )

        val lookup = repository.lookupForRepair("Dune", AppContentType.MOVIE, AppLanguage.EN)

        assertEquals(true, lookup.tmdb is LookupResult.Failure)
        assertEquals(true, lookup.kinopoisk is LookupResult.NoMatch)
        assertEquals(emptyList<ApiSearchResult>(), lookup.candidates)
    }

    @Test
    fun `repair lookup marks saved tmdb id stale and resolves replacement by title`() = runBlocking {
        val tmdb = FakeTmdbGateway(
            checkResult = LookupResult.NotFoundById,
            searchResult = LookupResult.Found(listOf(result("Dune", 2021, tmdb = 438631))),
        )
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val lookup = repository.lookupForRepair(
            query = "Dune",
            contentType = AppContentType.MOVIE,
            language = AppLanguage.EN,
            externalIds = ExternalIds(tmdb = 999),
            includeKinopoisk = false,
        )

        assertEquals(true, lookup.staleTmdbId)
        assertEquals(438631, lookup.candidates.first().externalIds.tmdb)
    }

    @Test
    fun `repair lookup preserves saved tmdb id on validation failure`() = runBlocking {
        val tmdb = FakeTmdbGateway(
            checkResult = LookupResult.Failure(IllegalStateException("offline"), retryable = true),
        )
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val lookup = repository.lookupForRepair(
            query = "Dune",
            contentType = AppContentType.MOVIE,
            language = AppLanguage.EN,
            externalIds = ExternalIds(tmdb = 999),
            includeKinopoisk = false,
            searchForMetadata = false,
        )

        assertEquals(false, lookup.staleTmdbId)
        assertEquals(true, lookup.tmdb is LookupResult.Failure)
    }

    @Test
    fun `details with saved tmdb id returns populated card`() = runBlocking {
        val details = details(title = "Dune", description = "Arrakis", posterUrl = "poster")
        val tmdb = FakeTmdbGateway(
            checkResult = LookupResult.Found(Unit),
            detailsResult = LookupResult.Found(details),
        )
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val found = repository.fetchDetails(
            contentType = AppContentType.MOVIE,
            language = AppLanguage.EN,
            externalIds = ExternalIds(tmdb = 438631),
            title = "Dune",
        )

        val card = (found as LookupResult.Found<AnimeDetails>).value
        assertEquals("Arrakis", card.description)
        assertEquals("poster", card.posterUrl)
    }

    @Test
    fun `legacy details without tmdb id resolves by title before loading card`() = runBlocking {
        val tmdb = FakeTmdbGateway(
            searchResult = LookupResult.Found(listOf(result("Dark", 2017, tmdb = 70523))),
            detailsResult = LookupResult.Found(details(title = "Dark", description = "Time travel")),
        )
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val found = repository.fetchDetails(
            contentType = AppContentType.SERIES,
            language = AppLanguage.EN,
            externalIds = ExternalIds(),
            title = "Dark",
            year = 2017,
        )

        assertEquals("Time travel", (found as LookupResult.Found<AnimeDetails>).value.description)
        assertEquals(1, tmdb.searchCalls)
    }

    @Test
    fun `series tmdb details strip all episode counts`() = runBlocking {
        val tmdb = FakeTmdbGateway(
            checkResult = LookupResult.Found(Unit),
            detailsResult = LookupResult.Found(
                details(title = "Ongoing", description = "", episodesAired = 8, episodesTotal = 24)
            ),
        )
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val found = repository.fetchDetails(
            contentType = AppContentType.SERIES,
            language = AppLanguage.EN,
            externalIds = ExternalIds(tmdb = 1),
            title = "Ongoing",
        ) as LookupResult.Found<AnimeDetails>

        assertEquals(0, found.value.episodesAired)
        assertEquals(null, found.value.episodesTotal)
    }

    @Test
    fun `series ru merged details strip all episode counts`() = runBlocking {
        val tmdb = FakeTmdbGateway(
            checkResult = LookupResult.Found(Unit),
            detailsResult = LookupResult.Found(
                details(title = "Ongoing", description = "", episodesAired = 8, episodesTotal = 24)
            ),
        )
        val kinopoisk = FakeKinopoiskGateway(
            detailsResult = LookupResult.Found(kinopoiskDetails(tmdbId = 1)),
        )
        val repository = MovieSeriesRepository(tmdb, kinopoisk)

        val found = repository.fetchDetails(
            contentType = AppContentType.SERIES,
            language = AppLanguage.RU,
            externalIds = ExternalIds(tmdb = 1, kinopoisk = 2),
            title = "Онгоинг",
        ) as LookupResult.Found<AnimeDetails>

        assertEquals(0, found.value.episodesAired)
        assertEquals(null, found.value.episodesTotal)
    }

    @Test
    fun `series kinopoisk only details expose no episode counts`() = runBlocking {
        val kinopoisk = FakeKinopoiskGateway(
            detailsResult = LookupResult.Found(kinopoiskDetails(tmdbId = null)),
        )
        val repository = MovieSeriesRepository(FakeTmdbGateway(), kinopoisk)

        val found = repository.fetchDetails(
            contentType = AppContentType.SERIES,
            language = AppLanguage.RU,
            externalIds = ExternalIds(kinopoisk = 2),
            title = "Онгоинг",
        ) as LookupResult.Found<AnimeDetails>

        assertEquals(0, found.value.episodesAired)
        assertEquals(null, found.value.episodesTotal)
    }

    @Test
    fun `valid tmdb id is retained without title search`() = runBlocking {
        val tmdb = FakeTmdbGateway(checkResult = LookupResult.Found(Unit))
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val resolved = repository.resolveTmdbId(
            externalIds = ExternalIds(tmdb = 438631),
            title = "Dune",
            contentType = AppContentType.MOVIE,
            year = 2021,
        )

        assertEquals(438631, resolved)
        assertEquals(0, tmdb.searchCalls)
    }

    @Test
    fun `stale tmdb id is resolved again by title`() = runBlocking {
        val tmdb = FakeTmdbGateway(
            checkResult = LookupResult.NotFoundById,
            searchResult = LookupResult.Found(listOf(result("Dune", 2021, tmdb = 438631))),
        )
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val resolved = repository.resolveTmdbId(
            externalIds = ExternalIds(tmdb = 999999),
            title = "Dune",
            contentType = AppContentType.MOVIE,
            year = 2021,
        )

        assertEquals(438631, resolved)
        assertEquals(1, tmdb.searchCalls)
    }

    @Test
    fun `kinopoisk id bridges directly to tmdb id`() = runBlocking {
        val kinopoisk = FakeKinopoiskGateway(
            detailsResult = LookupResult.Found(kinopoiskDetails(tmdbId = 1399)),
        )
        val tmdb = FakeTmdbGateway()
        val repository = MovieSeriesRepository(tmdb, kinopoisk)

        val resolved = repository.resolveTmdbId(
            externalIds = ExternalIds(kinopoisk = 464963),
            title = "Игра престолов",
            contentType = AppContentType.SERIES,
            year = 2011,
        )

        assertEquals(1399, resolved)
        assertEquals(0, tmdb.searchCalls)
    }

    @Test
    fun `missing external ids resolves tmdb id by title and year`() = runBlocking {
        val tmdb = FakeTmdbGateway(
            searchResult = LookupResult.Found(listOf(result("The Office", 2005, tmdb = 2316))),
        )
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val resolved = repository.resolveTmdbId(
            externalIds = ExternalIds(),
            title = "The Office",
            contentType = AppContentType.SERIES,
            year = 2005,
        )

        assertEquals(2316, resolved)
        assertEquals(1, tmdb.searchCalls)
        assertEquals(AppContentType.SERIES, tmdb.lastSearchContentType)
        assertEquals(2005, tmdb.lastSearchYear)
    }

    @Test
    fun `tmdb failure preserves existing id and does not search`() = runBlocking {
        val tmdb = FakeTmdbGateway(
            checkResult = LookupResult.Failure(IllegalStateException("offline"), retryable = true),
        )
        val repository = MovieSeriesRepository(tmdb, FakeKinopoiskGateway())

        val resolved = repository.resolveTmdbId(
            externalIds = ExternalIds(tmdb = 42),
            title = "Anything",
            contentType = AppContentType.MOVIE,
            year = null,
        )

        assertEquals(42, resolved)
        assertEquals(0, tmdb.searchCalls)
    }

    @Test
    fun `ru search keeps kinopoisk fields and fills their gaps from tmdb`() = runBlocking {
        val kinopoisk = FakeKinopoiskGateway(
            searchResult = LookupResult.Found(
                listOf(result("Дюна", 2021, tmdb = 438631, kinopoisk = 409118, source = "Kinopoisk"))
            )
        )
        val tmdb = FakeTmdbGateway(
            searchResult = LookupResult.Found(
                listOf(
                    result("Dune", 2021, tmdb = 438631, source = "TMDB").copy(
                        description = "Arrakis",
                        posterUrl = "tmdb-poster",
                        genres = listOf("Science Fiction"),
                    )
                )
            )
        )
        val repository = MovieSeriesRepository(tmdb, kinopoisk)

        val found = repository.search("Дюна", AppContentType.MOVIE, AppLanguage.RU)
        val merged = (found as LookupResult.Found<List<ApiSearchResult>>).value.single()

        assertEquals("Дюна", merged.title)
        assertEquals("Arrakis", merged.description)
        assertEquals("tmdb-poster", merged.posterUrl)
        assertEquals(438631, merged.externalIds.tmdb)
        assertEquals(409118, merged.externalIds.kinopoisk)
    }

    @Test
    fun `en search uses only tmdb`() = runBlocking {
        val kinopoisk = FakeKinopoiskGateway(
            searchResult = LookupResult.Found(listOf(result("Дюна", 2021, kinopoisk = 409118)))
        )
        val tmdb = FakeTmdbGateway(
            searchResult = LookupResult.Found(listOf(result("Dune", 2021, tmdb = 438631)))
        )
        val repository = MovieSeriesRepository(tmdb, kinopoisk)

        val found = repository.search("Dune", AppContentType.MOVIE, AppLanguage.EN)

        assertEquals("Dune", (found as LookupResult.Found<List<ApiSearchResult>>).value.single().title)
        assertEquals(0, kinopoisk.searchCalls)
        assertEquals(1, tmdb.searchCalls)
    }

    @Test
    fun `ru search fills missing kinopoisk english title from tmdb en result`() = runBlocking {
        val kinopoisk = FakeKinopoiskGateway(
            searchResult = LookupResult.Found(
                listOf(
                    result(
                        "1+1",
                        2011,
                        tmdb = 77338,
                        kinopoisk = 535341,
                        source = "Kinopoisk",
                        titleRu = "1+1",
                    )
                )
            )
        )
        val tmdb = FakeTmdbGateway(
            searchResultsByLanguage = mapOf(
                AppLanguage.RU to LookupResult.Found(
                    listOf(result("1+1", 2011, tmdb = 77338, titleRu = "1+1"))
                ),
                AppLanguage.EN to LookupResult.Found(
                    listOf(result("The Intouchables", 2011, tmdb = 77338, titleEn = "The Intouchables"))
                ),
            )
        )
        val repository = MovieSeriesRepository(tmdb, kinopoisk)

        val found = repository.search("1+1", AppContentType.MOVIE, AppLanguage.RU)
            as LookupResult.Found<List<ApiSearchResult>>

        assertEquals("1+1", found.value.single().titleRu)
        assertEquals("The Intouchables", found.value.single().titleEn)
    }

    private fun result(
        title: String,
        year: Int?,
        tmdb: Int? = null,
        kinopoisk: Int? = null,
        source: String = "TMDB",
        titleEn: String? = null,
        titleRu: String? = null,
    ) = ApiSearchResult(
        title = title,
        altTitle = null,
        posterUrl = null,
        episodes = 1,
        description = "",
        type = "Movie",
        genres = emptyList(),
        rating = null,
        source = source,
        categoryType = "MOVIE",
        externalId = (tmdb ?: kinopoisk)?.toString(),
        seasonYear = year,
        externalIds = ExternalIds(tmdb = tmdb, kinopoisk = kinopoisk),
        titleEn = titleEn,
        titleRu = titleRu,
    )

    private fun kinopoiskDetails(tmdbId: Int?) = KinopoiskDetails(
        id = 1,
        name = "Name",
        description = "",
        posterUrl = null,
        ratingKp = null,
        genres = emptyList(),
        externalTmdbId = tmdbId,
    )

    private fun details(
        title: String,
        description: String,
        posterUrl: String? = null,
        episodesAired: Int = 0,
        episodesTotal: Int? = null,
    ) = AnimeDetails(
        title = title,
        altTitle = null,
        description = description,
        type = "TV",
        status = "ONGOING",
        episodesAired = episodesAired,
        episodesTotal = episodesTotal,
        nextEpisode = null,
        genres = listOf("Drama"),
        rating = 80,
        posterUrl = posterUrl,
        source = "TMDB",
    )
}

private class FakeTmdbGateway(
    var searchResult: LookupResult<List<ApiSearchResult>> = LookupResult.NoMatch,
    var checkResult: LookupResult<Unit> = LookupResult.NoMatch,
    var detailsResult: LookupResult<AnimeDetails> = LookupResult.NoMatch,
    var episodeResult: LookupResult<SeriesEpisodeState> = LookupResult.NoMatch,
    var searchResultsByLanguage: Map<AppLanguage, LookupResult<List<ApiSearchResult>>> = emptyMap(),
) : TmdbMovieGateway {
    var searchCalls = 0
    var lastSearchContentType: AppContentType? = null
    var lastSearchYear: Int? = null

    override suspend fun search(
        query: String,
        contentType: AppContentType,
        language: AppLanguage,
        year: Int?,
    ): LookupResult<List<ApiSearchResult>> {
        searchCalls++
        lastSearchContentType = contentType
        lastSearchYear = year
        return searchResultsByLanguage[language] ?: searchResult
    }

    override suspend fun checkExists(id: Int, contentType: AppContentType): LookupResult<Unit> = checkResult

    override suspend fun details(
        id: Int,
        contentType: AppContentType,
        language: AppLanguage,
    ): LookupResult<AnimeDetails> = detailsResult

    override suspend fun episodeState(
        tmdbId: Int,
        language: AppLanguage,
        clock: Clock,
    ): LookupResult<SeriesEpisodeState> = episodeResult
}

private class FakeKinopoiskGateway(
    var searchResult: LookupResult<List<ApiSearchResult>> = LookupResult.NoMatch,
    var detailsResult: LookupResult<KinopoiskDetails> = LookupResult.NoMatch,
) : KinopoiskMovieGateway {
    var searchCalls = 0

    override suspend fun search(
        query: String,
        contentType: AppContentType,
        year: Int?,
    ): LookupResult<List<ApiSearchResult>> {
        searchCalls++
        return searchResult
    }

    override suspend fun details(id: Int): LookupResult<KinopoiskDetails> = detailsResult
}
