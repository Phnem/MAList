package com.example.myapplication.media.source

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieSeriesSourceCascadeTest {

    @Test
    fun `failing source configuration or lookup does not hide successful sibling`() = runBlocking {
        val workingHoster = VetroHoster(
            name = "Direct HTTPS",
            videos = listOf(
                VetroVideo(
                    url = "https://owned.example/house-s01e02.mp4",
                    label = "Auto",
                )
            ),
        )
        val failing = fakeSource("WebDAV") { error("server unavailable") }
        val direct = fakeSource("Direct HTTPS") {
            MovieSeriesSourceResult.Found(listOf(workingHoster))
        }

        val outcome = resolveMovieSeriesSources(seriesRequest(), listOf(failing, direct), 1_000)

        assertEquals(PlaybackResolution.Found(listOf(workingHoster)), outcome)
    }

    private fun fakeSource(
        name: String,
        resolve: suspend () -> MovieSeriesSourceResult,
    ) = object : MovieSeriesPlaybackSource {
        override val sourceName: String = name
        override suspend fun resolve(request: PlaybackRequest): MovieSeriesSourceResult = resolve()
    }

    private fun seriesRequest() = PlaybackRequest(
        anime = Anime(
            id = "house",
            title = "Doctor House",
            episodes = 1,
            rating = 0f,
            imageFileName = null,
            orderIndex = 0,
            dateAdded = 0,
            mediaType = MediaType.SERIES,
        ),
        episodeNumber = 2,
        language = AppLanguage.EN,
    )
}
