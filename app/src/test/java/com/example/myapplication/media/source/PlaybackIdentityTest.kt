package com.example.myapplication.media.source

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
        )

        val recovered = identity.toAnime(seasonInfo = null, episodeNumber = 2)

        assertEquals(MediaType.SERIES, recovered.mediaType)
        assertEquals(1408, recovered.tmdbId)
        assertEquals(178710, recovered.kinopoiskId)
    }
}
