package com.example.myapplication.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material3.HorizontalDivider
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
import com.example.myapplication.ui.shared.theme.IosDesign
import com.example.myapplication.ui.shared.theme.MotionTokens
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.ui.shared.theme.SquircleShape
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val QualityMenuWidth = 228.dp
private val QualityRowHeight = 56.dp
private val QualityArrowWidth = 22.dp
private val QualityArrowHeight = 11.dp

/** Скругление контекстного меню — гайдбук §3.1 ([IosDesign.RadiusMd], squircle, не капсула). */
private val QualityMenuRadius = IosDesign.RadiusMd

/** Левый inset разделителя = padding строки (10) + иконка (38) + зазор (12): линия под текстом. */
private val QualityDividerInset = 60.dp

/**
 * Меню выбора качества, привязанное к якорю: одна полупрозрачная squircle-карточка с хвостиком,
 * строки внутри разделены линией (раньше была стопка отдельных непрозрачных пилюль).
 */
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
    val arrowPx = with(density) { QualityArrowHeight.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val anchorGapPx = with(density) { 8.dp.toPx() }
    // Строки живут в одной карточке вплотную друг к другу — зазоров между ними больше нет.
    val menuHeightPx = state.options.size * rowPx + arrowPx
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
    // Материал уровня 2 (гайдбук §3.2) — полупрозрачная alpha-поверхность, тот же токен, что у
    // остальных всплывающих панелей. Настоящий backdrop-blur (kyant `drawBackdrop`, как у
    // GlassMenuHeader/GlassIconButton) здесь недоступен: меню живёт в отдельном окне `Popup`, а
    // `layerBackdrop` пишет контент окна приложения — сэмплировать его из чужого окна нельзя.
    // Глубину даёт связка «elevated-поверхность + scrim», без рамок-«ободков» (см. §3.2).
    // В тёмной теме — непрозрачный #333333, а не level2Surface: тот даёт чёрный с alpha 0.75, что
    // на чистом чёрном фоне приложения неотличимо от фона. В светлой теме материал уровня 2
    // работает как задумано и остаётся.
    val menuSurface = if (isDark) OverlayThemeTokens.EpisodeMenuSurfaceDark else IosDesign.level2Surface(isDark)
    val menuShape = remember { SquircleShape(QualityMenuRadius) }
    // Разделитель в тон бывшему pillRing: чуть заметнее его, но заметно легче §3.3-сепаратора —
    // на полупрозрачной поверхности линия 0.16 читалась бы как жирная решётка.
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
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
                        QualityMenuArrow(true, arrowFraction, menuSurface)
                    }
                    // Одна цельная карточка вместо стопки пилюль: заливка и скругление живут на
                    // контейнере, строки внутри разделены только линией.
                    Column(
                        modifier = Modifier
                            .clip(menuShape)
                            .background(menuSurface),
                    ) {
                        state.options.forEachIndexed { index, option ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = QualityDividerInset),
                                    thickness = IosDesign.SeparatorThickness,
                                    color = dividerColor,
                                )
                            }
                            QualityRow(
                                option = option,
                                selected = selectedQuality == option.resolution,
                                enabled = !closeRequested,
                                onClick = { close(option.resolution) },
                            )
                        }
                    }
                    if (!menuBelow) {
                        QualityMenuArrow(false, arrowFraction, menuSurface)
                    }
                }
            }
        }
    }
}

/**
 * Строка меню качества. Собственного фона и обводки у неё нет — они принадлежат общей карточке,
 * иначе внутри полупрозрачной поверхности проступала бы вторая, более плотная плашка.
 * Выбранный пункт помечается только галочкой: оранжевая подложка/обводка на строке убраны.
 */
@Composable
private fun QualityRow(
    option: EpisodeQualityOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(QualityRowHeight)
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

/**
 * Хвостик-указатель на якорь. [color] обязан быть ТЕМ ЖЕ полупрозрачным материалом, что и карточка:
 * сплошная заливка выглядела бы приклеенным чужеродным треугольником, а alpha совпадает, потому что
 * треугольник и карточка не перекрываются (стоят встык в [Column]).
 */
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