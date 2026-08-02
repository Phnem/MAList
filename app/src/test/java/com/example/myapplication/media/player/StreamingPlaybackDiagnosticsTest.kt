package com.example.myapplication.media.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPlaybackDiagnosticsTest {

    @Test
    fun `transfer accumulator measures ttfb rolling windows and no progress`() {
        val progress = TransferProgressAccumulator(expectedBytes = 1_000L)
        progress.start(nowMs = 1_000L)

        assertEquals(500L, progress.snapshot(nowMs = 1_500L).noProgressMs)

        progress.addBytes(nowMs = 2_000L, bytes = 100)
        progress.addBytes(nowMs = 2_500L, bytes = 200)
        val active = progress.snapshot(nowMs = 3_000L)

        assertEquals(1_000L, active.ttfbMs)
        assertEquals(300L, active.loadedBytes)
        assertEquals(300L, active.rolling1sBps)
        assertEquals(150L, active.rolling3sBps)
        assertEquals(500L, active.noProgressMs)
        assertEquals(1_000L, active.longestNoProgressMs)

        val stalled = progress.snapshot(nowMs = 6_000L)
        assertEquals(0L, stalled.rolling1sBps)
        assertEquals(0L, stalled.rolling3sBps)
        assertEquals(3_500L, stalled.noProgressMs)
        assertEquals(3_500L, stalled.longestNoProgressMs)
    }

    @Test
    fun `transfer accumulator coalesces hot callbacks into bounded time buckets`() {
        val progress = TransferProgressAccumulator(expectedBytes = null)
        progress.start(nowMs = 1_000L)

        repeat(10_000) { index ->
            progress.addBytes(nowMs = 1_000L + (index / 4), bytes = 1)
        }

        assertEquals(10_000L, progress.snapshot(nowMs = 3_499L).loadedBytes)
        assertTrue(progress.retainedSampleBucketCount <= 64)
    }

    @Test
    fun `error is a terminal chunk event and consumes start buffer state`() {
        assertTrue(isTerminalChunkEvent("completed"))
        assertTrue(isTerminalChunkEvent("canceled"))
        assertTrue(isTerminalChunkEvent("error"))
        assertFalse(isTerminalChunkEvent("started"))
    }

    @Test
    fun `chunk telemetry has correlation timing progress and source fields`() {
        val line = formatChunkLoadDiagnostic(
            ChunkLoadDiagnostic(
                event = "completed",
                loadId = 42L,
                host = "segments.cdn.example",
                responseCode = 206,
                quality = "720p",
                declaredBitrateBitsPerSecond = 2_000_000,
                segmentDurationMs = 6_000L,
                expectedBytes = 1_500_000L,
                actualBytes = 1_400_000L,
                requestStartMs = 10_000L,
                ttfbMs = 180L,
                rolling1sBytesPerSecond = 250_000L,
                rolling3sBytesPerSecond = 210_000L,
                noProgressMs = 300L,
                longestNoProgressMs = 700L,
                bufferAtStartMs = 15_000L,
                bufferAtEndMs = 9_000L,
                cancelReason = null,
                selectedSource = "AniLibria",
            ),
        )

        for (field in listOf(
            "event=completed",
            "loadId=42",
            "responseCode=206",
            "declaredBitrateBitsPerSecond=2000000",
            "segmentDurationMs=6000",
            "expectedBytes=1500000",
            "actualBytes=1400000",
            "requestStartMs=10000",
            "ttfbMs=180",
            "rolling1sBytesPerSecond=250000",
            "rolling3sBytesPerSecond=210000",
            "noProgressMs=300",
            "longestNoProgressMs=700",
            "bufferAtStartMs=15000",
            "bufferAtEndMs=9000",
            "selectedSource=AniLibria",
        )) {
            assertTrue("missing $field in $line", line.contains(field))
        }
    }

    @Test
    fun `load correlator uses request identity when concurrent callbacks arrive in reverse order`() {
        val correlator = LoadIdCorrelator<Any>(maxPending = 8)
        val firstSource = Any()
        val secondSource = Any()
        val firstRequest = Any()
        val secondRequest = Any()
        correlator.register(loadId = 10L, key = firstRequest)
        correlator.register(loadId = 11L, key = secondRequest)

        assertEquals(11L, correlator.begin(secondSource, secondRequest))
        assertEquals(10L, correlator.begin(firstSource, firstRequest))
        assertEquals(11L, correlator.end(secondSource, secondRequest))
        assertEquals(10L, correlator.end(firstSource, firstRequest))
    }

    @Test
    fun `late binding removes source from waiting queue before next request`() {
        val correlator = LoadIdCorrelator<Any>(maxPending = 8)
        val request = Any()
        val partialRetrySource = Any()

        assertEquals(null, correlator.begin(partialRetrySource, request))
        correlator.bind(partialRetrySource, loadId = 42L, key = request)
        assertEquals(42L, correlator.end(partialRetrySource, request))

        correlator.register(loadId = 43L, key = request)
        val nextSource = Any()
        assertEquals(43L, correlator.begin(nextSource, request))
        assertEquals(43L, correlator.end(nextSource, request))
    }

    @Test
    fun `source labels cannot turn urls or tokens into telemetry`() {
        assertEquals("unknown", safeSourceLabel("https://cdn.example/video?token=secret"))
        assertEquals("Kodik_HD", safeSourceLabel("Kodik HD"))
    }

    @Test
    fun `allocator telemetry exposes byte and time loading state`() {
        val line = formatAllocatorDiagnostic(
            totalBytesAllocated = 12_345,
            targetBufferBytes = null,
            isLoading = true,
            bufferedDurationMs = 40_000L,
        )

        assertTrue(line.contains("allocatorBytes=12345"))
        assertTrue(line.contains("targetBufferBytes=auto"))
        assertTrue(line.contains("isLoading=true"))
        assertTrue(line.contains("bufferedMs=40000"))
    }

    @Test
    fun `media host never includes path query credentials or token`() {
        val url = "https://user:password@cdn.example/video/master.m3u8?token=secret"

        assertEquals("cdn.example", safeMediaHost(url))
        assertEquals("unknown", safeMediaHost("not a url"))
        assertEquals(
            "segments.cdn.example",
            safePlaybackResourceHost(
                resourceUrl = "https://segments.cdn.example/hls/001.ts?token=segment-secret",
                fallbackHost = "master.example",
            ),
        )
        assertEquals(
            "master.example",
            safePlaybackResourceHost("invalid", "master.example"),
        )
    }

    @Test
    fun `bandwidth telemetry is rate limited using monotonic time`() {
        val limiter = PlaybackTelemetryRateLimiter(intervalMs = 10_000L)

        assertTrue(limiter.shouldLog(1_000L))
        assertFalse(limiter.shouldLog(10_999L))
        assertTrue(limiter.shouldLog(11_000L))
        assertFalse(limiter.shouldLog(10_500L))
    }

    @Test
    fun `resolution prefers structured value and otherwise parses label`() {
        assertEquals("720p", streamResolutionLabel(720, "HD"))
        assertEquals("1080p", streamResolutionLabel(null, "Full HD 1080p"))
        assertEquals("auto", streamResolutionLabel(null, "adaptive"))
    }

    @Test
    fun `state and bandwidth formatters contain actionable structured fields`() {
        val state = formatPlaybackStateDiagnostic(
            state = "BUFFERING",
            host = "cdn.example",
            resolution = "720p",
            positionMs = 12_000L,
            bufferedMs = 2_500L,
        )
        val bandwidth = formatBandwidthDiagnostic(
            host = "cdn.example",
            resolution = "720p",
            bitrateBps = 3_000_000L,
            bytes = 524_288L,
            loadMs = 950,
            bufferedMs = 9_000L,
        )

        assertTrue(state.contains("positionMs=12000"))
        assertTrue(state.contains("bufferedMs=2500"))
        assertTrue(bandwidth.contains("host=cdn.example"))
        assertTrue(bandwidth.contains("resolution=720p"))
        assertTrue(bandwidth.contains("bitrateBps=3000000"))
        assertTrue(bandwidth.contains("bytes=524288"))
    }

    @Test
    fun `failure formatters have no channel for urls messages or tokens`() {
        val secretUrl = "https://cdn.example/video.m3u8?token=do-not-log"
        val loadError = formatLoadErrorDiagnostic(
            host = safeMediaHost(secretUrl),
            resolution = "720p",
            errorType = "HttpDataSourceException",
            wasCanceled = false,
            positionMs = 1_000L,
            bufferedMs = 0L,
        )
        val underrun = formatAudioUnderrunDiagnostic(
            host = safeMediaHost(secretUrl),
            resolution = "720p",
            bufferBytes = 0,
            bufferMs = 0L,
            elapsedSinceFeedMs = 400L,
            positionMs = 1_000L,
        )

        for (line in listOf(loadError, underrun)) {
            assertFalse(line.contains("https://"))
            assertFalse(line.contains("do-not-log"))
            assertFalse(line.contains("token="))
        }
    }
}
