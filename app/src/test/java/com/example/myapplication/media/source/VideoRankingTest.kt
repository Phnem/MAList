package com.example.myapplication.media.source

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoRankingTest {

    @Test
    fun `AniLiberty HLS wins over preferred hotlink MP4 at same resolution`() {
        val placeholderProne = VetroVideo(
            url = "https://video.jut.su/episode.mp4",
            label = "720p",
            resolution = 720,
            isPreferred = true,
        )
        val hls = VetroVideo(
            url = "https://cache.libria.fun/episode/720/index.m3u8",
            label = "720p",
            resolution = 720,
        )

        val ranked = rankVideosForResolution(listOf(placeholderProne, hls), 720)

        assertEquals(hls, ranked.first())
        assertEquals(placeholderProne, ranked.last())
    }
}
