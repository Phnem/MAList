package com.example.myapplication.domain.search

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.ExternalIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddFromApiUseCaseMappingTest {

    @Test
    fun `ru series stores both movie ids localized titles and exactly one episode`() {
        val result = result(
            title = "Игра престолов",
            altTitle = "Game of Thrones",
            originalTitle = "Game of Thrones",
            categoryType = "SERIES",
            source = "Kinopoisk",
            episodes = 73,
            externalIds = ExternalIds(tmdb = 1399, kinopoisk = 464963),
            titleEn = "Game of Thrones",
            titleRu = "Игра престолов",
        )

        val params = buildSaveAnimeParams(
            result = result,
            effectiveMalId = null,
            imageFileName = "poster.jpg",
            selectedTags = listOf("Drama"),
            rating10 = 8.7f,
            dateAdded = 100L,
        )

        assertEquals(MediaType.SERIES, params.mediaType)
        assertEquals(1, params.episodes)
        assertEquals(1399, params.tmdbId)
        assertEquals(464963, params.kinopoiskId)
        assertEquals("Игра престолов", params.titleRu)
        assertEquals("Game of Thrones", params.titleEn)
    }

    @Test
    fun `en movie stores tmdb id english title and one episode`() {
        val params = buildSaveAnimeParams(
            result = result(
                title = "Dune",
                categoryType = "MOVIE",
                source = "TMDB",
                episodes = 0,
                externalIds = ExternalIds(tmdb = 438631),
                titleEn = "Dune",
            ),
            effectiveMalId = null,
            imageFileName = null,
            selectedTags = emptyList(),
            rating10 = 8f,
            dateAdded = 100L,
        )

        assertEquals(1, params.episodes)
        assertEquals("Dune", params.titleEn)
        assertNull(params.titleRu)
        assertEquals(438631, params.tmdbId)
    }

    @Test
    fun `anime source based id and episode mapping stays unchanged`() {
        val params = buildSaveAnimeParams(
            result = result(
                title = "Frieren",
                categoryType = "ANIME",
                source = "AniList",
                episodes = 28,
                externalId = "52991",
            ),
            effectiveMalId = 52991,
            imageFileName = null,
            selectedTags = emptyList(),
            rating10 = 9f,
            dateAdded = 100L,
        )

        assertEquals(MediaType.ANIME, params.mediaType)
        assertEquals(28, params.episodes)
        assertEquals(52991, params.anilistId)
        assertEquals(52991, params.malId)
        assertNull(params.tmdbId)
        assertNull(params.kinopoiskId)
    }

    @Test
    fun `manga source based mapping stays unchanged`() {
        val params = buildSaveAnimeParams(
            result = result(
                title = "Berserk",
                categoryType = "MANGA",
                source = "Jikan",
                episodes = 42,
                externalId = "2",
            ),
            effectiveMalId = 2,
            imageFileName = null,
            selectedTags = emptyList(),
            rating10 = 9f,
            dateAdded = 100L,
        )

        assertEquals(MediaType.MANGA, params.mediaType)
        assertEquals(42, params.episodes)
        assertEquals(2, params.malId)
        assertNull(params.tmdbId)
        assertNull(params.kinopoiskId)
    }

    @Test
    fun `neutral script ru title uses explicit locale fields instead of alphabet guessing`() {
        val params = buildSaveAnimeParams(
            result = result(
                title = "1+1",
                altTitle = "Intouchables",
                categoryType = "MOVIE",
                source = "Kinopoisk",
                episodes = 1,
                externalIds = ExternalIds(tmdb = 77338, kinopoisk = 535341),
                titleEn = "The Intouchables",
                titleRu = "1+1",
            ),
            effectiveMalId = null,
            imageFileName = null,
            selectedTags = emptyList(),
            rating10 = 8.8f,
            dateAdded = 100L,
        )

        assertEquals("1+1", params.titleRu)
        assertEquals("The Intouchables", params.titleEn)
    }

    @Test
    fun `duplicate probe reads movie ids from external ids`() {
        val probe = result(
            title = "Dune",
            categoryType = "MOVIE",
            source = "TMDB",
            episodes = 1,
            externalIds = ExternalIds(tmdb = 438631, kinopoisk = 409118),
        ).toDuplicateProbe()

        assertEquals(438631, probe?.tmdbId)
        assertEquals(409118, probe?.kinopoiskId)
        assertTrue(probe?.mediaType == MediaType.MOVIE)
    }

    private fun result(
        title: String,
        categoryType: String,
        source: String,
        episodes: Int,
        altTitle: String? = null,
        originalTitle: String? = null,
        externalId: String? = null,
        externalIds: ExternalIds = ExternalIds(),
        titleEn: String? = null,
        titleRu: String? = null,
    ) = ApiSearchResult(
        title = title,
        altTitle = altTitle,
        posterUrl = null,
        episodes = episodes,
        description = "",
        type = categoryType,
        genres = emptyList(),
        rating = null,
        source = source,
        categoryType = categoryType,
        externalId = externalId,
        originalTitle = originalTitle,
        titleEn = titleEn,
        titleRu = titleRu,
        externalIds = externalIds,
    )
}
