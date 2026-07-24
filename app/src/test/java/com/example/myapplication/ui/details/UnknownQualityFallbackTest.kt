package com.example.myapplication.ui.details

import com.example.myapplication.media.source.VetroVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class UnknownQualityFallbackTest {

    @Test
    fun preferred_unknown_quality_stream_is_still_playable() {
        val fallback = VetroVideo(
            url = "https://example.com/master.m3u8",
            label = "Auto",
            resolution = null,
            isPreferred = true,
        )

        assertEquals(fallback, chooseVideoForResolution(listOf(fallback), 1080))
    }
}
