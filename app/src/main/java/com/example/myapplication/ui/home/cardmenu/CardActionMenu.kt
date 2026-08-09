package com.example.myapplication.ui.home.cardmenu

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.ui.home.AnimeCardBody
import com.example.myapplication.ui.home.AnimeCardState
import com.example.myapplication.ui.shared.theme.MotionTokens
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import kotlin.math.roundToInt

// ==========================================
// Контекстное меню карточки (долгое удержание).
//
// Карточка поднимается над затемнённым списком, под ней всплывает ряд действий. Оверлей
// МОДАЛЕН: скрим съедает и тапы, и перетаскивания, поэтому под ним не скроллится список и не
// листаются страницы рабочей области. Док гасит и отключает уже сама рабочая область — он ей
// сосед, а не потомок.
//
// Блюр фона делает НЕ этот компонент: им занимается существующий `homeScrollBlur` на контенте
// главной. Свой слой блюра здесь добавил бы ещё один узел над `layerBackdrop` и уронил стекло.
// ==========================================

private val CARD_SCALE = 1.04f
private val ACTION_SIZE = 54.dp
private val ACTION_SPACING = 14.dp
private val CARD_TO_ACTIONS_GAP = 16.dp

/** Что именно выделено: данные карточки и её место на экране в момент удержания. */
@Immutable
data class CardMenuTarget(
    val state: AnimeCardState,
    val isFavorite: Boolean,
    /** Границы карточки в координатах корня окна. */
    val boundsInRoot: Rect,
)

@Composable
fun CardActionMenuOverlay(
    target: CardMenuTarget,
    /** Нижняя занятая зона (док + нав-бар): ряд кнопок не должен под неё уезжать. */
    bottomInset: Dp,
    topInset: Dp,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isAppInDarkTheme()
    val density = LocalDensity.current

    // Собственное начало координат оверлея: карточку мы поймали в координатах КОРНЯ, а
    // раскладываем внутри этого Box — без вычета его смещения копия уедет.
    var overlayOrigin by remember { mutableStateOf<Offset?>(null) }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(target.state.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = MotionTokens.menuPop())
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft },
    ) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val actionsRowHeightPx = with(density) { ACTION_SIZE.toPx() }
        val gapPx = with(density) { CARD_TO_ACTIONS_GAP.toPx() }
        val topInsetPx = with(density) { topInset.toPx() }
        val bottomInsetPx = with(density) { bottomInset.toPx() }

        val origin = overlayOrigin
        val cardLocalTop = if (origin != null) target.boundsInRoot.top - origin.y else 0f
        val cardLocalLeft = if (origin != null) target.boundsInRoot.left - origin.x else 0f
        // Высота с учётом увеличения: ряд кнопок отсчитывается от РЕАЛЬНОГО низа поднятой карточки.
        val scaledHeight = target.boundsInRoot.height * CARD_SCALE
        val grow = (scaledHeight - target.boundsInRoot.height) / 2f

        val lift = cardLiftFor(
            cardTop = cardLocalTop - grow,
            cardHeight = scaledHeight,
            menuHeight = actionsRowHeightPx,
            gap = gapPx,
            viewportHeight = viewportHeightPx,
            topInset = topInsetPx,
            bottomInset = bottomInsetPx,
        )

        // —— Скрим: гасит фон и запирает жесты —— //
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * progress.value))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                // Перетаскивания съедаем отдельно: clickable их не перехватывает, и без этого
                // палец по скриму листал бы страницы рабочей области.
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> change.consume() }
                }
        )

        if (origin != null) {
            val cardWidth = with(density) { target.boundsInRoot.width.toDp() }
            val cardHeight = with(density) { target.boundsInRoot.height.toDp() }

            // —— Копия карточки —— //
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            cardLocalLeft.roundToInt(),
                            (cardLocalTop - lift * progress.value).roundToInt(),
                        )
                    }
                    .width(cardWidth)
                    .height(cardHeight)
                    .graphicsLayer {
                        val s = 1f + (CARD_SCALE - 1f) * progress.value
                        scaleX = s
                        scaleY = s
                    }
                    // Тап по самой карточке — тоже выход из режима: отдельного действия у неё нет.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            ) {
                AnimeCardBody(state = target.state)
            }

            // —— Ряд действий —— //
            val actionsTop = cardLocalTop + grow + scaledHeight - lift * progress.value + gapPx
            Row(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            cardLocalLeft.roundToInt(),
                            actionsTop.roundToInt(),
                        )
                    }
                    .width(cardWidth)
                    .graphicsLayer {
                        alpha = progress.value
                        // Кнопки «выезжают» из-под карточки, а не появляются на месте.
                        translationY = (1f - progress.value) * -gapPx
                        val s = 0.85f + 0.15f * progress.value
                        scaleX = s
                        scaleY = s
                    },
                horizontalArrangement = Arrangement.spacedBy(ACTION_SPACING, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardActionButton(
                    icon = if (target.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    tint = OverlayThemeTokens.FavoriteGold,
                    isDark = isDark,
                    onClick = onToggleFavorite,
                )
                CardActionButton(
                    icon = Icons.Rounded.Delete,
                    tint = Color(0xFFE5382B),
                    isDark = isDark,
                    onClick = onDelete,
                )
                CardActionButton(
                    icon = Icons.Filled.Edit,
                    tint = if (isDark) Color.White else Color(0xFF1C1C1E),
                    isDark = isDark,
                    onClick = onEdit,
                )
                CardActionButton(
                    icon = Icons.Outlined.Info,
                    tint = if (isDark) Color.White else Color(0xFF1C1C1E),
                    isDark = isDark,
                    onClick = onDetails,
                )
            }
        }
    }
}

@Composable
private fun CardActionButton(
    icon: ImageVector,
    tint: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ACTION_SIZE)
            .clip(CircleShape)
            .background(if (isDark) Color(0xFF2C2C2E) else Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
