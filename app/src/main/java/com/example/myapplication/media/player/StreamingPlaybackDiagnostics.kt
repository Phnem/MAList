package com.example.myapplication.media.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.example.myapplication.media.source.VetroVideo
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

internal fun safeMediaHost(url: String): String = runCatching {
    URI(url).host?.lowercase()?.takeIf(String::isNotBlank)
}.getOrNull() ?: "unknown"

internal fun streamResolutionLabel(resolution: Int?, label: String): String =
    resolution?.takeIf { it > 0 }?.let { "${it}p" }
        ?: Regex("""(?i)(\d{3,4})p""").find(label)?.groupValues?.get(1)?.let { "${it}p" }
        ?: "auto"

internal fun safePlaybackResourceHost(resourceUrl: String, fallbackHost: String): String =
    safeMediaHost(resourceUrl).takeUnless { it == "unknown" } ?: fallbackHost

internal fun safeSourceLabel(sourceName: String?): String {
    val raw = sourceName?.trim().orEmpty()
    if (raw.isBlank() || "://" in raw || '?' in raw || '#' in raw) return "unknown"
    return raw
        .replace(Regex("""\s+"""), "_")
        .replace(Regex("""[^\p{L}\p{N}._-]"""), "_")
        .take(64)
        .ifBlank { "unknown" }
}

internal class PlaybackTelemetryRateLimiter(
    private val intervalMs: Long,
) {
    private var lastLoggedAtMs: Long? = null

    @Synchronized
    fun shouldLog(nowMs: Long): Boolean {
        val previous = lastLoggedAtMs
        if (previous != null && nowMs >= previous && nowMs - previous < intervalMs) return false
        if (previous != null && nowMs < previous) return false
        lastLoggedAtMs = nowMs
        return true
    }
}

internal fun formatPlaybackStateDiagnostic(
    state: String,
    host: String,
    resolution: String,
    positionMs: Long,
    bufferedMs: Long,
): String = "state=$state host=$host resolution=$resolution " +
    "positionMs=$positionMs bufferedMs=$bufferedMs"

internal fun formatBandwidthDiagnostic(
    host: String,
    resolution: String,
    bitrateBps: Long,
    bytes: Long,
    loadMs: Int,
    bufferedMs: Long,
): String = "bandwidth host=$host resolution=$resolution bitrateBps=$bitrateBps " +
    "bytes=$bytes loadMs=$loadMs bufferedMs=$bufferedMs"

internal fun formatLoadErrorDiagnostic(
    host: String,
    resolution: String,
    errorType: String,
    wasCanceled: Boolean,
    positionMs: Long,
    bufferedMs: Long,
): String = "loadError host=$host resolution=$resolution type=$errorType " +
    "canceled=$wasCanceled positionMs=$positionMs bufferedMs=$bufferedMs"

internal fun formatAudioUnderrunDiagnostic(
    host: String,
    resolution: String,
    bufferBytes: Int,
    bufferMs: Long,
    elapsedSinceFeedMs: Long,
    positionMs: Long,
): String = "audioUnderrun host=$host resolution=$resolution bufferBytes=$bufferBytes " +
    "bufferMs=$bufferMs elapsedSinceFeedMs=$elapsedSinceFeedMs positionMs=$positionMs"

internal data class ChunkLoadDiagnostic(
    val event: String,
    val loadId: Long,
    val host: String,
    val responseCode: Int?,
    val quality: String,
    val declaredBitrateBitsPerSecond: Int?,
    val segmentDurationMs: Long?,
    val expectedBytes: Long?,
    val actualBytes: Long,
    val requestStartMs: Long,
    val ttfbMs: Long?,
    val rolling1sBytesPerSecond: Long?,
    val rolling3sBytesPerSecond: Long?,
    val noProgressMs: Long?,
    val longestNoProgressMs: Long?,
    val bufferAtStartMs: Long?,
    val bufferAtEndMs: Long,
    val cancelReason: String?,
    val selectedSource: String,
)

