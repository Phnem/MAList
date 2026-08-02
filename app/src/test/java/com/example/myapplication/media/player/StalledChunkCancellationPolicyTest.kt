package com.example.myapplication.media.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StalledChunkCancellationPolicyTest {

    @Test
    fun `requires a lower track and a chunk inside the danger buffer`() {
        assertFalse(policy.shouldCancel(health(hasLowerTrack = false, noProgressMs = 5_000L)))
        assertFalse(policy.shouldCancel(health(safeBufferMs = 10_001L, noProgressMs = 5_000L)))
        assertTrue(policy.shouldCancel(health(noProgressMs = 4_000L)))
    }

    @Test
    fun `forecast cancels only when remaining bytes cannot arrive before safety margin`() {
        assertFalse(
            policy.shouldCancel(
                health(
                    expectedBytes = 1_000_000L,
                    loadedBytes = 500_000L,
                    rolling3sBytesPerSecond = 100_000L,
                ),
            ),
        )
        assertTrue(
            policy.shouldCancel(
                health(
                    expectedBytes = 1_000_000L,
                    loadedBytes = 500_000L,
                    rolling3sBytesPerSecond = 50_000L,
                ),
            ),
        )
    }

    @Test
    fun `unknown size needs explicit no progress and cooldown blocks a repeat`() {
        assertFalse(policy.shouldCancel(health(expectedBytes = null, noProgressMs = 3_999L)))
        assertFalse(
            policy.shouldCancel(
                health(
                    noProgressMs = 8_000L,
                    nowMs = 100_000L,
                    lastCanceledAtMs = 90_001L,
                ),
            ),
        )
        assertTrue(
            policy.shouldCancel(
                health(
                    noProgressMs = 8_000L,
                    nowMs = 100_000L,
                    lastCanceledAtMs = 85_000L,
                ),
            ),
        )
    }

    @Test
    fun `current and higher qualities are excluded but lower alternatives remain`() {
        val tracks = listOf(
            AdaptiveTrackQuality(bitrate = 4_000_000, height = 1080),
            AdaptiveTrackQuality(bitrate = 2_000_000, height = 720),
            AdaptiveTrackQuality(bitrate = 800_000, height = 480),
        )

        assertEquals(listOf(0, 1), trackIndicesAtOrAbove(tracks, loadingIndex = 1))
    }

    @Test
    fun `same height with lower bitrate is a lower adaptive alternative`() {
        val tracks = listOf(
            AdaptiveTrackQuality(bitrate = 3_000_000, height = 720),
            AdaptiveTrackQuality(bitrate = 1_000_000, height = 720),
            AdaptiveTrackQuality(bitrate = 500_000, height = 480),
        )

        assertEquals(listOf(1, 2), trackIndicesBelow(tracks, loadingIndex = 0))
        assertEquals(listOf(0), trackIndicesAtOrAbove(tracks, loadingIndex = 0))
    }

    private fun health(
        safeBufferMs: Long = 8_000L,
        hasLowerTrack: Boolean = true,
        noProgressMs: Long = 0L,
        loadedBytes: Long = 500_000L,
        expectedBytes: Long? = null,
        rolling3sBytesPerSecond: Long = 0L,
        nowMs: Long = 100_000L,
        lastCanceledAtMs: Long? = null,
    ) = StalledChunkHealth(
        safeBufferMs = safeBufferMs,
        hasLowerTrack = hasLowerTrack,
        noProgressMs = noProgressMs,
        loadedBytes = loadedBytes,
        expectedBytes = expectedBytes,
        rolling3sBytesPerSecond = rolling3sBytesPerSecond,
        nowMs = nowMs,
        lastCanceledAtMs = lastCanceledAtMs,
    )

    private companion object {
        val policy = StalledChunkCancellationPolicy()
    }
}
