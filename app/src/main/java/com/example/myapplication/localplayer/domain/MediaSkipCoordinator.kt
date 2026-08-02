package com.example.myapplication.localplayer.domain

data class SkipMediaKey(
    val playerIdentity: Int,
    val mediaId: String,
    val episodeNumber: Int?,
)

enum class SkipSeekReason { AUTOMATIC, MANUAL }

data class SkipSeekDecision(
    val segment: SkipSegment,
    val targetMs: Long,
    val reason: SkipSeekReason,
)

/**
 * Pure state machine shared by local and streaming Compose adapters.
 *
 * The key owns all state. Installing another player, URL/media id or episode clears both the
 * resolved segments and automatic-seek deduplication.
 */
class MediaSkipCoordinator {
    private var currentKey: SkipMediaKey? = null
    private var resolution = SkipSegmentResolution(emptyList(), null, null)
    private var lastAutomaticSegment: SegmentIdentity? = null
    private var pendingAutomaticTargetMs: Long? = null

    fun install(key: SkipMediaKey, resolved: SkipSegmentResolution) {
        val mediaChanged = currentKey != key
        currentKey = key
        resolution = resolved
        if (mediaChanged) {
            lastAutomaticSegment = null
            pendingAutomaticTargetMs = null
        }
    }

    fun activeSegment(key: SkipMediaKey, positionMs: Long): SkipSegment? {
        if (key != currentKey) return null
        return resolution.segments.firstOrNull {
            positionMs >= it.startMs && positionMs < it.endMs
        }
    }

    fun automaticSeek(
        key: SkipMediaKey,
        positionMs: Long,
        enabled: Boolean,
    ): SkipSeekDecision? {
        if (key != currentKey) return null
        val active = activeSegment(key, positionMs)
        if (active == null) {
            lastAutomaticSegment = null
            pendingAutomaticTargetMs = null
            return null
        }
        if (!enabled) return null
        val identity = SegmentIdentity(active.startMs, active.endMs, active.kind)
        if (lastAutomaticSegment == identity) return null
        lastAutomaticSegment = identity
        pendingAutomaticTargetMs = active.endMs
        return SkipSeekDecision(active, active.endMs, SkipSeekReason.AUTOMATIC)
    }

    fun manualSeek(key: SkipMediaKey, positionMs: Long): SkipSeekDecision? {
        val active = activeSegment(key, positionMs) ?: return null
        return SkipSeekDecision(active, active.endMs, SkipSeekReason.MANUAL)
    }

    /**
     * A resume/user/source seek is a fresh segment-entry check, even if Compose saw no null gap.
     * The discontinuity caused by our own automatic seek is consumed without re-arming it.
     */
    fun onPositionDiscontinuity(key: SkipMediaKey, newPositionMs: Long) {
        if (key != currentKey) return
        val automaticTarget = pendingAutomaticTargetMs
        pendingAutomaticTargetMs = null
        if (
            automaticTarget != null &&
            kotlin.math.abs(newPositionMs - automaticTarget) <= OWN_SEEK_TOLERANCE_MS
        ) {
            return
        }
        lastAutomaticSegment = null
    }

    private data class SegmentIdentity(
        val startMs: Long,
        val endMs: Long,
        val kind: SkipKind,
    )

    private companion object {
        const val OWN_SEEK_TOLERANCE_MS = 1_000L
    }
}
