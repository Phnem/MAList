package com.example.myapplication.media.source

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.media.source.movieseries.MovieSeriesStreamingProvider
import com.example.myapplication.media.source.movieseries.ProviderCapability
import com.example.myapplication.media.source.movieseries.ProviderId
import com.example.myapplication.media.source.movieseries.ProviderResolution
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieSeriesSourceCascadeTest {

    @Test
    fun `failing source configuration or lookup does not hide successful sibling`() = runBlocking {
        val failing = fakeSource("WebDAV") { error("server unavailable") }
        val direct = fakeSource("Direct HTTPS") {
            ProviderResolution.Found(listOf(workingHoster))
        }

        val outcome = resolveMovieSeriesSources(seriesRequest(), listOf(failing, direct), 1_000)

        assertEquals(listOf(workingVideoUrl), outcome.playableUrls())
    }

    @Test
    fun `not found sibling does not hide a later success`() = runBlocking {
        val missing = fakeSource("WebDAV") { ProviderResolution.NotFound }
        val found = fakeSource("Jellyfin") { ProviderResolution.Found(listOf(workingHoster)) }

        val outcome = resolveMovieSeriesSources(seriesRequest(), listOf(missing, found), 1_000)

        assertEquals(listOf(workingVideoUrl), outcome.playableUrls())
    }

    @Test
    fun `every provider answering not found reports no match rather than failure`() = runBlocking {
        val outcome = resolveMovieSeriesSources(
            seriesRequest(),
            listOf(
                fakeSource("WebDAV") { ProviderResolution.NotFound },
                fakeSource("Jellyfin") { ProviderResolution.NotFound },
            ),
            1_000,
        )

        assertEquals(PlaybackResolution.NoMatch, outcome)
    }

    @Test
    fun `provider level errors surface as failure not as a clean no match`() = runBlocking {
        val outcome = resolveMovieSeriesSources(
            seriesRequest(),
            listOf(
                fakeSource("WebDAV") { ProviderResolution.TemporaryError("HTTP 503") },
                fakeSource("Jellyfin") { ProviderResolution.RateLimited(1_000) },
            ),
            1_000,
        )

        assertEquals(PlaybackResolution.Failure, outcome)
    }

    @Test
    fun `unconfigured providers alone report not configured`() = runBlocking {
        val outcome = resolveMovieSeriesSources(
            seriesRequest(),
            listOf(
                fakeSource("WebDAV") { ProviderResolution.NotConfigured },
                fakeSource("Emby") { ProviderResolution.NotConfigured },
            ),
            1_000,
        )

        assertEquals(PlaybackResolution.NotConfigured(MediaType.SERIES), outcome)
    }

    @Test
    fun `provider without the media capability is never called`() = runBlocking {
        var called = false
        val movieOnly = fakeSource("Movies only", capabilities = setOf(ProviderCapability.MOVIE)) {
            called = true
            ProviderResolution.Found(listOf(workingHoster))
        }

        val outcome = resolveMovieSeriesSources(seriesRequest(), listOf(movieOnly), 1_000)

        assertFalse("A series request must not reach a movie-only provider", called)
        assertEquals(PlaybackResolution.NotConfigured(MediaType.SERIES), outcome)
    }

    @Test
    fun `provider of the other language is never called`() = runBlocking {
        var called = false
        val ruOnly = fakeSource(
            "RU only",
            capabilities = setOf(ProviderCapability.SERIES, ProviderCapability.RU),
        ) {
            called = true
            ProviderResolution.Found(listOf(workingHoster))
        }

        resolveMovieSeriesSources(seriesRequest(), listOf(ruOnly), 1_000)

        assertFalse("An EN request must not reach an RU-only provider", called)
    }

    @Test
    fun `cancellation is propagated and never swallowed as a provider failure`() {
        var escaped = false
        try {
            runBlocking {
                resolveMovieSeriesSources(
                    seriesRequest(),
                    listOf(fakeSource("WebDAV") { throw CancellationException("cancelled") }),
                    1_000,
                )
            }
        } catch (_: CancellationException) {
            escaped = true
        }

        assertTrue("CancellationException must escape the cascade", escaped)
    }

    private val workingVideoUrl = "https://owned.example/house-s01e02.mp4"

    private val workingHoster = VetroHoster(
        name = "Direct HTTPS",
        videos = listOf(VetroVideo(url = workingVideoUrl, label = "Auto")),
    )

    /**
     * Compares by stream URL rather than whole objects: the cascade now attributes each video to the
     * provider that produced it, so asserting object identity would just re-state the plumbing.
     */
    private fun PlaybackResolution.playableUrls(): List<String> =
        (this as? PlaybackResolution.Found)
            ?.hosters
            ?.flatMap { hoster -> hoster.videos.orEmpty().map(VetroVideo::url) }
            .orEmpty()

    private fun fakeSource(
        name: String,
        capabilities: Set<ProviderCapability> = setOf(
            ProviderCapability.MOVIE,
            ProviderCapability.SERIES,
        ),
        resolve: suspend () -> ProviderResolution,
    ) = object : MovieSeriesStreamingProvider {
        override val id: ProviderId = ProviderId(name)
        override val displayName: String = name
        override val capabilities: Set<ProviderCapability> = capabilities
        override suspend fun resolve(request: PlaybackRequest): ProviderResolution = resolve()
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
