package com.example.myapplication.ui.details

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsLookupRequestTest {

    @Test
    fun `series request carries external ids and cannot mutate released episode count`() {
        val anime = anime(
            title = "Игра престолов",
            mediaType = MediaType.SERIES,
            episodes = 73,
            tmdbId = 1399,
            kinopoiskId = 464963,
        )

        val request = anime.toDetailsLookupRequest(AppLanguage.RU)

        assertEquals(AppContentType.SERIES, request.appContentType)
        assertEquals(1399, request.externalIds.tmdb)
        assertEquals(464963, request.externalIds.kinopoisk)
        assertEquals("Игра престолов", request.title)
        assertEquals(73, anime.episodes)
    }

    @Test
    fun `legacy series without ids remains lookupable by title in en mode`() {
        val anime = anime(
            title = "Тьма",
            mediaType = MediaType.SERIES,
            episodes = 26,
        )

        assertTrue(anime.canLookupDetails(AppLanguage.EN))
        val request = anime.toDetailsLookupRequest(AppLanguage.EN)
        assertEquals("Тьма", request.title)
        assertNull(request.externalIds.tmdb)
    }

    @Test
    fun `movie request prefers localized title but safely falls back to primary title`() {
        val localized = anime(
            title = "Dune",
            titleRu = "Дюна",
            mediaType = MediaType.MOVIE,
            episodes = 1,
        )
        val fallback = localized.copy(titleRu = null)

        assertEquals("Дюна", localized.toDetailsLookupRequest(AppLanguage.RU).title)
        assertEquals("Dune", fallback.toDetailsLookupRequest(AppLanguage.RU).title)
    }

    @Test
    fun `anime en guard remains unchanged for records without english lookup keys`() {
        val anime = anime(title = "Стальной алхимик", mediaType = MediaType.ANIME, episodes = 64)

        assertFalse(anime.canLookupDetails(AppLanguage.EN))
    }

    private fun anime(
        title: String,
        mediaType: MediaType,
        episodes: Int,
        titleRu: String? = null,
        tmdbId: Int? = null,
        kinopoiskId: Int? = null,
    ) = Anime(
        id = "id",
        title = title,
        titleRu = titleRu,
        episodes = episodes,
        rating = 0f,
        imageFileName = null,
        orderIndex = 0,
        dateAdded = 0L,
        mediaType = mediaType,
        categoryType = mediaType.name,
        tmdbId = tmdbId,
        kinopoiskId = kinopoiskId,
    )
}
