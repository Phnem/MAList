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

    private fun result(
        title: String,
        year: Int?,
        tmdb: Int? = null,
        kinopoisk: Int? = null,
        source: String = "TMDB",
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
}

private class FakeTmdbGateway(
    var searchResult: LookupResult<List<ApiSearchResult>> = LookupResult.NoMatch,
    var checkResult: LookupResult<Unit> = LookupResult.NoMatch,
    var detailsResult: LookupResult<AnimeDetails> = LookupResult.NoMatch,
    var episodeResult: LookupResult<SeriesEpisodeState> = LookupResult.NoMatch,
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
        return searchResult
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
