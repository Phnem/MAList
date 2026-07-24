package com.example.myapplication.localplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.example.myapplication.ui.shared.theme.BrandOrangeBright
import com.example.myapplication.ui.shared.theme.MotionTokens
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.utils.performHaptic
import java.util.Locale

private enum class PlayerMenu { AUDIO, SPEED }

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val Capsule = RoundedCornerShape(percent = 50)

private fun isRuLocale() = Locale.getDefault().language == "ru"

/**
 * Кастомный скин контролов поверх видео: стеклянные Ambilight-доки (сверху/снизу) + белый бегунок
 * на оранжевой полосе. Автоскрытие 3.5 c, тап по видео — показать/скрыть с плавной анимацией,
 * замок — заблокировать жесты.
 */
@Composable
fun PlayerControlsOverlay(
    player: ExoPlayer,
    title: String,
    ambient: Color,
    isPlaying: Boolean,
    isBuffering: Boolean,
    position: Long,
    buffered: Long,
    duration: Long,
    hasPrev: Boolean,
    hasNext: Boolean,
    audioTracks: List<AudioTrackOption>,
    speed: Float,
    fit: VideoFit,
    skipVisible: Boolean,
    onSkip: () -> Unit,
    onRotate: () -> Unit,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectAudio: (AudioTrackOption) -> Unit,
    onCycleFit: () -> Unit,
) {
    val noise = rememberNoiseBrush()

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var menuKind by remember { mutableStateOf<PlayerMenu?>(null) }
    val menuState = remember { MutableTransitionState(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var unlockHintNonce by remember { mutableStateOf(0) }
    var showUnlockHint by remember { mutableStateOf(false) }

    // Жесты по видео: превью перемотки (свайп вбок) + индикатор дабл-тапа ±10с.
    var seekPreview by remember { mutableStateOf<SeekPreview?>(null) }
    var doubleTapNonce by remember { mutableStateOf(0) }
    var doubleTapForward by remember { mutableStateOf(true) }
    var showDoubleTap by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, isPlaying, menuKind, isScrubbing, locked, seekPreview) {
        if (controlsVisible && isPlaying && menuKind == null && !isScrubbing && !locked && seekPreview == null) {
            kotlinx.coroutines.delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(unlockHintNonce) {
        if (unlockHintNonce > 0) {
            showUnlockHint = true
            kotlinx.coroutines.delay(2500)
            showUnlockHint = false
        }
    }
    LaunchedEffect(menuState.currentState, menuState.isIdle) {
        if (!menuState.currentState && menuState.isIdle) menuKind = null
    }
    LaunchedEffect(doubleTapNonce) {
        if (doubleTapNonce > 0) {
            showDoubleTap = true
            kotlinx.coroutines.delay(550)
            showDoubleTap = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(locked, duration) {
                detectTapGestures(
                    onTap = {
                        if (locked) unlockHintNonce++
                        else controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (!locked && duration > 0) {
                            val forward = offset.x > size.width / 2f
                            val target = (player.currentPosition + if (forward) 10_000L else -10_000L)
                                .coerceIn(0L, duration)
                            player.seekTo(target)
                            doubleTapForward = forward
                            doubleTapNonce++
                        }
                    },
                )
            }
            .pointerInput(locked, duration) {
                if (locked) return@pointerInput
                var startPos = 0L
                var target = 0L
                detectHorizontalDragGestures(
                    onDragStart = {
                        startPos = player.currentPosition
                        target = startPos
                        seekPreview = SeekPreview(startPos, target, duration)
                    },
                    onDragEnd = {
                        if (duration > 0) player.seekTo(target)
                        seekPreview = null
                    },
                    onDragCancel = { seekPreview = null },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val msPerPx = 120_000f / size.width.coerceAtLeast(1)
                        target = (target + dragAmount * msPerPx).toLong().coerceIn(0L, duration)
                        seekPreview = SeekPreview(startPos, target, duration)
                    },
                )
            },
    ) {
        if (locked) {
            AnimatedVisibility(
                visible = showUnlockHint,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .padding(20.dp),
                enter = fadeIn(MotionTokens.standard()),
                exit = fadeOut(MotionTokens.dialogExit()),
            ) {
                DockIconButton(
                    icon = Icons.Rounded.Lock,
                    contentDescription = if (isRuLocale()) "Разблокировать" else "Unlock",
                    onClick = { locked = false; showUnlockHint = false; controlsVisible = true },
                    modifier = Modifier.ambientGlassPill(Capsule, ambient, noise),
                )
            }
            return@Box
        }

        // Превью перемотки свайпом + индикатор дабл-тапа — поверх, независимо от контролов.
        seekPreview?.let { SeekPreviewOverlay(it, Modifier.align(Alignment.Center)) }
        if (showDoubleTap) {
            DoubleTapIndicator(
                forward = doubleTapForward,
                modifier = Modifier
                    .align(if (doubleTapForward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 40.dp),
            )
        }

        // Кнопка «Пропустить» — весь опенинг, независимо от автоскрытия.
        if (skipVisible) {
            SkipButton(
                ambient = ambient,
                noise = noise,
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 104.dp),
            )
        }

        // ——— Затемняющие градиенты ———
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(MotionTokens.standard()),
            exit = fadeOut(MotionTokens.dialogExit()),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent)),
                )
                Box(
                    Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))),
                )
            }
        }

        // ——— Верхний ряд ———
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            enter = fadeIn(MotionTokens.standard()) + slideInVertically(MotionTokens.sheetOffset) { -it },
            exit = fadeOut(MotionTokens.dialogExit()) + slideOutVertically(MotionTokens.dialogExit()) { -it },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Левая пилюля в weight-контейнере → правая пилюля всегда прижата к углу.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Row(
                        modifier = Modifier.ambientGlassPill(Capsule, ambient, noise).padding(start = 6.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TransportIcon(Icons.AutoMirrored.Rounded.ArrowBack, 40.dp, 22.dp, onClick = onBack)
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = SnProFamily,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Row(
                    modifier = Modifier.ambientGlassPill(Capsule, ambient, noise).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DockIconButton(
                        icon = Icons.Rounded.MusicNote,
                        contentDescription = if (isRuLocale()) "Аудиодорожка" else "Audio track",
                        onClick = {
                            if (audioTracks.isNotEmpty()) { menuKind = PlayerMenu.AUDIO; menuState.targetState = true }
                        },
                    )
                    DockIconButton(
                        icon = Icons.Rounded.Speed,
                        contentDescription = if (isRuLocale()) "Скорость" else "Speed",
                        onClick = { menuKind = PlayerMenu.SPEED; menuState.targetState = true },
                    )
                }
            }
        }

        // ——— Центр: prev / play-pause / next ———
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(MotionTokens.standard()),
            exit = fadeOut(MotionTokens.dialogExit()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                TransportIcon(Icons.Rounded.SkipPrevious, 56.dp, 34.dp, enabled = hasPrev) { player.seekToPreviousMediaItem() }
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    if (isBuffering) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
                    } else {
                        TransportIcon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            72.dp, 46.dp,
                        ) { if (isPlaying) player.pause() else player.play() }
                    }
                }
                TransportIcon(Icons.Rounded.SkipNext, 56.dp, 34.dp, enabled = hasNext) { player.seekToNextMediaItem() }
            }
        }

        // ——— Низ: время + бегунок, справа док ———
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(MotionTokens.standard()) + slideInVertically(MotionTokens.sheetOffset) { it },
            exit = fadeOut(MotionTokens.dialogExit()) + slideOutVertically(MotionTokens.dialogExit()) { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Время, док и полоса — в одном контейнере 90% ширины (5%–95%), чтобы время было
                // строго над левым концом полосы, а док — над правым.
                Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${formatTime(position)} / ${formatTime(duration)}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = SnProFamily, fontWeight = FontWeight.Medium),
                            color = Color.White,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier.ambientGlassPill(Capsule, ambient, noise).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DockIconButton(Icons.Rounded.PictureInPictureAlt, contentDescription = "PiP", onClick = onEnterPip)
                            DockIconButton(
                                Icons.Rounded.ScreenRotation,
                                contentDescription = if (isRuLocale()) "Поворот" else "Rotate",
                                onClick = onRotate,
                            )
                            DockIconButton(
                                Icons.Rounded.Lock,
                                contentDescription = if (isRuLocale()) "Блокировка" else "Lock",
                                onClick = { locked = true; controlsVisible = false },
                            )
                            DockIconButton(
                                if (fit == VideoFit.CROP) Icons.Rounded.CropFree else Icons.Rounded.AspectRatio,
                                contentDescription = if (isRuLocale()) "Формат кадра" else "Aspect",
                                onClick = onCycleFit,
                            )
                        }
                    }

                    SeekBar(
                        modifier = Modifier.fillMaxWidth(),
                        position = position,
                        buffered = buffered,
                        duration = duration,
                        accent = BrandOrangeBright,
                        onScrubStart = { isScrubbing = true },
                        onScrubEnd = { fraction ->
                            isScrubbing = false
                            if (duration > 0) player.seekTo((fraction * duration).toLong())
                        },
                    )
                }
            }
        }

        val kind = menuKind
        if (kind != null && (menuState.targetState || menuState.currentState || !menuState.isIdle)) {
            OptionMenu(
                state = menuState,
                menu = kind,
                ambient = ambient,
                noise = noise,
                audioTracks = audioTracks,
                speed = speed,
                onSelectSpeed = { onSelectSpeed(it); menuState.targetState = false },
                onSelectAudio = { onSelectAudio(it); menuState.targetState = false },
                onDismiss = { menuState.targetState = false },
            )
        }
    }
}

