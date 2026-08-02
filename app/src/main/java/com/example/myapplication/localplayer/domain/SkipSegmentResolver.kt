package com.example.myapplication.localplayer.domain

import com.example.myapplication.media.source.VetroSkipReference
import com.example.myapplication.media.source.VetroTimestamp
import kotlin.math.abs

data class SkipSegmentRequest(
    val anilistId: Int?,
    val malId: Int?,
    val episodeNumber: Int?,
    val durationMs: Long,
    val exactTimestamps: List<VetroTimestamp> = emptyList(),
    val exactOrigin: String? = null,
    val reference: VetroSkipReference? = null,
)

data class SkipSegmentResolution(
    val segments: List<SkipSegment>,
    val origin: String?,
    val referenceDurationMs: Long?,
)

/**
 * Single priority resolver for every player:
 * exact current-video timestamps → compatible episode reference → AniSkip.
 */
class SkipSegmentResolver internal constructor(
    private val aniSkip: AniSkipLookup,
) {

    suspend fun resolve(request: SkipSegmentRequest): SkipSegmentResolution {
        if (request.durationMs <= 0L) return SkipSegmentResolution(emptyList(), null, null)

        val exact = request.exactTimestamps.toSegments(request.durationMs)
        if (exact.isNotEmpty()) {
            return SkipSegmentResolution(
                segments = exact,
                origin = request.exactOrigin ?: EXACT_ORIGIN,
                referenceDurationMs = request.durationMs,
            )
        }

        val reference = request.reference
        if (reference != null) {
            val referenced = reference.segments
                .filter { timestamp ->
                    areSkipDurationsCompatible(
                        request.durationMs,
                        reference.referenceDurationMs,
                        timestamp.kind,
                        reference.origin,
                    )
                }
                .toSegments(request.durationMs)
            if (referenced.isNotEmpty()) {
                return SkipSegmentResolution(
                    segments = referenced,
                    origin = reference.origin,
                    referenceDurationMs = reference.referenceDurationMs,
                )
            }
        }

        val fallback = aniSkip.fetch(
            request.anilistId,
            request.malId,
            request.episodeNumber,
            request.durationMs,
        )
        return SkipSegmentResolution(
            segments = fallback.segments,
            origin = ANISKIP_ORIGIN.takeIf { fallback.segments.isNotEmpty() },
            referenceDurationMs = fallback.referenceDurationMs,
        )
    }

    private fun List<VetroTimestamp>.toSegments(durationMs: Long): List<SkipSegment> =
        mapNotNull { timestamp ->
            if (timestamp.startMs < 0L) return@mapNotNull null
            val start = timestamp.startMs
            val end = timestamp.endMs.coerceAtMost(durationMs)
            if (start >= end || start >= durationMs) return@mapNotNull null
            SkipSegment(start, end, timestamp.kind)
        }.sortedBy(SkipSegment::startMs)

    private companion object {
        const val EXACT_ORIGIN = "source"
        const val ANISKIP_ORIGIN = "AniSkip"
    }
}

internal fun areSkipDurationsCompatible(
    currentDurationMs: Long,
    referenceDurationMs: Long,
    kind: SkipKind? = null,
    origin: String? = null,
): Boolean {
    if (currentDurationMs <= 0L || referenceDurationMs <= 0L) return false
    val difference = abs(currentDurationMs - referenceDurationMs)
    val opening = kind == SkipKind.OPENING && origin.equals("jut.su", ignoreCase = true)
    val maxDifferenceMs = if (opening) MAX_OPENING_REFERENCE_DIFFERENCE_MS else MAX_REFERENCE_DIFFERENCE_MS
    val maxDifferenceRatio =
        if (opening) MAX_OPENING_REFERENCE_DIFFERENCE_RATIO else MAX_REFERENCE_DIFFERENCE_RATIO
    return difference <= maxDifferenceMs &&
        difference.toDouble() <= currentDurationMs.toDouble() * maxDifferenceRatio
}

private const val MAX_REFERENCE_DIFFERENCE_RATIO = 0.01
private const val MAX_REFERENCE_DIFFERENCE_MS = 15_000L
private const val MAX_OPENING_REFERENCE_DIFFERENCE_RATIO = 0.02
private const val MAX_OPENING_REFERENCE_DIFFERENCE_MS = 30_000L
