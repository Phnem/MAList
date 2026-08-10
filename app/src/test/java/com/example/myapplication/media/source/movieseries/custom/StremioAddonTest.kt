package com.example.myapplication.media.source.movieseries.custom

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.media.source.PlaybackRequest
import com.example.myapplication.media.source.movieseries.MatchAccuracy
import com.example.myapplication.media.source.movieseries.ProviderCapability
import com.example.myapplication.media.source.movieseries.ProviderResolution
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

class StremioImporterTest {

    @Test
    fun `a stream addon serving movies and series is accepted`() {
        val result = StremioImporter.import(BASE, manifest())

        val addon = (result as StremioImport.Valid).addon
        assertTrue(ProviderCapability.MOVIE in addon.capabilities)
        assertTrue(ProviderCapability.SERIES in addon.capabilities)
        assertTrue(ProviderCapability.IMDB_ID in addon.capabilities)
    }

    @Test
    fun `a p2p addon is refused outright`() {
        val result = StremioImporter.import(
            BASE,
            manifest().copy(behaviorHints = StremioAddonHints(p2p = true)),
        )

        // Declaring p2p makes it a torrent index; Vetro has no such stack.
        assertInvalid(result, "P2P")
    }

    @Test
    fun `an addon without the stream resource is refused`() {
        assertInvalid(
            StremioImporter.import(BASE, manifest().copy(resources = listOf("catalog", "meta"))),
            "stream resource",
        )
    }

    @Test
    fun `an addon serving neither movie nor series is refused`() {
        assertInvalid(
            StremioImporter.import(BASE, manifest().copy(types = listOf("channel"))),
            "neither movie nor series",
        )
    }

    @Test
    fun `an addon that does not accept imdb ids is refused`() {
        assertInvalid(
            StremioImporter.import(BASE, manifest().copy(idPrefixes = listOf("kitsu:"))),
            "IMDb ids",
        )
    }

    @Test
    fun `an addon restricted to imdb ids is accepted`() {
        val result = StremioImporter.import(BASE, manifest().copy(idPrefixes = listOf("tt")))

        assertTrue(result is StremioImport.Valid)
    }

    @Test
    fun `a plain http addon url is refused`() {
        assertInvalid(StremioImporter.import("http://addon.example", manifest()), "https")
    }

    @Test
    fun `an addon needing its own configuration first is refused`() {
        assertInvalid(
            StremioImporter.import(
                BASE,
                manifest().copy(behaviorHints = StremioAddonHints(configurationRequired = true)),
            ),
            "configuration",
        )
    }

    private fun assertInvalid(result: StremioImport, contains: String) {
        assertTrue("Expected rejection mentioning $contains, got $result", result is StremioImport.Invalid)
        assertTrue((result as StremioImport.Invalid).reason.contains(contains))
    }

    private fun manifest() = StremioManifest(
        id = "com.example.addon",
        name = "Example addon",
        resources = listOf("stream"),
        types = listOf("movie", "series"),
    )

    private companion object {
        const val BASE = "https://addon.example"
    }
}

class StremioAddonProviderTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `a movie is requested by its imdb id`() = runBlocking {
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond("""{"streams":[{"url":"https://cdn.example/a.mp4","name":"1080p"}]}""", HttpStatusCode.OK, jsonHeaders)
        }

        val result = provider(engine).resolve(movieRequest())

        assertEquals("/stream/movie/tt1375666.json", path)
        assertEquals(MatchAccuracy.IMDB_ID, (result as ProviderResolution.Found).accuracy)
        assertEquals(1080, result.hosters.single().videos!!.single().resolution)
    }

    @Test
    fun `an episode uses the documented id colon season colon episode form`() = runBlocking {
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond("""{"streams":[{"url":"https://cdn.example/a.mp4"}]}""", HttpStatusCode.OK, jsonHeaders)
        }

        provider(engine).resolve(seriesRequest(season = 9, episode = 17))

        assertEquals("/stream/series/tt0412142:9:17.json", path)
    }

    @Test
    fun `a title without an imdb id is unsupported rather than searched by name`() = runBlocking {
        var called = false
        val engine = MockEngine {
            called = true
            respond("""{"streams":[]}""", HttpStatusCode.OK, jsonHeaders)
        }
        val request = movieRequest().let { it.copy(anime = it.anime.copy(imdbId = null)) }

        assertEquals(ProviderResolution.Unsupported, provider(engine).resolve(request))
        assertFalse("The protocol has no addressing mode other than the id", called)
    }

    @Test
    fun `torrent usenet and archive streams are all discarded`() = runBlocking {
        val body = """
            {"streams":[
              {"infoHash":"abc123","fileIdx":0,"name":"1080p torrent"},
              {"nzbUrl":"https://usenet.example/a.nzb"},
              {"ytId":"dQw4w9WgXcQ"},
              {"externalUrl":"https://site.example/watch"},
              {"zipUrls":["https://x.example/a.zip"]}
            ]}
        """.trimIndent()
        val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }

        // Vetro has no torrent, usenet or archive stack, and externalUrl is a browser link.
        assertEquals(ProviderResolution.NotFound, provider(engine).resolve(movieRequest()))
    }

    @Test
    fun `a non http url permitted by the spec is discarded`() = runBlocking {
        val engine = MockEngine {
            respond("""{"streams":[{"url":"rtmp://cdn.example/live"}]}""", HttpStatusCode.OK, jsonHeaders)
        }

        assertEquals(ProviderResolution.NotFound, provider(engine).resolve(movieRequest()))
    }

    @Test
    fun `a direct url alongside rejected forms still plays`() = runBlocking {
        val body = """
            {"streams":[
              {"infoHash":"abc123"},
              {"url":"https://cdn.example/good.mp4","name":"720p"}
            ]}
        """.trimIndent()
        val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }

        val result = provider(engine).resolve(movieRequest()) as ProviderResolution.Found

        assertEquals("https://cdn.example/good.mp4", result.hosters.single().videos!!.single().url)
    }

    @Test
    fun `proxy headers are filtered to what a cdn legitimately needs`() = runBlocking {
        val body = """
            {"streams":[{"url":"https://cdn.example/a.mp4","behaviorHints":{"notWebReady":true,
             "proxyHeaders":{"request":{"Referer":"https://ok.example","Authorization":"Bearer x",
             "Cookie":"session=1","User-Agent":"VLC"}}}}]}
        """.trimIndent()
        val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }

        val video = (provider(engine).resolve(movieRequest()) as ProviderResolution.Found)
            .hosters.single().videos!!.single()

        assertEquals(setOf("Referer", "User-Agent"), video.headers.keys)
        // An addon must not be able to turn Vetro into a credential-forwarding primitive.
        assertFalse(video.headers.keys.any { it.equals("Authorization", true) })
        assertFalse(video.headers.keys.any { it.equals("Cookie", true) })
    }

    @Test
    fun `streaming never implies permission to download`() = runBlocking {
        val engine = MockEngine {
            respond("""{"streams":[{"url":"https://cdn.example/a.mp4"}]}""", HttpStatusCode.OK, jsonHeaders)
        }

        val video = (provider(engine).resolve(movieRequest()) as ProviderResolution.Found)
            .hosters.single().videos!!.single()

        assertFalse(video.downloadAllowed)
    }

    @Test
    fun `subtitles are carried across but only over http`() = runBlocking {
        val body = """
            {"streams":[{"url":"https://cdn.example/a.mp4","subtitles":[
              {"url":"https://cdn.example/en.vtt","lang":"en"},
              {"url":"ftp://cdn.example/ru.vtt","lang":"ru"}
            ]}]}
        """.trimIndent()
        val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }

        val video = (provider(engine).resolve(movieRequest()) as ProviderResolution.Found)
            .hosters.single().videos!!.single()

        assertEquals(1, video.subtitles.size)
        assertEquals("en", video.subtitles.single().lang)
    }

    @Test
    fun `an empty stream list is not found rather than an error`() = runBlocking {
        val engine = MockEngine { respond("""{"streams":[]}""", HttpStatusCode.OK, jsonHeaders) }

        assertEquals(ProviderResolution.NotFound, provider(engine).resolve(movieRequest()))
    }

    @Test
    fun `addon errors map to typed outcomes`() = runBlocking {
        assertEquals(ProviderResolution.NotFound, outcome(HttpStatusCode.NotFound))
        assertTrue(outcome(HttpStatusCode.TooManyRequests) is ProviderResolution.RateLimited)
        assertTrue(outcome(HttpStatusCode.InternalServerError) is ProviderResolution.TemporaryError)
    }

    @Test
    fun `a malformed body becomes an invalid response`() = runBlocking {
        val engine = MockEngine { respond("<html>nope</html>", HttpStatusCode.OK, jsonHeaders) }

        assertTrue(provider(engine).resolve(movieRequest()) is ProviderResolution.InvalidResponse)
    }

    @Test
    fun `an unexpected response shape is not found rather than a crash`() = runBlocking {
        val engine = MockEngine { respond("""{"other":1}""", HttpStatusCode.OK, jsonHeaders) }

        assertEquals(ProviderResolution.NotFound, provider(engine).resolve(movieRequest()))
    }

    @Test
    fun `anime never reaches a stremio addon`() = runBlocking {
        val engine = MockEngine { respond("""{"streams":[]}""", HttpStatusCode.OK, jsonHeaders) }
        val request = movieRequest().let { it.copy(anime = it.anime.copy(mediaType = MediaType.ANIME)) }

        assertEquals(ProviderResolution.Unsupported, provider(engine).resolve(request))
    }

    private suspend fun outcome(status: HttpStatusCode): ProviderResolution {
        val engine = MockEngine { respond("", status) }
        return provider(engine).resolve(movieRequest())
    }

    private fun provider(engine: MockEngine) = StremioAddonProvider(
        addon = StremioAddon(
            baseUrl = "https://addon.example",
            manifest = StremioManifest(
                id = "com.example.addon",
                name = "Example addon",
                resources = listOf("stream"),
                types = listOf("movie", "series"),
            ),
            capabilities = setOf(
                ProviderCapability.MOVIE,
                ProviderCapability.SERIES,
                ProviderCapability.IMDB_ID,
            ),
        ),
        client = HttpClient(engine),
    )

    private fun movieRequest() = PlaybackRequest(
        anime = anime(MediaType.MOVIE, "Inception", "tt1375666"),
        episodeNumber = 1,
    )

    private fun seriesRequest(season: Int, episode: Int) = PlaybackRequest(
        anime = anime(MediaType.SERIES, "Doctor House", "tt0412142"),
        episodeNumber = episode,
        seasonInfo = SeasonInfo(
            seasonNumber = season,
            episodes = 24,
            source = "TMDB",
            title = "Doctor House",
        ),
    )

    private fun anime(mediaType: MediaType, title: String, imdbId: String?) = Anime(
        id = "id",
        title = title,
        episodes = 1,
        rating = 0f,
        imageFileName = null,
        orderIndex = 0,
        dateAdded = 0,
        mediaType = mediaType,
        imdbId = imdbId,
    )
}
