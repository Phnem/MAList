package com.example.myapplication.ui.home.updates

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.ui.shared.theme.MotionTokens
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign

// ==========================================
// EpisodeUpdateStack — iOS-style top-of-screen пуш-стопка обновлений серий.
// Одна карточка — одиночный баннер; несколько — стопка, где старые выглядывают
// снизу (уже и ниже), как сложенные уведомления на iPhone.
//
// Фон карточки НЕПРОЗРАЧНЫЙ: верхняя полностью перекрывает стопку, поэтому текст
// задних карточек не просвечивает (была «каша» на полупрозрачном стекле).
//
// Жесты: свайп верхней влево/вправо = отказ (улетает по горизонтали);
// кнопка ✓ — улетает вверх (accept); кнопка ✕ — вправо (decline).
// Callback вызывается ПОСЛЕ анимации; до обновления БД карточка прячется через
// departedIds, чтобы не мигнула обратно.
// ==========================================

private const val VISIBLE_BACK_CARDS = 2
private const val BACK_SCALE_STEP = 0.05f
private val CARD_HEIGHT = 76.dp
private val BACK_PEEK = 9.dp
private val CARD_SHADOW = 14.dp
/** Доля ширины карточки, после которой отпущенный свайп засчитывается как отказ. */
private const val SWIPE_DISMISS_FRACTION = 0.32f

