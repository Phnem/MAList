package com.example.myapplication.media.player

import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.source.chunk.Chunk
import androidx.media3.exoplayer.source.chunk.MediaChunk
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter
import com.google.common.collect.ImmutableList

internal data class StalledChunkHealth(
    val safeBufferMs: Long,
    val hasLowerTrack: Boolean,
    val noProgressMs: Long,
    val loadedBytes: Long,
    val expectedBytes: Long?,
    val rolling3sBytesPerSecond: Long,
    val nowMs: Long,
    val lastCanceledAtMs: Long?,
)

internal class StalledChunkCancellationPolicy(
    private val maximumSafeBufferMs: Long = 10_000L,
    private val noProgressThresholdMs: Long = 4_000L,
    private val cancellationCooldownMs: Long = 15_000L,
    private val forecastSafetyMarginMs: Long = 1_000L,
) {
    fun shouldCancel(health: StalledChunkHealth): Boolean {
        if (!health.hasLowerTrack) return false
        if (health.safeBufferMs !in 0L..maximumSafeBufferMs) return false
        val previousCancellation = health.lastCanceledAtMs
        if (
            previousCancellation != null &&
            health.nowMs - previousCancellation < cancellationCooldownMs
        ) return false
        if (health.noProgressMs >= noProgressThresholdMs) return true

        val expectedBytes = health.expectedBytes ?: return false
        val remainingBytes = (expectedBytes - health.loadedBytes).coerceAtLeast(0L)
        val bytesPerSecond = health.rolling3sBytesPerSecond
        if (remainingBytes == 0L || bytesPerSecond <= 0L) return false
        val forecastMs = remainingBytes.toDouble() * 1_000.0 / bytesPerSecond.toDouble()
        val availableMs = (health.safeBufferMs - forecastSafetyMarginMs).coerceAtLeast(0L)
        return forecastMs > availableMs.toDouble()
    }
}

internal data class AdaptiveTrackQuality(
    val bitrate: Int,
    val height: Int,
)

internal fun trackIndicesAtOrAbove(
    tracks: List<AdaptiveTrackQuality>,
    loadingIndex: Int,
): List<Int> {
    val loading = tracks.getOrNull(loadingIndex) ?: return emptyList()
    return tracks.indices.filter { !tracks[it].isLowerThan(loading) }
}

private fun AdaptiveTrackQuality.isLowerThan(other: AdaptiveTrackQuality): Boolean = when {
    height > 0 && other.height > 0 && height != other.height -> height < other.height
    bitrate > 0 && other.bitrate > 0 -> bitrate < other.bitrate
    else -> false
}

internal fun trackIndicesBelow(
    tracks: List<AdaptiveTrackQuality>,
    loadingIndex: Int,
): List<Int> {
    val loading = tracks.getOrNull(loadingIndex) ?: return emptyList()
    return tracks.indices.filter { tracks[it].isLowerThan(loading) }
}

internal class StallAwareAdaptiveTrackSelectionFactory(
    private val tuning: StreamingPlayerTuning,
    private val transferMonitor: StreamingTransferMonitor,
    private val nowMs: () -> Long,
) : AdaptiveTrackSelection.Factory(
    tuning.minDurationForQualityIncreaseMs,
    tuning.maxDurationForQualityDecreaseMs,
    tuning.minDurationToRetainAfterDiscardMs,
    tuning.maxWidthToDiscard,
    tuning.maxHeightToDiscard,
    tuning.bandwidthFraction,
) {
    override fun createAdaptiveTrackSelection(
        group: TrackGroup,
        tracks: IntArray,
        type: Int,
        bandwidthMeter: BandwidthMeter,
        adaptationCheckpoints: ImmutableList<AdaptiveTrackSelection.AdaptationCheckpoint>,
    ): AdaptiveTrackSelection = StallAwareAdaptiveTrackSelection(
        group = group,
        tracks = tracks,
        type = type,
        bandwidthMeter = bandwidthMeter,
        adaptationCheckpoints = adaptationCheckpoints,
        tuning = tuning,
        transferMonitor = transferMonitor,
        nowMs = nowMs,
    )
}

