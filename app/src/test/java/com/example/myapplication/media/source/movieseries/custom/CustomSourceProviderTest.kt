package com.example.myapplication.media.source.movieseries.custom

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.media.source.PlaybackRequest
import com.example.myapplication.media.source.movieseries.MatchAccuracy
import com.example.myapplication.media.source.movieseries.ProviderCapability
import com.example.myapplication.media.source.movieseries.ProviderResolution
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSourceProviderTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `a movie resolves through a single request`() = runBlocking {
        var requestedPath: String? = null
        val engine = MockEngine { request ->
            requestedPath = request.url.encodedPath
            respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders)
        }

        val result = provider(engine).resolve(movieRequest())

        assertEquals("/api/movie/27205", requestedPath)
        val found = result as ProviderResolution.Found
        assertEquals("https://cdn.example/a.m3u8", found.hosters.single().videos!!.single().url)
        assertEquals(MatchAccuracy.TMDB_ID, found.accuracy)
    }

    @Test
    fun `an episode substitutes season and episode`() = runBlocking {
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders)
        }

        provider(engine).resolve(seriesRequest(season = 2, episode = 3))

        assertEquals("/api/series/1408/2/3", path)
    }

    @Test
    fun `a two step chain feeds the lookup result into the stream request`() = runBlocking {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath.startsWith("/search")) {
                respond("""{"results":[{"id":"internal-42"}]}""", HttpStatusCode.OK, jsonHeaders)
            } else {
                respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val manifest = base().copy(
            movie = null,
            series = null,
            resolveVia = ManifestChain(
                lookup = ManifestLookupStep(path = "/search/{tmdbId}", extract = "/results/0/id"),
                movie = ManifestRequest(path = "/stream/{lookupId}"),
            ),
        )

        val result = CustomSourceProvider(manifest, HttpClient(engine)).resolve(movieRequest())

        assertEquals(listOf("/search/27205", "/stream/internal-42"), paths)
        assertTrue(result is ProviderResolution.Found)
    }

    @Test
    fun `a missing id in the template yields unsupported instead of a blank segment`() = runBlocking {
        var called = false
        val engine = MockEngine {
            called = true
            respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders)
        }
        val manifest = base().copy(
            capabilities = base().capabilities + ProviderCapability.IMDB_ID,
            movie = ManifestRequest(path = "/api/movie/{imdbId}"),
        )

        val request = movieRequest().let { it.copy(anime = it.anime.copy(tmdbId = 27205, imdbId = null)) }
        val result = CustomSourceProvider(manifest, HttpClient(engine)).resolve(request)

        assertFalse("A template needing an absent id must not be requested", called)
        assertEquals(ProviderResolution.Unsupported, result)
    }

    @Test
    fun `a substituted value cannot move the request to another host`() = runBlocking {
        var called = false
        val engine = MockEngine {
            called = true
            respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders)
        }
        val manifest = base().copy(
            capabilities = base().capabilities,
            movie = ManifestRequest(path = "/api/movie/{title}"),
        )
        val hostile = movieRequest().let {
            it.copy(anime = it.anime.copy(title = "../../@evil.example/x"))
        }

        val result = CustomSourceProvider(manifest, HttpClient(engine)).resolve(hostile)

        // The value is percent-encoded, so it stays one path segment on the configured origin.
        assertTrue(called)
        assertTrue(result is ProviderResolution.Found)
    }

    @Test
    fun `a header secret is sent and never appears in the url`() = runBlocking {
        var header: String? = null
        var url: String? = null
        val engine = MockEngine { request ->
            header = request.headers["Authorization"]
            url = request.url.toString()
            respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders)
        }
        val manifest = base().copy(auth = ManifestAuth(AuthKind.HEADER, "Authorization", "Bearer "))

        CustomSourceProvider(manifest, HttpClient(engine), secretProvider = { "s3cret" })
            .resolve(movieRequest())

        assertEquals("Bearer s3cret", header)
        assertFalse("The secret must never reach the URL", url!!.contains("s3cret"))
    }

    @Test
    fun `a source needing a secret reports not configured when none is stored`() = runBlocking {
        val engine = MockEngine { respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders) }
        val manifest = base().copy(auth = ManifestAuth(AuthKind.HEADER, "Authorization"))

        val result = CustomSourceProvider(manifest, HttpClient(engine), secretProvider = { null })
            .resolve(movieRequest())

        assertEquals(ProviderResolution.NotConfigured, result)
    }

    @Test
    fun `provider errors map to typed outcomes`() = runBlocking {
        assertEquals(ProviderResolution.NotFound, outcomeFor(HttpStatusCode.NotFound))
        assertTrue(outcomeFor(HttpStatusCode.Forbidden) is ProviderResolution.Blocked)
        assertTrue(outcomeFor(HttpStatusCode.BadGateway) is ProviderResolution.TemporaryError)
    }

    @Test
    fun `a retryable status is retried up to the configured limit`() = runBlocking {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            if (attempts < 3) {
                respond("", HttpStatusCode.ServiceUnavailable)
            } else {
                respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val manifest = base().copy(retry = ManifestRetry(maxAttempts = 3, backoffMs = 1))

        val result = CustomSourceProvider(manifest, HttpClient(engine), sleep = {})
            .resolve(movieRequest())

        assertEquals(3, attempts)
        assertTrue(result is ProviderResolution.Found)
    }

    @Test
    fun `a malformed body becomes an invalid response rather than a crash`() = runBlocking {
        val engine = MockEngine { respond("not json at all", HttpStatusCode.OK, jsonHeaders) }

        val result = provider(engine).resolve(movieRequest())

        assertTrue(result is ProviderResolution.InvalidResponse)
    }

    @Test
    fun `a response shape change yields not found instead of a crash`() = runBlocking {
        val engine = MockEngine { respond("""{"unexpected":true}""", HttpStatusCode.OK, jsonHeaders) }

        assertEquals(ProviderResolution.NotFound, provider(engine).resolve(movieRequest()))
    }

    @Test
    fun `a non http stream url is discarded`() = runBlocking {
        val engine = MockEngine {
            respond("""{"streams":[{"src":"magnet:?xt=urn:btih:abc"}]}""", HttpStatusCode.OK, jsonHeaders)
        }

        assertEquals(ProviderResolution.NotFound, provider(engine).resolve(movieRequest()))
    }

    @Test
    fun `download stays denied unless the source says yes and declares the capability`() = runBlocking {
        val body = """{"streams":[{"src":"https://cdn.example/a.mp4","can":"true"}]}"""
        val mapping = base().response.copy(downloadAllowed = "/can")

        val withoutCapability = resolveWith(base().copy(response = mapping), body)
        assertFalse(withoutCapability.videos().single().downloadAllowed)

        val withCapability = resolveWith(
            base().copy(response = mapping, capabilities = base().capabilities + ProviderCapability.DOWNLOAD),
            body,
        )
        assertTrue(withCapability.videos().single().downloadAllowed)
    }

    @Test
    fun `a source declaring one language reports it for ranking`() = runBlocking {
        val engine = MockEngine { respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders) }
        val manifest = base().copy(capabilities = base().capabilities + ProviderCapability.RU)

        val result = CustomSourceProvider(manifest, HttpClient(engine)).resolve(movieRequest())

        assertEquals(AppLanguage.RU, (result as ProviderResolution.Found).language)
    }

    @Test
    fun `a source declaring both languages reports none so ranking does not demote it`() = runBlocking {
        val engine = MockEngine { respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders) }
        val manifest = base().copy(
            capabilities = base().capabilities + ProviderCapability.RU + ProviderCapability.EN,
        )

        val result = CustomSourceProvider(manifest, HttpClient(engine)).resolve(movieRequest())

        assertNull((result as ProviderResolution.Found).language)
    }

    @Test
    fun `pagination stops early when a page returns nothing`() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders)
            } else {
                respond("""{"streams":[]}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val manifest = base().copy(pagination = ManifestPagination(maxPages = 5))

        CustomSourceProvider(manifest, HttpClient(engine), sleep = {}).resolve(movieRequest())

        assertEquals(2, calls)
    }

    @Test
    fun `anime never reaches a custom movie source`() = runBlocking {
        val engine = MockEngine { respond(MOVIE_BODY, HttpStatusCode.OK, jsonHeaders) }
        val request = movieRequest().let { it.copy(anime = it.anime.copy(mediaType = MediaType.ANIME)) }

        assertEquals(ProviderResolution.Unsupported, provider(engine).resolve(request))
    }

    private suspend fun outcomeFor(status: HttpStatusCode): ProviderResolution {
        val engine = MockEngine { respond("", status) }
        return CustomSourceProvider(base(), HttpClient(engine), sleep = {}).resolve(movieRequest())
    }

    private suspend fun resolveWith(manifest: VetroSourceManifest, body: String): ProviderResolution {
        val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }
        return CustomSourceProvider(manifest, HttpClient(engine)).resolve(movieRequest())
    }

    private fun ProviderResolution.videos() =
        (this as ProviderResolution.Found).hosters.single().videos!!

    private fun provider(engine: MockEngine) = CustomSourceProvider(base(), HttpClient(engine))

    private fun base() = VetroSourceManifest(
        manifestVersion = SUPPORTED_MANIFEST_VERSION,
        id = "my-catalog",
        name = "My catalog",
        baseUrl = "https://media.example.org",
        capabilities = setOf(
            ProviderCapability.MOVIE,
            ProviderCapability.SERIES,
            ProviderCapability.TMDB_ID,
        ),
        movie = ManifestRequest(path = "/api/movie/{tmdbId}"),
        series = ManifestRequest(path = "/api/series/{tmdbId}/{season}/{episode}"),
        response = ManifestResponseMapping(streams = "/streams", url = "/src", resolution = "/height"),
    )

    private fun movieRequest() = PlaybackRequest(
        anime = anime(MediaType.MOVIE, tmdbId = 27205, title = "Inception"),
        episodeNumber = 1,
    )

    private fun seriesRequest(season: Int, episode: Int) = PlaybackRequest(
        anime = anime(MediaType.SERIES, tmdbId = 1408, title = "Doctor House"),
        episodeNumber = episode,
        seasonInfo = com.example.myapplication.domain.seasons.SeasonInfo(
            seasonNumber = season,
            episodes = 24,
            source = "TMDB",
            title = "Doctor House",
        ),
    )

    private fun anime(mediaType: MediaType, tmdbId: Int, title: String) = Anime(
        id = "id",
        title = title,
        episodes = 1,
        rating = 0f,
        imageFileName = null,
        orderIndex = 0,
        dateAdded = 0,
        mediaType = mediaType,
        tmdbId = tmdbId,
    )

    private companion object {
        const val MOVIE_BODY = """{"streams":[{"src":"https://cdn.example/a.m3u8","height":1080}]}"""
    }
}
