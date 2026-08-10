package com.example.myapplication.media.source

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PlaybackRoutingPolicyTest {

    @Test
    fun `Russian anime keeps the existing native cascade`() {
        assertEquals(
            PlaybackRoute.AnimeRu,
            PlaybackRoutingPolicy.route(MediaType.ANIME, AppLanguage.RU),
        )
    }

    @Test
    fun `English anime keeps AnimeHeaven and reference cascade`() {
        assertEquals(
            PlaybackRoute.AnimeEn,
            PlaybackRoutingPolicy.route(MediaType.ANIME, AppLanguage.EN),
        )
    }

    @Test
    fun `movies and series never route through anime providers`() {
        listOf(MediaType.MOVIE, MediaType.SERIES).forEach { mediaType ->
            AppLanguage.entries.forEach { language ->
                val route = PlaybackRoutingPolicy.route(mediaType, language)
                assertNotEquals(PlaybackRoute.AnimeRu, route)
                assertNotEquals(PlaybackRoute.AnimeEn, route)
            }
        }
    }

    @Test
    fun `movies and series get a separate cascade per language`() {
        listOf(MediaType.MOVIE, MediaType.SERIES).forEach { mediaType ->
            assertEquals(
                PlaybackRoute.MovieSeriesRu,
                PlaybackRoutingPolicy.route(mediaType, AppLanguage.RU),
            )
            assertEquals(
                PlaybackRoute.MovieSeriesEn,
                PlaybackRoutingPolicy.route(mediaType, AppLanguage.EN),
            )
        }
    }

    @Test
    fun `only movie series routes report a resolution language`() {
        assertEquals(AppLanguage.RU, PlaybackRoute.MovieSeriesRu.movieSeriesLanguage)
        assertEquals(AppLanguage.EN, PlaybackRoute.MovieSeriesEn.movieSeriesLanguage)
        assertNull(PlaybackRoute.AnimeRu.movieSeriesLanguage)
        assertNull(PlaybackRoute.AnimeEn.movieSeriesLanguage)
        assertNull(PlaybackRoute.None.movieSeriesLanguage)
    }

    @Test
    fun `manga has no video route`() {
        assertEquals(
            PlaybackRoute.None,
            PlaybackRoutingPolicy.route(MediaType.MANGA, AppLanguage.RU),
        )
    }

    @Test
    fun `one provider failure does not cancel another applicable provider`() = runBlocking {
        val hoster = VetroHoster(
            name = "working source",
            videos = listOf(VetroVideo(url = "https://media.example/episode.mp4", label = "Auto")),
        )
        val attempts = runPlaybackProviderCascade(
            listOf(
                PlaybackProviderCall("broken", 1_000) { error("provider is down") },
                PlaybackProviderCall("working", 1_000) { listOf(hoster) },
            )
        )

        assertEquals(
            PlaybackResolution.Found(listOf(hoster)),
            playbackResolution(
                playableHosters = attempts.flatMap { it.value.orEmpty() },
                hadProviderFailure = attempts.any { it.failed },
            ),
        )
    }
}
