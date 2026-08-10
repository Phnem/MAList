package com.example.myapplication.media.source

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.AppLanguage
import org.junit.Assert.assertEquals
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
                assertEquals(
                    PlaybackRoute.DirectOnly,
                    PlaybackRoutingPolicy.route(mediaType, language),
                )
            }
        }
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
                playableHosters = attempts.flatMap { it.hosters },
                hadProviderFailure = attempts.any { it.failed },
            ),
        )
    }
}
