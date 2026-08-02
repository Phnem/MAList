package com.example.myapplication.media.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingPlayerTuningTest {

    @Test
    fun `remote buffer keeps sixty seconds and resumes conservatively after rebuffer`() {
        val tuning = StreamingPlayerTuning.DEFAULT

        assertEquals(60_000, tuning.minBufferMs)
        assertEquals(90_000, tuning.maxBufferMs)
        assertEquals(2_000, tuning.bufferForPlaybackMs)
        assertEquals(6_000, tuning.bufferForPlaybackAfterRebufferMs)
        assertEquals(true, tuning.prioritizeTimeOverSizeThresholds)
    }

    @Test
    fun `adaptive selection rises slowly drops early and retains buffered chunks`() {
        val tuning = StreamingPlayerTuning.DEFAULT

        assertEquals(25_000, tuning.minDurationForQualityIncreaseMs)
        assertEquals(10_000, tuning.maxDurationForQualityDecreaseMs)
        assertEquals(25_000, tuning.minDurationToRetainAfterDiscardMs)
        assertEquals(0, tuning.maxWidthToDiscard)
        assertEquals(0, tuning.maxHeightToDiscard)
        assertEquals(0.60f, tuning.bandwidthFraction)
    }
}
