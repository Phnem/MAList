package com.example.myapplication.media.source

import com.example.myapplication.media.source.movieseries.ProviderResolution
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.network.AppLanguage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPlaybackSourceTest {

    @Test
    fun `propfind resolves matching series episode with auth and download capability`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("PROPFIND", request.method.value)
            assertEquals("infinity", request.headers["Depth"])
            assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().startsWith("Basic "))
            assertFalse(request.url.toString().contains("app-password"))
            respond(
                content = MULTISTATUS,
                status = HttpStatusCode.MultiStatus,
                headers = headersOf(HttpHeaders.ContentType, "application/xml"),
            )
        }
        val source = WebDavPlaybackSource(HttpClient(engine)) {
            WebDavConfig(
                baseUrl = "https://cloud.example/remote.php/dav/files/alice",
                rootPath = "/Series",
                username = "alice",
                password = "app-password",
                downloadAllowed = true,
            )
        }

        val hosters = (source.resolve(seriesRequest()) as ProviderResolution.Found).hosters
        val video = hosters.single().videos.orEmpty().single()

        assertEquals("https://cloud.example/remote.php/dav/files/alice/Series/Doctor%20House/S01E02.mp4", video.url)
        assertTrue(video.headers[HttpHeaders.Authorization].orEmpty().startsWith("Basic "))
        assertTrue(video.downloadAllowed)
        assertEquals(
            WebDavConfig(
                "https://cloud.example/remote.php/dav/files/alice",
                "/Series",
                "alice",
                "app-password",
            ).credentialRef(),
            video.credentialRef,
        )
    }

    @Test
    fun `different episode is not returned`() = runBlocking {
        val source = WebDavPlaybackSource(
            HttpClient(MockEngine { respond(MULTISTATUS, HttpStatusCode.MultiStatus) })
        ) {
            WebDavConfig("https://cloud.example/dav", "/Series", "alice", "secret")
        }

        assertEquals(ProviderResolution.NotFound, source.resolve(seriesRequest(episode = 9)))
    }

    @Test
    fun `authenticated adaptive manifests are rejected to keep child requests origin safe`() = runBlocking {
        val adaptiveXml = MULTISTATUS
            .replace("S01E02.mp4", "S01E02.mp4.m3u8")
            .replace(
                "</d:multistatus>",
                "<d:response><d:href>/dav/Series/Doctor%20House/folder.mp4/S01E02.m3u8</d:href></d:response></d:multistatus>",
            )
        val source = WebDavPlaybackSource(
            HttpClient(MockEngine { respond(adaptiveXml, HttpStatusCode.MultiStatus) })
        ) {
            WebDavConfig("https://cloud.example/dav", "/Series", "alice", "secret")
        }

        assertEquals(ProviderResolution.NotFound, source.resolve(seriesRequest()))
    }

    @Test
    fun `persisted candidate rehydrates only for the same configured origin and root`() {
        val original = WebDavConfig(
            "https://cloud.example/dav/alice",
            "/Series",
            "alice",
            "first-secret",
        )
        val video = VetroVideo(
            url = "https://cloud.example/dav/alice/Series/House/S01E02.mp4",
            label = "Auto",
            headers = mapOf("Authorization" to original.authorizationHeader()),
            credentialRef = original.credentialRef(),
        ).withoutPersistedSecrets()

        assertTrue(video.rehydrateWebDavCredentials(original).headers.containsKey("Authorization"))
        val changedServer = original.copy(baseUrl = "https://other.example/dav/alice")
        assertFalse(
            video.rehydrateWebDavCredentials(changedServer).headers.containsKey("Authorization")
        )
        val attacker = video.copy(url = "https://attacker.example/House/S01E02.mp4")
        assertFalse(attacker.rehydrateWebDavCredentials(original).headers.containsKey("Authorization"))
    }

    @Test
    fun `missing webdav root is an invalid response rather than a clean not found`() = runBlocking {
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.NotFound)
        }
        val source = WebDavPlaybackSource(HttpClient(engine)) { validConfig() }

        val result = source.resolve(seriesRequest())

        // A wrong root says nothing about the title; reporting NotFound would hide the mistake.
        assertTrue(result is ProviderResolution.InvalidResponse)
    }

    @Test
    fun `webdav auth rejection is reported as blocked`() = runBlocking {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.Unauthorized) }
        val source = WebDavPlaybackSource(HttpClient(engine)) { validConfig() }

        assertTrue(source.resolve(seriesRequest()) is ProviderResolution.Blocked)
    }

    @Test
    fun `webdav server fault is reported as temporary`() = runBlocking {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.ServiceUnavailable) }
        val source = WebDavPlaybackSource(HttpClient(engine)) { validConfig() }

        assertTrue(source.resolve(seriesRequest()) is ProviderResolution.TemporaryError)
    }

    private fun validConfig() = WebDavConfig(
        baseUrl = "https://cloud.example/remote.php/dav/files/alice",
        rootPath = "/Series",
        username = "alice",
        password = "app-password",
    )

    private fun seriesRequest(episode: Int = 2) = PlaybackRequest(
        anime = Anime(
            id = "house",
            title = "Doctor House",
            titleRu = "Доктор Хаус",
            episodes = 1,
            rating = 0f,
            imageFileName = null,
            orderIndex = 0,
            dateAdded = 0,
            mediaType = MediaType.SERIES,
        ),
        episodeNumber = episode,
        seasonInfo = SeasonInfo(seasonNumber = 1, episodes = 22, source = "TMDB"),
        language = AppLanguage.EN,
    )

    private companion object {
        const val MULTISTATUS = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>https://attacker.example/Doctor%20House/S01E02.mp4</d:href></d:response>
              <d:response><d:href>/remote.php/dav/files/alice/Series/Doctor%20House/S01E01.mp4</d:href></d:response>
              <d:response><d:href>/remote.php/dav/files/alice/Series/Doctor%20House/S01E02.mp4</d:href></d:response>
              <d:response><d:href>/remote.php/dav/files/alice/Series/Other/S01E02.mp4</d:href></d:response>
            </d:multistatus>
        """
    }
}
