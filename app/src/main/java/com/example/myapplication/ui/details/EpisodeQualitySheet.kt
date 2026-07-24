package com.example.myapplication.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.myapplication.ui.shared.theme.MotionTokens
import com.example.myapplication.ui.shared.theme.SnProFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val QualityMenuWidth = 228.dp
private val QualityRowHeight = 56.dp
private val QualityMenuGap = 8.dp
private val QualityArrowWidth = 22.dp
private val QualityArrowHeight = 11.dp

/** Anchored capsule popover matching the source selector on the home screen. */
@Composable
fun EpisodeQualityPopover(
    state: EpisodeQualityPickerState,
    selectedQuality: Int?,
    anchor: Rect,
    ru: Boolean,
    isDark: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val appView = LocalView.current
    val visibleState = remember(state) {
        MutableTransitionState(false).apply { targetState = true }
    }
    var pendingSelection by remember(state) { mutableStateOf<Int?>(null) }
    var closeRequested by remember(state) { mutableStateOf(false) }

    fun close(selection: Int? = null) {
        if (closeRequested) return
        pendingSelection = selection
        closeRequested = true
        visibleState.targetState = false
    }

    LaunchedEffect(closeRequested) {
        if (!closeRequested) return@LaunchedEffect
        delay(200)
        pendingSelection?.let(onSelect) ?: onDismiss()
    }

    val menuWidthPx = with(density) { QualityMenuWidth.toPx() }
    val rowPx = with(density) { QualityRowHeight.toPx() }
    val gapPx = with(density) { QualityMenuGap.toPx() }
    val arrowPx = with(density) { QualityArrowHeight.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val anchorGapPx = with(density) { 8.dp.toPx() }
    val menuHeightPx = state.options.size * rowPx +
        (state.options.size - 1).coerceAtLeast(0) * gapPx + arrowPx
    val anchorCenterX = anchor.center.x
    val menuX = (anchorCenterX - menuWidthPx / 2f).coerceIn(
        marginPx,
        (appView.width - menuWidthPx - marginPx).coerceAtLeast(marginPx),
    )
    val menuBelow = anchor.bottom + anchorGapPx + menuHeightPx + marginPx <= appView.height
    val menuY = if (menuBelow) {
        anchor.bottom + anchorGapPx
    } else {
        (anchor.top - anchorGapPx - menuHeightPx).coerceAtLeast(marginPx)
    }
    val arrowFraction = ((anchorCenterX - menuX) / menuWidthPx).coerceIn(0.08f, 0.92f)
    val transformOrigin = TransformOrigin(arrowFraction, if (menuBelow) 0f else 1f)
    val pillColor = if (isDark) Color(0xFF232329) else Color(0xFFF4F4F6)
    val pillRing = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val scrimColor = Color.Black.copy(alpha = if (isDark) 0.52f else 0.32f)

    val fullWindowProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset.Zero
        }
    }

    Popup(
        popupPositionProvider = fullWindowProvider,
        onDismissRequest = { close() },
        properties = PopupProperties(focusable = true),
    ) {
        val popupView = LocalView.current
        var windowDelta by remember { mutableStateOf<IntOffset?>(null) }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val app = IntArray(2).also(appView::getLocationOnScreen)
                    val popup = IntArray(2).also(popupView::getLocationOnScreen)
                    windowDelta = IntOffset(app[0] - popup[0], app[1] - popup[1])
                },
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(MotionTokens.menuPop()),
                exit = fadeOut(MotionTokens.sheetDismissForced()),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { close() },
                )
            }

            val delta = windowDelta ?: return@Box
            AnimatedVisibility(
                visibleState = visibleState,
                modifier = Modifier.offset {
                    IntOffset(menuX.roundToInt() + delta.x, menuY.roundToInt() + delta.y)
                },
                enter = scaleIn(
                    animationSpec = MotionTokens.menuPop(),
                    initialScale = 0.85f,
                    transformOrigin = transformOrigin,
                ) + fadeIn(MotionTokens.menuPop()),
                exit = scaleOut(
                    animationSpec = MotionTokens.sheetDismissForced(),
                    targetScale = 0.85f,
                    transformOrigin = transformOrigin,
                ) + fadeOut(MotionTokens.sheetDismissForced()),
            ) {
                Column(modifier = Modifier.width(QualityMenuWidth)) {
                    if (menuBelow) {
                        QualityMenuArrow(true, arrowFraction, pillColor)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(QualityMenuGap)) {
                        state.options.forEach { option ->
                            QualityPill(
                                option = option,
                                selected = selectedQuality == option.resolution,
                                enabled = !closeRequested,
                                isDark = isDark,
                                onClick = { close(option.resolution) },
                            )
                        }
                    }
                    if (!menuBelow) {
                        QualityMenuArrow(false, arrowFraction, pillColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityPill(
    option: EpisodeQualityOption,
    selected: Boolean,
    enabled: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.13f)
        isDark -> Color(0xFF232329)
        else -> Color(0xFFF4F4F6)
    }
    val ring = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(QualityRowHeight)
            .clip(shape)
            .background(background)
            .border(1.dp, ring, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Hd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = option.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = SnProFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun QualityMenuArrow(pointUp: Boolean, fraction: Float, color: Color) {
    Canvas(
        modifier = Modifier
            .offset(x = QualityMenuWidth * fraction - QualityArrowWidth / 2)
            .size(QualityArrowWidth, QualityArrowHeight),
    ) {
        val path = Path().apply {
            if (pointUp) {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path, color, style = Fill)
    }
}