/**
 * Оранжевая полоса + простой белый круглый бегунок (диаметр ~20% больше толщины полосы).
 * Никакого стекла на бегунке — по требованию.
 */
@Composable
private fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    accent: Color,
    onScrubStart: () -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val trackHeight = 12.dp
    val thumb = 15.dp // ≈ +25% к толщине полосы
    val thumbPx = with(density) { thumb.toPx() }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    // Держим бегунок на отпущенной позиции, пока плеер реально не доедет туда — иначе он на кадр
    // «отскакивает» к старому месту (лаг).
    var pendingSeek by remember { mutableStateOf<Float?>(null) }

    val fraction by remember(position, duration, isScrubbing, scrubFraction, pendingSeek) {
        derivedStateOf {
            when {
                isScrubbing -> scrubFraction
                pendingSeek != null -> pendingSeek!!
                duration > 0 -> (position.toFloat() / duration).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }
    LaunchedEffect(position, duration, pendingSeek) {
        val ps = pendingSeek ?: return@LaunchedEffect
        if (duration > 0 && kotlin.math.abs(position.toFloat() / duration - ps) < 0.02f) pendingSeek = null
    }
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .pointerInput(duration) {
                val padPx = thumbPx / 2f
                fun xToFraction(x: Float): Float {
                    val usable = (size.width - thumbPx).coerceAtLeast(1f)
                    return ((x - padPx) / usable).coerceIn(0f, 1f)
                }
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        down.consume()
                        isScrubbing = true
                        onScrubStart()
                        performHaptic(view, "light")
                        scrubFraction = xToFraction(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                            if (!change.pressed) {
                                change.consume()
                                performHaptic(view, "heavy")
                                isScrubbing = false
                                pendingSeek = scrubFraction
                                onScrubEnd(scrubFraction)
                                break
                            }
                            change.consume()
                            scrubFraction = xToFraction(change.position.x)
                        }
                    }
                }
            },
    ) {
        val travelPx = with(density) { (maxWidth - thumb).toPx() }

        // Полоса: хвост + буфер + оранжевый прогресс.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.Center)
                .clip(Capsule)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val r = h / 2f
                    val pad = thumbPx / 2f
                    val usable = (w - thumbPx).coerceAtLeast(1f)
                    drawRoundRect(color = Color.White.copy(alpha = 0.22f), cornerRadius = CornerRadius(r, r))
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.34f),
                        size = Size((pad + usable * bufferedFraction).coerceAtLeast(h), h),
                        cornerRadius = CornerRadius(r, r),
                    )
                    drawRoundRect(
                        color = accent,
                        size = Size((pad + usable * fraction).coerceAtLeast(h), h),
                        cornerRadius = CornerRadius(r, r),
                    )
                },
        )

        // Белый круглый бегунок.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = travelPx * fraction
                    val s = if (isScrubbing) 1.18f else 1f
                    scaleX = s
                    scaleY = s
                }
                .size(thumb)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun SkipButton(ambient: Color, noise: androidx.compose.ui.graphics.ShaderBrush, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .ambientGlassPill(Capsule, ambient, noise)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isRuLocale()) "Пропустить" else "Skip",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = SnProFamily, fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * Меню аудио/скорости в стиле стопки ресурсов на карточках ([WebLinkPopupMenu]): столбик стеклянных
 * пилюль, «вылетающих» из верхнего-правого угла (нота/спидометр) пружиной [MotionTokens.menuPop],
 * поверх затемняющего скрима. Закрытие — обратное «всасывание».
 */
