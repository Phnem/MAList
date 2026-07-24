package com.example.myapplication.media.ui

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.myapplication.localplayer.domain.SkipSegment
import com.example.myapplication.localplayer.domain.SkipSegmentProvider
import com.example.myapplication.localplayer.ui.AudioTrackOption
import com.example.myapplication.localplayer.ui.PlayerControlsOverlay
import com.example.myapplication.localplayer.ui.VideoFit
import com.example.myapplication.media.source.VetroVideo
import com.example.myapplication.ui.shared.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import kotlin.math.max

/** The custom Exo controls applied to a remote, header-aware player. */
@Composable
fun StreamPlayerSurface(
    player: ExoPlayer,
    video: VetroVideo,
    renditions: List<VetroVideo> = emptyList(),
    onSelectRendition: (VetroVideo) -> Unit = {},
    title: String,
    episodeNumber: Int,
    malId: Int?,
    anilistId: Int?,
    autoSkipEnabled: Boolean,
    isInPip: Boolean,
    onEnterPip: () -> Unit,
    onRotate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skipProvider = koinInject<SkipSegmentProvider>()
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var isBuffering by remember(player) { mutableStateOf(true) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var buffered by remember(player) { mutableLongStateOf(player.bufferedPosition) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var embeddedAudioTracks by remember(player) {
        mutableStateOf<List<AudioTrackOption>>(emptyList())
    }
    var speed by remember(player) { mutableStateOf(1f) }
    var fit by remember(player) { mutableStateOf(VideoFit.ORIGINAL) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var videoAspect by remember(player) { mutableFloatStateOf(16f / 9f) }
    val viewportAspect = viewportSize.width.toFloat() / viewportSize.height.coerceAtLeast(1)
    val cropScale = max(
        viewportAspect / videoAspect.coerceAtLeast(0.01f),
        videoAspect / viewportAspect.coerceAtLeast(0.01f),
    ).coerceIn(1f, 2.5f)
    val animatedVideoScale by animateFloatAsState(
        targetValue = if (fit == VideoFit.CROP) cropScale else 1f,
        animationSpec = MotionTokens.standard(),
        label = "streamVideoFitScale",
    )
    var segments by remember(video.url) { mutableStateOf<List<SkipSegment>>(emptyList()) }

    val studioTracks = remember(renditions, video.url) {
        renditions
            .filter { !it.sourceName.isNullOrBlank() }
            .distinctBy { it.sourceName }
            .takeIf { it.size > 1 }
            .orEmpty()
            .map { rendition ->
                AudioTrackOption(
                    id = "rendition:${rendition.url}",
                    label = rendition.sourceName.orEmpty(),
                    isSelected = rendition.url == video.url,
                    groupIndex = -1,
                    trackIndex = -1,
                    renditionUrl = rendition.url,
                )
            }
    }
    // A single embedded track adds no choice. Multiple embedded tracks remain available together
    // with whole-studio renditions supplied by separate providers.
    val audioTracks =
        studioTracks + embeddedAudioTracks.takeIf { it.size > 1 }.orEmpty()

    LaunchedEffect(video.url, duration, malId, anilistId, episodeNumber) {
        if (duration <= 0L) return@LaunchedEffect
        val embedded = video.timestamps.map {
            SkipSegment(it.startMs, it.endMs, it.kind)
        }
        segments = embedded.ifEmpty {
            runCatching {
                skipProvider.fetch(anilistId, malId, episodeNumber, duration)
            }.getOrDefault(emptyList())
        }
    }
    val activeSegment by remember {
        derivedStateOf {
            segments.firstOrNull { position >= it.startMs && position < it.endMs }
        }
    }
    LaunchedEffect(activeSegment, autoSkipEnabled) {
        val segment = activeSegment
        if (autoSkipEnabled && segment != null) player.seekTo(segment.endMs)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState != Player.STATE_IDLE) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                embeddedAudioTracks = tracks.streamAudioOptions()
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    videoAspect = size.width * size.pixelWidthHeightRatio / size.height
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                position = player.currentPosition
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            player.duration.takeIf { it > 0L }?.let { duration = it }
            delay(250L)
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .graphicsLayer {
                    scaleX = animatedVideoScale
                    scaleY = animatedVideoScale
                },
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

                view.keepScreenOn = isPlaying
            },
        )

        if (!isInPip) {
            PlayerControlsOverlay(
                player = player,
                title = title,
                ambient = Color(0xFF141414),
                onRotate = onRotate,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                position = position,
                buffered = buffered,
                duration = duration,
                hasPrev = false,
                hasNext = false,
                audioTracks = audioTracks,
                speed = speed,
                fit = fit,
                skipVisible = !autoSkipEnabled && activeSegment != null,
                onSkip = { activeSegment?.let { player.seekTo(it.endMs) } },
                onBack = onBack,
                onEnterPip = onEnterPip,
                onSelectSpeed = {
                    speed = it
                    player.playbackParameters = PlaybackParameters(it)
                },
                onSelectAudio = { option ->
                    val rendition = option.renditionUrl
                        ?.let { url -> renditions.firstOrNull { it.url == url } }
                    if (rendition != null) {
                        onSelectRendition(rendition)
                    } else {
                        player.applyStreamAudioOverride(option)
                    }
                },
                onCycleFit = {
                    fit = if (fit == VideoFit.ORIGINAL) VideoFit.CROP else VideoFit.ORIGINAL
                },
            )
        }
    }
}

private fun Tracks.streamAudioOptions(): List<AudioTrackOption> {
    val result = ArrayList<AudioTrackOption>()
    groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            val label = format.label
                ?: format.language?.let {
                    java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it }
                }
                ?: "Track ${result.size + 1}"
            result += AudioTrackOption(
                id = "$groupIndex:$trackIndex",
                label = label.replaceFirstChar { it.uppercase() },
                isSelected = group.isTrackSelected(trackIndex),
                groupIndex = groupIndex,
                trackIndex = trackIndex,
            )
        }
    }
    return result
}

private fun ExoPlayer.applyStreamAudioOverride(option: AudioTrackOption) {
    val group = currentTracks.groups.getOrNull(option.groupIndex) ?: return
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex))
        )
        .build()
}
