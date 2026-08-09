package com.example.myapplication.domain.settings

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.ExternalIds
import com.example.myapplication.network.LookupResult
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairAnimeDbMovieSeriesPolicyTest {

    @Test
    fun `movie with tmdb but no kinopoisk has retryable optional gap only`() {
        val gaps = classifyRepairGaps(movie(tmdbId = 10), hasImage = true, nowMillis = NOW)

        assertFalse(gaps.hasCriticalGaps())
        assertTrue(gaps.hasRetryableOptionalGaps())
        assertTrue(gaps.needsRepair)
    }

    @Test
    fun `recent kinopoisk no-match pauses optional retry for fourteen days`() {
        val gaps = classifyRepairGaps(
            movie(tmdbId = 10, kinopoiskNotFoundAt = NOW - DAY_MILLIS),
            hasImage = true,
            nowMillis = NOW,
        )

        assertFalse(gaps.hasCriticalGaps())
        assertFalse(gaps.hasRetryableOptionalGaps())
        assertFalse(gaps.needsRepair)
    }

    @Test
    fun `series repair never changes released episode count`() {
        val anime = movie(mediaType = MediaType.SERIES, episodes = 7)
        val candidate = candidate(episodes = 24)
        val gaps = classifyRepairGaps(anime, hasImage = true, nowMillis = NOW)

        assertEquals(7, repairedEpisodeCount(anime, listOf(candidate), gaps))
    }

    @Test
    fun `movie repair initializes missing episode count to one`() {
        val anime = movie(episodes = 0)
        val gaps = classifyRepairGaps(anime, hasImage = true, nowMillis = NOW)

        assertEquals(1, repairedEpisodeCount(anime, emptyList(), gaps))
    }

    @Test
    fun `network failure never produces not-found timestamp`() {
        val failure = LookupResult.Failure(IllegalStateException("offline"), retryable = true)

        assertNull(notFoundTimestampFor(failure, NOW))
        assertEquals(NOW, notFoundTimestampFor(LookupResult.NoMatch, NOW))
    }

    @Test
    fun `anime and manga retain legacy external id gap semantics`() {
        val anime = movie(mediaType = MediaType.ANIME).copy(categoryType = "ANIME")
        val manga = movie(mediaType = MediaType.MANGA).copy(categoryType = "MANGA")

        val animeGaps = classifyRepairGaps(anime, hasImage = true, nowMillis = NOW)
        val mangaGaps = classifyRepairGaps(manga, hasImage = true, nowMillis = NOW)

        assertTrue(animeGaps.missingAnimeExternalId)
        assertTrue(mangaGaps.missingAnimeExternalId)
        assertFalse(animeGaps.missingTmdb)
        assertFalse(mangaGaps.missingKinopoisk)
    }

    @Test
    fun `movie repair reads tmdb and kinopoisk from typed external ids`() {
        val ids = mergedRepairExternalIds(
            movie(),
            listOf(candidate(episodes = 1).copy(externalIds = ExternalIds(tmdb = 10, kinopoisk = 20))),
        )

        assertEquals(10, ids.tmdb)
        assertEquals(20, ids.kinopoisk)
        assertNull(ids.anilist)
    }

    @Test
    fun `anime repair keeps legacy source id projection`() {
        val anime = movie(mediaType = MediaType.ANIME).copy(categoryType = "ANIME")
        val anilist = candidate(episodes = 12).copy(
            source = "AniList",
            externalId = "42",
            externalIds = ExternalIds(),
        )

        val ids = mergedRepairExternalIds(anime, listOf(anilist))

        assertEquals(42, ids.anilist)
        assertNull(ids.tmdb)
    }

    private fun movie(
        mediaType: MediaType = MediaType.MOVIE,
        episodes: Int = 1,
        tmdbId: Int? = null,
        kinopoiskNotFoundAt: Long? = null,
    ) = Anime(
        id = "id",
        title = "Dune",
        episodes = episodes,
        rating = 8f,
        imageFileName = "poster.jpg",
        orderIndex = 0,
        dateAdded = 0,
        tags = persistentListOf("science_fiction"),
        categoryType = mediaType.name,
        mediaType = mediaType,
        tmdbId = tmdbId,
        kinopoiskNotFoundAt = kinopoiskNotFoundAt,
    )

    private fun candidate(episodes: Int) = ApiSearchResult(
        title = "Dune",
        altTitle = null,
        posterUrl = null,
        episodes = episodes,
        description = "",
        type = "TV",
        genres = emptyList(),
        rating = 80,
        source = "TMDB",
        categoryType = AppContentType.SERIES.name,
        externalId = null,
        externalIds = ExternalIds(tmdb = 1),
    )

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val NOW = 20L * DAY_MILLIS
    }
}
