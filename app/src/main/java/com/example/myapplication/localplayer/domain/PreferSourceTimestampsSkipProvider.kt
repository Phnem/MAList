package com.example.myapplication.localplayer.domain

import com.example.myapplication.media.source.VetroTimestamp

/**
 * Prefers source-provided [VetroTimestamp]s when present; otherwise delegates to AniSkip API.
 */
class PreferSourceTimestampsSkipProvider(
    private val fallback: SkipSegmentProvider,
) : SkipSegmentProvider {

    @Volatile
    var sourceTimestamps: List<VetroTimestamp> = emptyList()

    override suspend fun fetch(
        anilistId: Int?,
        malId: Int?,
        episodeNumber: Int?,
        durationMs: Long,
    ): List<SkipSegment> {
        val fromSource = sourceTimestamps.map {
            SkipSegment(startMs = it.startMs, endMs = it.endMs, kind = it.kind)
        }
        if (fromSource.isNotEmpty()) return fromSource
        return fallback.fetch(anilistId, malId, episodeNumber, durationMs)
    }
}
