package com.example.myapplication.media

import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.media.source.VetroHoster
import com.example.myapplication.media.source.VetroVideo
import com.example.myapplication.media.source.PlaybackResolution
import com.example.myapplication.media.source.isSafeForBackgroundPersistence
import com.example.myapplication.ui.details.DownloadQuality
import java.io.File

@JvmInline
value class JobId(val value: String)

/**
 * Single facade for resolve → play | download. UI depends only on this interface.
 */
interface MediaGateway {
    suspend fun resolvePlayback(
        anime: Anime,
        episodeNumber: Int,
        seasonInfo: SeasonInfo? = null,
    ): PlaybackResolution {
        val hosters = resolveHosters(anime, episodeNumber, seasonInfo)
        return if (hosters.isEmpty()) {
            PlaybackResolution.NoMatch
        } else {
            PlaybackResolution.Found(hosters)
        }
    }

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

internal fun downloadableCandidates(
    video: VetroVideo,
    fallbackVideos: List<VetroVideo>,
): List<VetroVideo> {
    require(video.downloadAllowed) { "Источник разрешает только просмотр / Source is stream-only" }
    require(video.isSafeForBackgroundPersistence()) {
        "Секретный URL нельзя передать фоновой загрузке / Sensitive stream cannot be persisted"
    }
    return (listOf(video) + fallbackVideos)
        .filter { it.downloadAllowed && it.isSafeForBackgroundPersistence() }
        .distinctBy(VetroVideo::url)
}
