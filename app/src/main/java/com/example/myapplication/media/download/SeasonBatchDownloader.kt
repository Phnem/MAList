package com.example.myapplication.media.download

import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.media.JobId
import com.example.myapplication.media.MediaGateway
import java.io.File

/**
 * Phase 5: enqueue an entire season by resolving each episode then queueing downloads.
 */
class SeasonBatchDownloader(
    private val mediaGateway: MediaGateway,
) {
    suspend fun enqueueSeason(
        anime: Anime,
        season: SeasonInfo,
        quality: DownloadQuality,
        outDir: File,
        episodeFilter: Set<Int>? = null,
    ): List<JobId> {
        val count = season.episodes.coerceAtLeast(0)
        if (count == 0) return emptyList()
        val episodes = (1..count).filter { episodeFilter == null || it in episodeFilter }
        val jobs = mutableListOf<JobId>()
        for (ep in episodes) {
            val hosters = mediaGateway.resolveHosters(anime, ep, season)
            val video = mediaGateway.resolveBestVideo(hosters) ?: continue
            jobs += mediaGateway.enqueueDownload(
                video = video,
                quality = quality,
                outDir = outDir,
                animeId = anime.id,
                episodeNumber = ep,
            )
        }
        return jobs
    }
}
