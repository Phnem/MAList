package com.example.myapplication.media.source

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackIdentityTest {

    @Test
    fun `player recovery preserves series type and movie provider ids`() {
        val identity = PlaybackIdentity(
            libraryId = "doctor-house",
            title = "Doctor House",
            mediaType = MediaType.SERIES,
            tmdbId = 1408,
            kinopoiskId = 178710,
            imdbId = "tt0412142",
        )

        val recovered = identity.toAnime(seasonInfo = null, episodeNumber = 2)

        assertEquals(MediaType.SERIES, recovered.mediaType)
        assertEquals(1408, recovered.tmdbId)
        assertEquals(178710, recovered.kinopoiskId)
        assertEquals("tt0412142", recovered.imdbId)
    }

    @Test
    fun `identity captured from a library entry carries the imdb id both ways`() {
        val anime = Anime(
            id = "doctor-house",
            title = "Doctor House",
            episodes = 176,
            rating = 0f,
            imageFileName = null,
            orderIndex = 0,
            dateAdded = 0,
            mediaType = MediaType.SERIES,
            tmdbId = 1408,
            imdbId = "tt0412142",
        )

        val identity = PlaybackIdentity.from(anime)

        assertEquals("tt0412142", identity.imdbId)
        assertEquals("tt0412142", identity.toAnime(seasonInfo = null, episodeNumber = 1).imdbId)
    }

    @Test
    fun `playback request exposes the imdb id to providers`() {
        val request = PlaybackRequest(
            anime = Anime(
                id = "inception",
                title = "Inception",
                episodes = 1,
                rating = 0f,
                imageFileName = null,
                orderIndex = 0,
                dateAdded = 0,
                mediaType = MediaType.MOVIE,
                imdbId = "tt1375666",
            ),
            episodeNumber = 1,
        )

        assertEquals("tt1375666", request.imdbId)
    }
}