private class StallAwareAdaptiveTrackSelection(
    group: TrackGroup,
    tracks: IntArray,
    type: Int,
    bandwidthMeter: BandwidthMeter,
    adaptationCheckpoints: List<AdaptiveTrackSelection.AdaptationCheckpoint>,
    tuning: StreamingPlayerTuning,
    private val transferMonitor: StreamingTransferMonitor,
    private val nowMs: () -> Long,
    private val cancellationPolicy: StalledChunkCancellationPolicy =
        StalledChunkCancellationPolicy(),
) : AdaptiveTrackSelection(
    group,
    tracks,
    type,
    bandwidthMeter,
    tuning.minDurationForQualityIncreaseMs.toLong(),
    tuning.maxDurationForQualityDecreaseMs.toLong(),
    tuning.minDurationToRetainAfterDiscardMs.toLong(),
    tuning.maxWidthToDiscard,
    tuning.maxHeightToDiscard,
    tuning.bandwidthFraction,
    DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
    adaptationCheckpoints,
    Clock.DEFAULT,
) {
    private data class SegmentCooldownKey(
        val uriHash: Int,
        val position: Long,
        val length: Long,
        val customKeyHash: Int?,
    )

    private val canceledSegments = LinkedHashMap<SegmentCooldownKey, Long>()

    override fun shouldCancelChunkLoad(
        playbackPositionUs: Long,
        loadingChunk: Chunk,
        queue: List<MediaChunk>,
    ): Boolean {
        val mediaChunk = loadingChunk as? MediaChunk ?: return false
        if (mediaChunk.startTimeUs == C.TIME_UNSET) return false
        val loadingIndex = indexOf(mediaChunk.trackFormat)
        if (loadingIndex == C.INDEX_UNSET) return false
        val qualities = (0 until length).map { index ->
            val format = getFormat(index)
            AdaptiveTrackQuality(format.bitrate, format.height)
        }
        val excludedIndices = trackIndicesAtOrAbove(qualities, loadingIndex)
        val now = nowMs()
        val availableLowerIndices = trackIndicesBelow(qualities, loadingIndex)
            .filterNot { isTrackExcluded(it, now) }
        val progress = transferMonitor.snapshot(mediaChunk.loadTaskId, mediaChunk.dataSpec)
            ?: return false
        val segmentKey = mediaChunk.dataSpec.segmentCooldownKey()
        val shouldCancel = cancellationPolicy.shouldCancel(
            StalledChunkHealth(
                safeBufferMs = (mediaChunk.startTimeUs - playbackPositionUs) / 1_000L,
                hasLowerTrack = availableLowerIndices.isNotEmpty(),
                noProgressMs = progress.noProgressMs,
                loadedBytes = progress.loadedBytes,
                expectedBytes = progress.expectedBytes,
                rolling3sBytesPerSecond = progress.rolling3sBps,
                nowMs = now,
                lastCanceledAtMs = canceledSegments[segmentKey],
            ),
        )
        if (!shouldCancel) return false
        excludedIndices
            .asSequence()
            .filter { it != loadingIndex }
            .filterNot { isTrackExcluded(it, now) }
            .forEach { index ->
                if (!excludeTrack(index, TRACK_EXCLUSION_MS)) return false
            }
        if (!excludeTrack(loadingIndex, TRACK_EXCLUSION_MS)) return false
        canceledSegments[segmentKey] = now
        trimCanceledSegments()
        transferMonitor.recordCancellationReason(
            mediaChunk.loadTaskId,
            mediaChunk.dataSpec,
            TransferCancellationReason.SLOW_CHUNK,
        )
        return true
    }

    private fun trimCanceledSegments() {
        while (canceledSegments.size > MAX_CANCELED_SEGMENTS) {
            canceledSegments.remove(canceledSegments.keys.first())
        }
    }

    private fun androidx.media3.datasource.DataSpec.segmentCooldownKey() = SegmentCooldownKey(
        uriHash = uri.hashCode(),
        position = position,
        length = length,
        customKeyHash = key?.hashCode(),
    )

    private companion object {
        const val TRACK_EXCLUSION_MS = 25_000L
        const val MAX_CANCELED_SEGMENTS = 64
    }
}