internal fun formatChunkLoadDiagnostic(diagnostic: ChunkLoadDiagnostic): String = buildString {
    with(diagnostic) {
        append("chunk event=").append(event)
        append(" loadId=").append(loadId)
        append(" host=").append(host)
        append(" responseCode=").append(responseCode.telemetryValue())
        append(" quality=").append(quality)
        append(" declaredBitrateBitsPerSecond=")
            .append(declaredBitrateBitsPerSecond.telemetryValue())
        append(" segmentDurationMs=").append(segmentDurationMs.telemetryValue())
        append(" expectedBytes=").append(expectedBytes.telemetryValue())
        append(" actualBytes=").append(actualBytes)
        append(" requestStartMs=").append(requestStartMs)
        append(" ttfbMs=").append(ttfbMs.telemetryValue())
        append(" rolling1sBytesPerSecond=").append(rolling1sBytesPerSecond.telemetryValue())
        append(" rolling3sBytesPerSecond=").append(rolling3sBytesPerSecond.telemetryValue())
        append(" noProgressMs=").append(noProgressMs.telemetryValue())
        append(" longestNoProgressMs=").append(longestNoProgressMs.telemetryValue())
        append(" bufferAtStartMs=").append(bufferAtStartMs.telemetryValue())
        append(" bufferAtEndMs=").append(bufferAtEndMs)
        append(" cancelReason=").append(cancelReason ?: "none")
        append(" selectedSource=").append(selectedSource)
    }
}

internal fun isTerminalChunkEvent(event: String): Boolean = event != "started"

internal fun formatAllocatorDiagnostic(
    totalBytesAllocated: Int,
    targetBufferBytes: Int?,
    isLoading: Boolean,
    bufferedDurationMs: Long,
): String = "allocator allocatorBytes=$totalBytesAllocated " +
    "targetBufferBytes=${targetBufferBytes.telemetryValue(default = "auto")} " +
    "isLoading=$isLoading bufferedMs=$bufferedDurationMs"

private fun Any?.telemetryValue(default: String = "unknown"): String = this?.toString() ?: default

