package com.example.myapplication.localplayer.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.myapplication.localplayer.model.LocalEpisode
import com.example.myapplication.ui.shared.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max

/** Один вариант аудиодорожки для выбора (например «Русский», «English», «Japanese»). */
data class AudioTrackOption(
    val id: String,
    val label: String,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int,
    val renditionUrl: String? = null,
)

/** Режим кадра: как есть (letterbox, обычно 16:9) или с обрезкой краёв (заполнить экран). */
enum class VideoFit { ORIGINAL, CROP }

/**
 * Полноэкранный проигрыватель на androidx.media3 (ExoPlayer) с полностью кастомным скином:
 * плоские доки + белая полоса с оранжевым бегунком (см. [PlayerControlsOverlay]).
 * Стандартный контроллер [PlayerView] отключён (`useController = false`) — все кнопки наши,
 * никаких дефолтных +5/−5.
 *
 * Играет строго локальные `content://` из выбранной пользователем папки. Сетевого стриминга нет.
 */
@Composable
fun PlayerScreen(
    episodes: List<LocalEpisode>,
    startIndex: Int,
    malId: Int?,
    anilistId: Int?,
    autoSkipEnabled: Boolean,
    isInPip: Boolean,
    onEnterPip: () -> Unit,
    onRotate: () -> Unit,
    onBack: () -> Unit,
    onPlayerAvailable: (ExoPlayer?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val skipProvider = org.koin.compose.koinInject<com.example.myapplication.localplayer.domain.SkipSegmentProvider>()

    val exoPlayer = remember {
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
            val items = episodes.map { MediaItem.fromUri(Uri.parse(it.documentUri)) }
            setMediaItems(items, startIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0)), 0L)
            playWhenReady = true
            prepare()
        }
    }

    // ——— Наблюдаемое состояние плеера ———
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentIndex by remember { mutableIntStateOf(startIndex.coerceAtLeast(0)) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var speed by remember { mutableStateOf(1f) }
    var fit by remember { mutableStateOf(VideoFit.ORIGINAL) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    // Сюрфейс плеера — источник кадра для тона доков (PixelCopy), см. rememberPlayerAmbient.
    var videoSurface by remember { mutableStateOf<android.view.SurfaceView?>(null) }
    var drmProtected by remember { mutableStateOf(false) }
    val viewportAspect = viewportSize.width.toFloat() / viewportSize.height.coerceAtLeast(1)
    val cropScale = max(
        viewportAspect / videoAspect.coerceAtLeast(0.01f),
        videoAspect / viewportAspect.coerceAtLeast(0.01f),
    ).coerceIn(1f, 2.5f)
    val animatedVideoScale by animateFloatAsState(
        targetValue = if (fit == VideoFit.CROP) cropScale else 1f,
        animationSpec = MotionTokens.standard(),
        label = "localVideoFitScale",
    )
    var playbackError by remember { mutableStateOf<String?>(null) }

    // ——— Тайминги пропуска опенинга/эндинга (AniSkip) ———
    var segments by remember { mutableStateOf<List<com.example.myapplication.localplayer.domain.SkipSegment>>(emptyList()) }
    val durationKnown = duration > 0
    androidx.compose.runtime.LaunchedEffect(currentIndex, durationKnown, malId) {
        segments = emptyList()
        if (!durationKnown) return@LaunchedEffect
        val epNum = episodes.getOrNull(currentIndex)?.episodeNumber
        segments = runCatching { skipProvider.fetch(anilistId, malId, epNum, duration) }.getOrDefault(emptyList())
    }
    val activeSegment by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            segments.firstOrNull { position >= it.startMs && position < it.endMs }
        }
    }
    // Автоскип: если включён в настройках (по умолчанию ВЫКЛ) — молча перескакиваем.
    androidx.compose.runtime.LaunchedEffect(activeSegment, autoSkipEnabled) {
        val seg = activeSegment
        if (autoSkipEnabled && seg != null) exoPlayer.seekTo(seg.endMs)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) playbackError = null
                if (state != Player.STATE_IDLE) duration = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("LocalPlayer", "Playback failed: ${error.errorCodeName}", error)
                isBuffering = false
                playbackError = "Файл повреждён или имеет неподдерживаемый формат"
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exoPlayer.currentMediaItemIndex
            }

            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = tracks.extractAudioOptions()
                drmProtected = tracks.isDrmProtected()
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    videoAspect = size.width * size.pixelWidthHeightRatio / size.height
                }
            }

            // Позицию после seek применяем сразу — иначе бегунок на кадр «отскакивает» назад к старой
            // позиции (poll ещё не успел обновиться), создавая ощущение лага.
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                position = exoPlayer.currentPosition
            }
        }
        onPlayerAvailable(exoPlayer)
        exoPlayer.addListener(listener)
        onDispose {
            onPlayerAvailable(null)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Media session: аппаратные/BT-кнопки (пауза, next/prev) маршрутизируются в плеер.
    DisposableEffect(exoPlayer) {
        val session = androidx.media3.session.MediaSession.Builder(context, exoPlayer)
            .setId("vetro-local-player-${System.currentTimeMillis()}")
            .build()
        onDispose { session.release() }
    }

    // PiP: отдаём плеер Activity, чтобы её кнопки управления слали команды сюда.
    val pipActivity = remember(context) {
        var c: android.content.Context? = context
        var found: LocalPlayerActivity? = null
        while (c is android.content.ContextWrapper) {
            if (c is LocalPlayerActivity) { found = c; break }
            c = c.baseContext
        }
        found
    }
    DisposableEffect(exoPlayer, pipActivity) {
        pipActivity?.attachPipPlayer(exoPlayer)
        onDispose { pipActivity?.attachPipPlayer(null) }
    }
    androidx.compose.runtime.LaunchedEffect(isPlaying, pipActivity) {
        pipActivity?.refreshPipParams()
    }

    // Опрос позиции (дешевле, чем per-frame; хватает для плавной полосы).
    androidx.compose.runtime.LaunchedEffect(exoPlayer) {
        while (isActive) {
            position = exoPlayer.currentPosition
            buffered = exoPlayer.bufferedPosition
            val d = exoPlayer.duration
            if (d > 0) duration = d
            delay(250)
        }
    }

    // Полный экран: статус-бар и навигация уезжают, пока виден кадр (в PiP окно и так без баров).
    ImmersivePlayerWindow(enabled = !isInPip)

    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .graphicsLayer {
                    scaleX = animatedVideoScale
                    scaleY = animatedVideoScale
                },
            factory = { ctx ->
                // surface_type по умолчанию = SurfaceView: дешевле по батарее и композитингу, чем
                // TextureView. Не переводить на TextureView ради блюра — доки плоские, кадр
                // для их тона снимается отдельно через PixelCopy (см. rememberPlayerAmbient).
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    videoSurface = videoSurfaceView as? android.view.SurfaceView
                }
            },
            update = { view ->
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

                // Пока идёт воспроизведение — не даём экрану гаснуть/затемняться.
                view.keepScreenOn = isPlaying
            },
        )

        playbackError?.let { message ->
            androidx.compose.material3.Text(
                text = message,
                color = Color.White,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            )
        }

        // В режиме «картинка в картинке» все контролы прячем — остаётся только видео.
        if (!isInPip) {
            // Сэмплим кадр только пока контролы на экране — иначе читали бы сюрфейс впустую.
            var controlsVisible by remember { mutableStateOf(true) }
            val ambient = rememberPlayerAmbient(
                surfaceProvider = { videoSurface },
                adaptive = !drmProtected,
                active = controlsVisible,
            )
            PlayerControlsOverlay(
                onControlsVisibleChange = { controlsVisible = it },
                player = exoPlayer,
                title = episodes.getOrNull(currentIndex)?.originalName.orEmpty(),
                ambient = ambient,
                onRotate = onRotate,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                position = position,
                buffered = buffered,
                duration = duration,
                hasPrev = currentIndex > 0,
                hasNext = currentIndex < episodes.lastIndex,
                audioTracks = audioTracks,
                speed = speed,
                fit = fit,
                // Кнопку показываем только когда автоскип ВЫКЛ и мы внутри отрезка опенинга/эндинга.
                skipVisible = !autoSkipEnabled && activeSegment != null,
                onSkip = { activeSegment?.let { exoPlayer.seekTo(it.endMs) } },
                onBack = onBack,
                onEnterPip = onEnterPip,
                onSelectSpeed = { s -> speed = s; exoPlayer.playbackParameters = PlaybackParameters(s) },
                onSelectAudio = { opt -> exoPlayer.applyAudioOverride(opt) },
                onCycleFit = { fit = if (fit == VideoFit.ORIGINAL) VideoFit.CROP else VideoFit.ORIGINAL },
            )
        }
    }
}

/** Достаёт список аудиодорожек из [Tracks] для меню выбора. */
private fun Tracks.extractAudioOptions(): List<AudioTrackOption> {
    val out = ArrayList<AudioTrackOption>()
    groups.forEachIndexed { gIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (t in 0 until group.length) {
            if (!group.isTrackSupported(t)) continue
            val format = group.getTrackFormat(t)
            val label = format.label
                ?: format.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
                ?: "Track ${out.size + 1}"
            out += AudioTrackOption(
                id = "$gIndex:$t",
                label = label.replaceFirstChar { it.uppercase() },
                isSelected = group.isTrackSelected(t),
                groupIndex = gIndex,
                trackIndex = t,
            )
        }
    }
    return out
}

/** Применяет выбор аудиодорожки через override параметров трек-селектора. */
private fun ExoPlayer.applyAudioOverride(option: AudioTrackOption) {
    val group = currentTracks.groups.getOrNull(option.groupIndex) ?: return
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex)),
        )
        .build()
}