@Composable
private fun OptionMenu(
    state: MutableTransitionState<Boolean>,
    menu: PlayerMenu,
    ambient: Color,
    noise: ShaderBrush,
    audioTracks: List<AudioTrackOption>,
    speed: Float,
    onSelectSpeed: (Float) -> Unit,
    onSelectAudio: (AudioTrackOption) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val origin = TransformOrigin(1f, 0f) // «вытекает» из верхнего-правого дока
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = state,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(MotionTokens.menuPop()),
            exit = fadeOut(MotionTokens.sheetDismissForced()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            )
        }
        AnimatedVisibility(
            visibleState = state,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 66.dp, end = 14.dp),
            enter = scaleIn(MotionTokens.menuPop(), initialScale = 0.5f, transformOrigin = origin) + fadeIn(MotionTokens.menuPop()),
            exit = scaleOut(MotionTokens.sheetDismissForced(), targetScale = 0.6f, transformOrigin = origin) + fadeOut(MotionTokens.sheetDismissForced()),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (menu) {
                    PlayerMenu.AUDIO -> audioTracks.forEach { opt ->
                        MenuPill(opt.label, opt.isSelected, ambient, noise) { onSelectAudio(opt) }
                    }
                    PlayerMenu.SPEED -> SPEED_OPTIONS.forEach { s ->
                        MenuPill(
                            label = if (s == 1f) (if (isRuLocale()) "Обычная (1×)" else "Normal (1×)") else "${trimSpeed(s)}×",
                            selected = s == speed,
                            ambient = ambient,
                            noise = noise,
                        ) { onSelectSpeed(s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPill(label: String, selected: Boolean, ambient: Color, noise: ShaderBrush, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .ambientGlassPill(Capsule, ambient, noise)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(BrandOrangeBright))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = SnProFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) BrandOrangeBright else Color.White,
        )
    }
}

/** Состояние превью перемотки свайпом: база, цель, длительность (для строки времени). */
private data class SeekPreview(val base: Long, val target: Long, val duration: Long)

@Composable
private fun SeekPreviewOverlay(preview: SeekPreview, modifier: Modifier = Modifier) {
    val delta = preview.target - preview.base
    val sign = if (delta >= 0) "+" else "−"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatTime(preview.target),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SnProFamily, fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$sign${formatTime(kotlin.math.abs(delta))}",
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = SnProFamily, fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun DoubleTapIndicator(forward: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(Capsule)
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (forward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (forward) "+10" else "−10",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = SnProFamily, fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

/** Иконка транспорта без фона (белая). Без окраски по нажатию. */
@Composable
private fun TransportIcon(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(iconSize))
    }
}

/** Иконка-кнопка внутри дока. Всегда белая — без окраски при нажатии/активации. */
@Composable
private fun DockIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun trimSpeed(s: Float): String =
    if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0').trimEnd('.')