internal class StreamingPlaybackDiagnostics(
    video: VetroVideo,
    private val transferMonitor: StreamingTransferMonitor? = null,
    private val allocator: DefaultAllocator? = null,
    private val targetBufferBytes: Int? = null,
    private val bandwidthLimiter: PlaybackTelemetryRateLimiter =
        PlaybackTelemetryRateLimiter(BANDWIDTH_LOG_INTERVAL_MS),
) : AnalyticsListener {
    private val primaryHost = safeMediaHost(video.url)
    @Volatile
    private var mediaLoadHost = primaryHost
    private val resolution = streamResolutionLabel(video.resolution, video.label)
    private val selectedSource = safeSourceLabel(video.sourceName)
    private val bufferAtLoadStart = ConcurrentHashMap<Long, Long>()

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        transferMonitor?.registerLoad(loadEventInfo.loadTaskId, loadEventInfo.dataSpec)
        if (
            mediaLoadData.dataType == C.DATA_TYPE_MANIFEST ||
            mediaLoadData.dataType == C.DATA_TYPE_MEDIA
        ) {
            mediaLoadHost = safePlaybackResourceHost(
                loadEventInfo.uri.toString(),
                primaryHost,
            )
            bufferAtLoadStart[loadEventInfo.loadTaskId] = eventTime.totalBufferedDurationMs
            logChunkEvent("started", eventTime, loadEventInfo, mediaLoadData)
        }
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        logChunkEvent("completed", eventTime, loadEventInfo, mediaLoadData)
    }

    override fun onLoadCanceled(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        transferMonitor?.finishInterrupted(loadEventInfo.loadTaskId, loadEventInfo.dataSpec)
        logChunkEvent("canceled", eventTime, loadEventInfo, mediaLoadData)
    }

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int,
    ) {
        Log.i(
            TAG,
            formatPlaybackStateDiagnostic(
                state = stateName(state),
                host = primaryHost,
                resolution = resolution,
                positionMs = eventTime.currentPlaybackPositionMs,
                bufferedMs = eventTime.totalBufferedDurationMs,
            ),
        )
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long,
    ) {
        if (!bandwidthLimiter.shouldLog(eventTime.realtimeMs)) return
        Log.i(
            TAG,
            formatBandwidthDiagnostic(
                host = mediaLoadHost,
                resolution = resolution,
                bitrateBps = bitrateEstimate,
                bytes = totalBytesLoaded,
                loadMs = totalLoadTimeMs,
                bufferedMs = eventTime.totalBufferedDurationMs,
            ),
        )
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) {
        transferMonitor?.finishInterrupted(
            loadId = loadEventInfo.loadTaskId,
            dataSpec = loadEventInfo.dataSpec,
            responseCode = error.httpResponseCode(),
        )
        logChunkEvent("error", eventTime, loadEventInfo, mediaLoadData)
        Log.w(
            TAG,
            formatLoadErrorDiagnostic(
                host = safePlaybackResourceHost(loadEventInfo.uri.toString(), mediaLoadHost),
                resolution = resolution,
                errorType = error.javaClass.simpleName,
                wasCanceled = wasCanceled,
                positionMs = eventTime.currentPlaybackPositionMs,
                bufferedMs = eventTime.totalBufferedDurationMs,
            ),
        )
    }

    override fun onIsLoadingChanged(eventTime: AnalyticsListener.EventTime, isLoading: Boolean) {
        val currentAllocator = allocator ?: return
        Log.i(
            TAG,
            formatAllocatorDiagnostic(
                totalBytesAllocated = currentAllocator.totalBytesAllocated,
                targetBufferBytes = targetBufferBytes,
                isLoading = isLoading,
                bufferedDurationMs = eventTime.totalBufferedDurationMs,
            ),
        )
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        Log.w(
            TAG,
            formatAudioUnderrunDiagnostic(
                host = mediaLoadHost,
                resolution = resolution,
                bufferBytes = bufferSize,
                bufferMs = bufferSizeMs,
                elapsedSinceFeedMs = elapsedSinceLastFeedMs,
                positionMs = eventTime.currentPlaybackPositionMs,
            ),
        )
    }

    private fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN"
    }

    private fun logChunkEvent(
        event: String,
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        if (
            mediaLoadData.dataType != C.DATA_TYPE_MANIFEST &&
            mediaLoadData.dataType != C.DATA_TYPE_MEDIA
        ) return
        val terminal = isTerminalChunkEvent(event)
        val transfer = if (terminal) {
            transferMonitor?.consumeCompleted(loadEventInfo.loadTaskId, loadEventInfo.dataSpec)
        } else {
            null
        }
        val progress = transfer?.progress
            ?: transferMonitor?.snapshot(loadEventInfo.loadTaskId, loadEventInfo.dataSpec)
        val format = mediaLoadData.trackFormat
        val expectedBytes = progress?.expectedBytes
            ?: loadEventInfo.dataSpec.length.takeUnless { it == C.LENGTH_UNSET.toLong() }
            ?: loadEventInfo.responseHeaders.contentLength()
        Log.i(
            TAG,
            formatChunkLoadDiagnostic(
                ChunkLoadDiagnostic(
                    event = event,
                    loadId = loadEventInfo.loadTaskId,
                    host = safePlaybackResourceHost(loadEventInfo.uri.toString(), mediaLoadHost),
                    responseCode = transfer?.responseCode,
                    quality = format.qualityLabel(resolution),
                    declaredBitrateBitsPerSecond = format?.bitrate?.takeIf { it > 0 },
                    segmentDurationMs = mediaLoadData.segmentDurationMs(),
                    expectedBytes = expectedBytes,
                    actualBytes = loadEventInfo.bytesLoaded,
                    requestStartMs = progress?.requestStartMs
                        ?: (loadEventInfo.elapsedRealtimeMs - loadEventInfo.loadDurationMs),
                    ttfbMs = progress?.ttfbMs,
                    rolling1sBytesPerSecond = progress?.rolling1sBps,
                    rolling3sBytesPerSecond = progress?.rolling3sBps,
                    noProgressMs = progress?.noProgressMs,
                    longestNoProgressMs = progress?.longestNoProgressMs,
                    bufferAtStartMs = if (terminal) {
                        bufferAtLoadStart.remove(loadEventInfo.loadTaskId)
                    } else {
                        bufferAtLoadStart[loadEventInfo.loadTaskId]
                    },
                    bufferAtEndMs = eventTime.totalBufferedDurationMs,
                    cancelReason = transfer?.cancelReason
                        ?: if (event == "canceled") "media3" else null,
                    selectedSource = selectedSource,
                ),
            ),
        )
    }

    private companion object {
        const val TAG = "StreamTelemetry"
        const val BANDWIDTH_LOG_INTERVAL_MS = 10_000L
    }
}

private fun Format?.qualityLabel(fallback: String): String = this
    ?.height
    ?.takeIf { it > 0 }
    ?.let { "${it}p" }
    ?: fallback

private fun MediaLoadData.segmentDurationMs(): Long? = if (
    mediaStartTimeMs != C.TIME_UNSET &&
    mediaEndTimeMs != C.TIME_UNSET &&
    mediaEndTimeMs >= mediaStartTimeMs
) {
    mediaEndTimeMs - mediaStartTimeMs
} else {
    null
}

private fun Map<String, List<String>>.contentLength(): Long? = headerLong("Content-Length")

private fun Throwable.httpResponseCode(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
        current = current.cause
    }
    return null
}