@Composable
fun EpisodeUpdateStack(
    updates: List<AnimeUpdate>,
    coverPathFor: (animeId: String) -> String?,
    onAccept: (AnimeUpdate) -> Unit,
    onDecline: (AnimeUpdate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDark = isAppInDarkTheme()

    // iOS-палитра: непрозрачные карточки, мягкая тень, брендовый оранжевый акцент.
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White
    val onCard = if (isDark) Color.White else Color(0xFF1C1C1E)
    val hairline = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val accent = Color(0xFFE85002)
    val onAccent = Color.White
    val declineBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEFEFF0)
    val declineTint = Color(0xFFE5382B)

    // Улетевшие, но ещё не удалённые из БД карточки: скрываем до обновления Flow.
    val departedIds: SnapshotStateList<String> = remember { mutableStateListOf() }
    LaunchedEffect(updates) {
        departedIds.retainAll { id -> updates.any { it.animeId == id } }
    }
    val visible = updates.filter { it.animeId !in departedIds }
    if (visible.isEmpty()) return

    val top = visible.first()
    val offsetX = remember(top.animeId) { Animatable(0f) }
    val offsetY = remember(top.animeId) { Animatable(0f) }
    var departing by remember(top.animeId) { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val peekPx = with(density) { BACK_PEEK.toPx() }
        val dismissThresholdPx = widthPx * SWIPE_DISMISS_FRACTION
        val flyOutXPx = widthPx * 1.2f
        val flyOutYPx = with(density) { 480.dp.toPx() }
        val velocityThresholdPx = with(density) {
            MotionTokens.DismissVelocityThresholdDpPerSec.dp.toPx()
        }

        fun flyOutHorizontally(direction: Float, update: AnimeUpdate) {
            if (departing) return
            departing = true
            scope.launch {
                offsetX.animateTo(direction * flyOutXPx, animationSpec = tween(240))
                departedIds += update.animeId
                onDecline(update)
            }
        }

        fun flyOutUp(update: AnimeUpdate) {
            if (departing) return
            departing = true
            scope.launch {
                offsetY.animateTo(-flyOutYPx, animationSpec = tween(280))
                departedIds += update.animeId
                onAccept(update)
            }
        }

        val backCount = (visible.size - 1).coerceAtMost(VISIBLE_BACK_CARDS)
        val stackHeight = CARD_HEIGHT + BACK_PEEK * backCount

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight),
            contentAlignment = Alignment.TopCenter
        ) {
            // Прогресс ухода топа: задние карточки подтягиваются на уровень выше.
            val progress = (maxOf(abs(offsetX.value), abs(offsetY.value)) / dismissThresholdPx)
                .coerceIn(0f, 1f)

            // +2: топ, видимые пики и одна скрытая карточка, всплывающая при уходе топа.
            visible.take(VISIBLE_BACK_CARDS + 2).withIndex().reversed().forEach { (index, update) ->
                val isTop = index == 0

                val translateY: Float
                val scale: Float
                val alpha: Float
                if (isTop) {
                    translateY = offsetY.value
                    scale = 1f
                    val horizontalFade = abs(offsetX.value) / (widthPx * 0.9f)
                    val verticalFade = -offsetY.value / flyOutYPx
                    alpha = (1f - maxOf(horizontalFade, verticalFade)).coerceIn(0f, 1f)
                } else {
                    val currentY = peekPx * index
                    val nextY = peekPx * (index - 1)
                    val currentScale = 1f - BACK_SCALE_STEP * index
                    val nextScale = 1f - BACK_SCALE_STEP * (index - 1)
                    translateY = currentY + (nextY - currentY) * progress
                    scale = currentScale + (nextScale - currentScale) * progress
                    alpha = if (index > VISIBLE_BACK_CARDS) progress else 1f
                }

                val gestureModifier = if (isTop && !departing) {
                    Modifier.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            scope.launch { offsetX.snapTo(offsetX.value + delta) }
                        },
                        onDragStopped = { velocity ->
                            val shouldDismiss = abs(offsetX.value) > dismissThresholdPx ||
                                abs(velocity) > velocityThresholdPx
                            if (shouldDismiss) {
                                val direction = if (offsetX.value != 0f) sign(offsetX.value) else sign(velocity)
                                flyOutHorizontally(if (direction == 0f) 1f else direction, update)
                            } else {
                                scope.launch {
                                    offsetX.animateTo(0f, animationSpec = MotionTokens.menuPop())
                                }
                            }
                        }
                    )
                } else Modifier

                key(update.animeId) {
                    Box(
                        modifier = gestureModifier
                            .zIndex((100 - index).toFloat())
                            .graphicsLayer {
                                translationX = if (isTop) offsetX.value else 0f
                                translationY = translateY
                                scaleX = scale
                                scaleY = scale
                                rotationZ = if (isTop) {
                                    ((offsetX.value / widthPx) * 10f).coerceIn(-7f, 7f)
                                } else 0f
                                this.alpha = alpha
                            }
                            .fillMaxWidth()
                    ) {
                        EpisodeUpdateCard(
                            update = update,
                            // Memo: resolve пути идёт в БД, а стопка рекомпозируется каждый кадр драга.
                            coverPath = remember(update.animeId) { coverPathFor(update.animeId) },
                            isDark = isDark,
                            cardColor = cardColor,
                            onCard = onCard,
                            hairline = hairline,
                            accent = accent,
                            onAccent = onAccent,
                            declineBg = declineBg,
                            declineTint = declineTint,
                            dimmed = !isTop,
                            actionsEnabled = isTop && !departing,
                            onAccept = { flyOutUp(update) },
                            onDecline = { flyOutHorizontally(1f, update) },
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Карточка: [обложка] [название + счётчик серий] [✓] [✕]
// ==========================================

@Composable
private fun EpisodeUpdateCard(
    update: AnimeUpdate,
    coverPath: String?,
    isDark: Boolean,
    cardColor: Color,
    onCard: Color,
    hairline: Color,
    accent: Color,
    onAccent: Color,
    declineBg: Color,
    declineTint: Color,
    dimmed: Boolean,
    actionsEnabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val tileShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .shadow(CARD_SHADOW, tileShape, clip = false)
            .clip(tileShape)
            .background(cardColor)
            .border(1.dp, hairline, tileShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // —— Миниатюра обложки —— //
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFEFEFF0)),
                contentAlignment = Alignment.Center
            ) {
                if (coverPath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(coverPath))
                            .size(Size(104, 104))
                            .crossfade(true)
                            .build(),
                        contentDescription = update.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            // —— Название (fade на переполнении) + счётчик серий —— //
            Column(modifier = Modifier.weight(1f)) {
                FadingEndText(
                    text = update.title,
                    color = onCard,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "%dep. — %dEp.",
                        update.currentEpisodes,
                        update.newEpisodes
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
            Spacer(Modifier.width(8.dp))

            // —— Кнопки-иконки: Принять / Отказать —— //
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleIconButton(
                    background = accent,
                    enabled = actionsEnabled,
                    onClick = onAccept,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                CircleIconButton(
                    background = declineBg,
                    enabled = actionsEnabled,
                    onClick = onDecline,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = declineTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Лёгкое затемнение задних карточек стопки для ощущения глубины.
        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
        }
    }
}

@Composable
private fun CircleIconButton(
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Однострочный текст: если не влезает — конец растворяется горизонтальным градиентом
 * (без многоточия), как subject в iOS-пуше. Fade включается только при реальном
 * переполнении, иначе короткий заголовок терял бы последние буквы.
 */
@Composable
private fun FadingEndText(
    text: String,
    color: Color,
) {
    var overflowed by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { overflowed = it.hasVisualOverflow },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (overflowed) compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                if (overflowed) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.72f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    )
}
