package com.example.myapplication.media.source

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.network.AppLanguage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalMediaServerPlaybackSourceTest {

    @Test
    fun `Jellyfin resolves requested series episode without token in URL`() = runBlocking {
        var requestIndex = 0
        val engine = MockEngine { request ->
            assertEquals("personal-token", request.headers["X-Emby-Token"])
            assertFalse(request.url.toString().contains("personal-token"))
            when (requestIndex++) {
                0 -> {
                    assertTrue(request.url.encodedPath.endsWith("/Users/user-1/Items"))
                    respond(SERIES_SEARCH, HttpStatusCode.OK)
                }
                1 -> {
                    assertTrue(request.url.encodedPath.endsWith("/Shows/series-1/Episodes"))
                    assertEquals("1", request.url.parameters["Season"])
                    respond(EPISODES, HttpStatusCode.OK)
                }
                else -> {
                    assertTrue(request.url.encodedPath.endsWith("/Items/episode-2/PlaybackInfo"))
                    respond(JELLYFIN_DIRECT_PLAYBACK, HttpStatusCode.OK)
                }
            }
        }
        val config = PersonalMediaServerConfig(
            baseUrl = "https://jellyfin.example",
            userId = "user-1",
            accessToken = "personal-token",
            downloadAllowed = true,
        )
        val source = PersonalMediaServerPlaybackSource(
            client = HttpClient(engine),
            provider = PersonalMediaServerProvider.JELLYFIN,
            configProvider = { config },
        )

        val result = source.resolve(seriesRequest()) as MovieSeriesSourceResult.Found
        val video = result.hosters.single().videos.orEmpty().single()

        assertEquals(
            "https://jellyfin.example/Videos/episode-2/stream.mp4?" +
                "Static=true&MediaSourceId=media-2&PlaySessionId=play-2",
            video.url,
        )
        assertEquals("personal-token", video.headers["X-Emby-Token"])
        assertTrue(video.downloadAllowed)
        assertEquals(config.credentialRef(PersonalMediaServerProvider.JELLYFIN), video.credentialRef)

        val persisted = video.withoutPersistedSecrets()
        assertFalse(persisted.headers.containsKey("X-Emby-Token"))
        assertEquals(
            "personal-token",
            persisted.rehydratePersonalServerCredentials(
                PersonalMediaServerProvider.JELLYFIN,
                config,
            ).headers["X-Emby-Token"],
        )
        val otherServer = config.copy(baseUrl = "https://other.example")
        assertFalse(
            persisted.rehydratePersonalServerCredentials(
                PersonalMediaServerProvider.JELLYFIN,
                otherServer,
            ).headers.containsKey("X-Emby-Token")
        )
    }

    @Test
    fun `Emby resolves movie and respects server download denial`() = runBlocking {
        val source = PersonalMediaServerPlaybackSource(
            client = HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/PlaybackInfo")) {
                        respond(EMBY_DIRECT_PLAYBACK, HttpStatusCode.OK)
                    } else {
                        respond(MOVIE_SEARCH, HttpStatusCode.OK)
                    }
                }
            ),
            provider = PersonalMediaServerProvider.EMBY,
            configProvider = {
                PersonalMediaServerConfig(
                    baseUrl = "https://emby.example",
                    userId = "user-2",
                    accessToken = "emby-token",
                    downloadAllowed = true,
                )
            },
        )

        val result = source.resolve(movieRequest()) as MovieSeriesSourceResult.Found
        val video = result.hosters.single().videos.orEmpty().single()

        assertEquals(
            "https://emby.example/Videos/movie-1/stream.mp4?" +
                "Static=true&MediaSourceId=media-movie&PlaySessionId=play-movie",
            video.url,
        )
        assertFalse(video.downloadAllowed)
    }

    @Test
    fun `failed Jellyfin does not hide Emby series episode`() = runBlocking {
        val jellyfin = PersonalMediaServerPlaybackSource(
            client = HttpClient(MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) }),
            provider = PersonalMediaServerProvider.JELLYFIN,
            configProvider = { serverConfig("https://jellyfin.example", "jellyfin-token") },
        )
        var embyRequestIndex = 0
        val emby = PersonalMediaServerPlaybackSource(
            client = HttpClient(
                MockEngine { request ->
                    assertEquals("emby-token", request.headers["X-Emby-Token"])
                    when (embyRequestIndex++) {
                        0 -> respond(SERIES_SEARCH, HttpStatusCode.OK)
                        1 -> respond(EPISODES, HttpStatusCode.OK)
                        else -> respond(EMBY_HLS_PLAYBACK, HttpStatusCode.OK)
                    }
                }
            ),
            provider = PersonalMediaServerProvider.EMBY,
            configProvider = { serverConfig("https://emby.example", "emby-token") },
        )

        val result = resolveMovieSeriesSources(
            request = seriesRequest(),
            sources = listOf(jellyfin, emby),
            timeoutMs = 1_000,
        ) as PlaybackResolution.Found

        assertEquals(
            "https://emby.example/Videos/episode-2/master.m3u8?MediaSourceId=media-2&PlaySessionId=play-2",
            result.hosters.single().videos.orEmpty().single().url,
        )
        val hls = result.hosters.single().videos.orEmpty().single()
        assertEquals("vetro-test", hls.headers["X-Playback-Test"])
        assertFalse(hls.downloadAllowed)
    }

    @Test
    fun `canonical id conflict never falls back to matching title`() = runBlocking {
        val source = PersonalMediaServerPlaybackSource(
            client = HttpClient(MockEngine { respond(CONFLICTING_SEARCH, HttpStatusCode.OK) }),
            provider = PersonalMediaServerProvider.JELLYFIN,
            configProvider = { serverConfig("https://jellyfin.example", "token") },
        )

        assertEquals(MovieSeriesSourceResult.NoMatch, source.resolve(seriesRequest()))
    }

    @Test
    fun `ambiguous title fallback returns no match`() = runBlocking {
        val source = PersonalMediaServerPlaybackSource(
            client = HttpClient(MockEngine { respond(AMBIGUOUS_SEARCH, HttpStatusCode.OK) }),
            provider = PersonalMediaServerProvider.JELLYFIN,
            configProvider = { serverConfig("https://jellyfin.example", "token") },
        )
        val request = seriesRequest().copy(anime = anime(MediaType.SERIES, tmdbId = null))

        assertEquals(MovieSeriesSourceResult.NoMatch, source.resolve(request))
    }

    @Test
    fun `local download denial wins over server permission`() = runBlocking {
        val source = PersonalMediaServerPlaybackSource(
            client = HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/PlaybackInfo")) {
                        respond(EMBY_DIRECT_PLAYBACK, HttpStatusCode.OK)
                    } else {
                        respond(DOWNLOADABLE_MOVIE_SEARCH, HttpStatusCode.OK)
                    }
                }
            ),
            provider = PersonalMediaServerProvider.EMBY,
            configProvider = {
                serverConfig("https://emby.example", "token").copy(downloadAllowed = false)
            },
        )

        val result = source.resolve(movieRequest()) as MovieSeriesSourceResult.Found

        assertFalse(result.hosters.single().videos.orEmpty().single().downloadAllowed)
    }

    @Test
    fun `disabled direct capability falls back to enabled HLS`() = runBlocking {
        val result = movieSourceWithPlayback(DISABLED_DIRECT_WITH_HLS)
            .resolve(movieRequest()) as MovieSeriesSourceResult.Found

        assertEquals("HLS", result.hosters.single().videos.orEmpty().single().label)
    }

    @Test
    fun `disabled transcode capability rejects advertised URL`() = runBlocking {
        val result = movieSourceWithPlayback(DISABLED_TRANSCODE).resolve(movieRequest())

        assertEquals(MovieSeriesSourceResult.NoMatch, result)
    }

    @Test
    fun `personal server credential scope rejects sibling root`() {
        val config = serverConfig("https://media.example/jellyfin", "token")

        assertTrue(config.isAllowedUrl("https://media.example/jellyfin/Videos/1/stream.mp4"))
        assertFalse(config.isAllowedUrl("https://media.example/jellyfin-evil/Videos/1/stream.mp4"))
        assertFalse(config.isAllowedUrl("https://media.example/jellyfin/%2e%2e/sibling/stream.mp4"))
        assertTrue(containsSensitiveQuery("https://media.example/jellyfin/file?%74oken=value"))
        assertTrue(urlContainsSecret("https://media.example/file?key=%73ecret-token", "secret-token"))
        assertTrue(urlContainsSecret("https://media.example/Videos/abc/def", "abc/def"))
        assertTrue(urlContainsSecret("https://media.example/file?%73ecret-token=value", "secret-token"))
    }

    @Test
    fun `credentialed media blocks redirects and strips headers from cross origin children`() {
        val video = VetroVideo(
            url = "https://media.example/jellyfin/Videos/1/master.m3u8",
            label = "HLS",
            headers = mapOf("X-Emby-Token" to "secret-token"),
            credentialRef = PlaybackCredentialRef("emby:0123456789abcdef01234567"),
            credentialScope = requireNotNull(
                PlaybackAuthScope.create("https://media.example/jellyfin", allowQuery = true)
            ).credentialScope(),
        )
        val client = OkHttpClient().forPlaybackCandidate(video)

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertEquals(
            "secret-token",
            video.credentialHeadersFor("https://media.example/jellyfin/segment.ts")["X-Emby-Token"],
        )
        assertFalse(
            video.credentialHeadersFor("https://attacker.example/segment.ts")
                .containsKey("X-Emby-Token")
        )
        assertFalse(
            video.credentialHeadersFor("https://media.example/jellyfin/%2e%2e/segment.ts")
                .containsKey("X-Emby-Token")
        )
    }

    private fun seriesRequest() = PlaybackRequest(
        anime = anime(MediaType.SERIES, tmdbId = 1408),
        episodeNumber = 2,
        seasonInfo = SeasonInfo(1, 22, source = "TMDB"),
        language = AppLanguage.EN,
    )

    private fun movieRequest() = PlaybackRequest(
        anime = anime(MediaType.MOVIE, tmdbId = 603),
        episodeNumber = 1,
        language = AppLanguage.EN,
    )

    private fun anime(mediaType: MediaType, tmdbId: Int?) = Anime(
        id = "library-id",
        title = if (mediaType == MediaType.MOVIE) "The Matrix" else "Doctor House",
        episodes = 1,
        rating = 0f,
        imageFileName = null,
        orderIndex = 0,
        dateAdded = 0,
        mediaType = mediaType,
        tmdbId = tmdbId,
    )

    private fun serverConfig(baseUrl: String, token: String) = PersonalMediaServerConfig(
        baseUrl = baseUrl,
        userId = "user-1",
        accessToken = token,
    )

    private fun movieSourceWithPlayback(playback: String) = PersonalMediaServerPlaybackSource(
        client = HttpClient(
            MockEngine { request ->
                respond(
                    if (request.url.encodedPath.endsWith("/PlaybackInfo")) playback else MOVIE_SEARCH,
                    HttpStatusCode.OK,
                )
            }
        ),
        provider = PersonalMediaServerProvider.EMBY,
        configProvider = { serverConfig("https://emby.example", "token") },
    )

    private companion object {
        const val SERIES_SEARCH = """{
          "Items":[{"Id":"series-1","Name":"Doctor House","Type":"Series","ProviderIds":{"Tmdb":"1408"}}]
        }"""
        const val EPISODES = """{
          "Items":[
            {"Id":"episode-1","Name":"Pilot","Type":"Episode","ParentIndexNumber":1,"IndexNumber":1,"CanDownload":true},
            {"Id":"episode-2","Name":"Paternity","Type":"Episode","ParentIndexNumber":1,"IndexNumber":2,"CanDownload":true}
          ]
        }"""
        const val MOVIE_SEARCH = """{
          "Items":[{"Id":"movie-1","Name":"The Matrix","Type":"Movie","CanDownload":false,"ProviderIds":{"Tmdb":"603"}}]
        }"""
        const val DOWNLOADABLE_MOVIE_SEARCH = """{
          "Items":[{"Id":"movie-1","Name":"The Matrix","Type":"Movie","CanDownload":true,"ProviderIds":{"Tmdb":"603"}}]
        }"""
        const val CONFLICTING_SEARCH = """{
          "Items":[{"Id":"wrong-series","Name":"Doctor House","ProviderIds":{"Tmdb":"999"}}]
        }"""
        const val AMBIGUOUS_SEARCH = """{
          "Items":[
            {"Id":"series-a","Name":"Doctor House","ProviderIds":{}},
            {"Id":"series-b","Name":"Doctor House","ProviderIds":{}}
          ]
        }"""
        const val JELLYFIN_DIRECT_PLAYBACK = """{
          "PlaySessionId":"play-2",
          "MediaSources":[{
            "Id":"media-2","Container":"mp4","SupportsDirectStream":true
          }]
        }"""
        const val EMBY_DIRECT_PLAYBACK = """{
          "PlaySessionId":"play-movie",
          "MediaSources":[{
            "Id":"media-movie","SupportsDirectStream":true,
            "DirectStreamUrl":"/Videos/movie-1/stream.mp4?Static=true&MediaSourceId=media-movie&PlaySessionId=play-movie"
          }]
        }"""
        const val EMBY_HLS_PLAYBACK = """{
          "PlaySessionId":"play-2",
          "MediaSources":[{
            "Id":"media-2","SupportsTranscoding":true,
            "TranscodingUrl":"/Videos/episode-2/master.m3u8?MediaSourceId=media-2&PlaySessionId=play-2",
            "RequiredHttpHeaders":{"X-Playback-Test":"vetro-test"}
          }]
        }"""
        const val DISABLED_DIRECT_WITH_HLS = """{
          "PlaySessionId":"play-movie",
          "MediaSources":[
            {
              "Id":"disabled-direct","SupportsDirectStream":false,
              "DirectStreamUrl":"/Videos/movie-1/forbidden.mp4"
            },
            {
              "Id":"hls","SupportsTranscoding":true,
              "TranscodingUrl":"/Videos/movie-1/master.m3u8"
            }
          ]
        }"""
        const val DISABLED_TRANSCODE = """{
          "MediaSources":[{
            "Id":"disabled-hls","SupportsTranscoding":false,
            "TranscodingUrl":"/Videos/movie-1/master.m3u8"
          }]
        }"""
    }
}
