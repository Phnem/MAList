package com.example.myapplication.media

import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.media.source.VetroHoster
import com.example.myapplication.media.source.VetroVideo
import com.example.myapplication.ui.details.DownloadQuality
import java.io.File

@JvmInline
value class JobId(val value: String)

/**
 * Single facade for resolve → play | download. UI depends only on this interface.
 */
interface MediaGateway {
    suspend fun resolveHosters(
        anime: Anime,
        episodeNumber: Int,
        seasonInfo: SeasonInfo? = null,
    ): List<VetroHoster>

    suspend fun resolveBestVideo(hosters: List<VetroHoster>): VetroVideo?

    suspend fun enqueueDownload(
        video: VetroVideo,
        fallbackVideos: List<VetroVideo> = emptyList(),
        quality: DownloadQuality,
        outDir: File,
        animeId: String = "",
        episodeNumber: Int = 0,
        durationSec: Int? = null,
    ): JobId
}
