package com.example.myapplication.media.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.example.myapplication.media.source.SanitizeHeaders
import com.example.myapplication.media.source.VetroSubtitleTrack
import com.example.myapplication.media.source.VetroVideo
import okhttp3.OkHttpClient

/**
 * Builds header-aware Media3 sources. Source-provided external audio is merged only when the same
 * provider explicitly marks it as a synchronized track; unrelated studio releases are switched as
 * complete renditions by StreamPlayerActivity instead.
 */
object HeaderResolvingPlayerFactory {

    fun createPlayer(context: Context, client: OkHttpClient, video: VetroVideo): ExoPlayer {
        val mediaSourceFactory = createMediaSourceFactory(client, video)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    fun buildMediaSource(client: OkHttpClient, video: VetroVideo): MediaSource {
        val factory = createMediaSourceFactory(client, video)
        val primary = factory.createMediaSource(buildMediaItem(video))
        val externalAudio = video.audioTracks.map { track ->
            factory.createMediaSource(buildTrackMediaItem(track))
        }
        return if (externalAudio.isEmpty()) {
            primary
        } else {
            MergingMediaSource(primary, *externalAudio.toTypedArray())
        }
    }

    fun buildMediaItem(video: VetroVideo): MediaItem {
        val builder = MediaItem.Builder().setUri(Uri.parse(video.url))
        when {
            video.url.contains(".m3u8", ignoreCase = true) ->
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            video.url.contains(".mpd", ignoreCase = true) ->
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        val subs = video.subtitles.map { it.toSubtitleConfig() }
        if (subs.isNotEmpty()) builder.setSubtitleConfigurations(subs)
        return builder.build()
    }

    fun createMediaSourceFactory(client: OkHttpClient, video: VetroVideo): DefaultMediaSourceFactory {
        val headers = SanitizeHeaders.sanitize(video.headers)
        val okHttpFactory = OkHttpDataSource.Factory(client)
        val resolvingFactory = ResolvingDataSource.Factory(okHttpFactory) { dataSpec: DataSpec ->
            if (headers.isEmpty()) dataSpec else dataSpec.withRequestHeaders(headers)
        }
        return DefaultMediaSourceFactory(resolvingFactory)
    }

    private fun buildTrackMediaItem(track: VetroSubtitleTrack): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(Uri.parse(track.url))
            .setMediaId("external-audio:${track.lang}")
        when {
            track.url.contains(".m3u8", ignoreCase = true) ->
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            track.url.contains(".mpd", ignoreCase = true) ->
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        return builder.build()
    }

    private fun VetroSubtitleTrack.toSubtitleConfig(): MediaItem.SubtitleConfiguration {
        val mime = when {
            mimeType.contains("ssa", ignoreCase = true) ||
                mimeType.contains("ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
            mimeType.contains("vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
            else -> MimeTypes.TEXT_VTT
        }
        return MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
            .setMimeType(mime)
            .setLanguage(lang)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }
}
