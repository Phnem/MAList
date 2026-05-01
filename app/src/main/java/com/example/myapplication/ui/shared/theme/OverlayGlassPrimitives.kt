package com.example.myapplication.ui.shared.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Универсальные «glass»-примитивы для оверлеев.
 *
 * Цели:
 * - Стандартизировать визуал стеклянных панелей/плиток (синхронизация, sort, settings, stats).
 * - Не привязываться к Haze: панели часто рисуются поверх scrim, где блюр неэффективен.
 * - Дать как готовый wrapper [OverlayGlassPanel], так и набор Modifier-ов для гибкого применения.
 *
 * Эффекты:
 *  • [Modifier.glassFill]      — мягкий вертикальный «налив» (top brighter → bottom).
 *  • [Modifier.glassEdge]      — внутренний 1dp блик-обводка по диагонали (от 22% к 4%).
 *  • [Modifier.glassRim]       — внешняя приглушённая рамка-демаркация.
 *  • [OverlayGlassPanel]       — Box с применёнными в правильном порядке слоями.
 */

/**
 * Лёгкий вертикальный gradient-fill, накладываемый поверх основного panelBg.
 * На тёмной теме почти не видно (alpha 0.06 → 0), на светлой даёт ощущение полупрозрачного
 * стекла поверх surface (0.55 → 0).
 */
fun Modifier.glassFill(isDark: Boolean): Modifier = this.then(
    Modifier.background(
        Brush.verticalGradient(
            colors = if (isDark) listOf(
                Color.White.copy(alpha = OverlayThemeTokens.GlassFillTopAlphaDark),
                Color.White.copy(alpha = OverlayThemeTokens.GlassFillBottomAlphaDark)
            ) else listOf(
                Color.White.copy(alpha = OverlayThemeTokens.GlassFillTopAlphaLight),
                Color.White.copy(alpha = OverlayThemeTokens.GlassFillBottomAlphaLight)
            )
        )
    )
)

/**
 * Внутренняя обводка-блик 1dp с linear-градиентом (top-left bright → bottom-right transparent).
 * Это ключевой «edge» эффект, который придаёт ощущение объёма стекла — иначе панель выглядит
 * плоским surface'ом.
 */
fun Modifier.glassEdge(
    cornerRadius: Dp,
    isDark: Boolean,
    width: Dp = OverlayThemeTokens.GlassEdgeWidth
): Modifier = this.drawWithContent {
    drawContent()
    val strokeWidth = width.toPx()
    val cornerPx = (cornerRadius.toPx() - strokeWidth / 2f).coerceAtLeast(0f)
    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
    val rectSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val edgeBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = OverlayThemeTokens.GlassEdgeAlphaDark),
                Color.White.copy(alpha = (OverlayThemeTokens.GlassEdgeAlphaDark * 0.5f)),
                Color.White.copy(alpha = OverlayThemeTokens.GlassEdgeFadeAlphaDark)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = OverlayThemeTokens.GlassEdgeAlphaLight),
                Color.White.copy(alpha = (OverlayThemeTokens.GlassEdgeAlphaLight * 0.55f)),
                Color.White.copy(alpha = OverlayThemeTokens.GlassEdgeFadeAlphaLight)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
    }
    drawRoundRect(
        brush = edgeBrush,
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = CornerRadius(cornerPx),
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Внешний приглушённый «rim» — формирует контур панели, не конкурируя с edge-highlight.
 * Полезен для отделения панели от scrim на тёмной теме.
 */
fun Modifier.glassRim(
    cornerRadius: Dp,
    isDark: Boolean
): Modifier = this.drawWithContent {
    drawContent()
    val strokeWidth = 0.75.dp.toPx()
    val cornerPx = (cornerRadius.toPx() - strokeWidth / 2f).coerceAtLeast(0f)
    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
    val rectSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = CornerRadius(cornerPx),
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Готовый стеклянный контейнер: panelBg → fill → rim → edge highlight.
 * Подходит как для крупных панелей (sync, sort), так и для внутренних плиток.
 */
@Composable
fun OverlayGlassPanel(
    isDark: Boolean,
    panelBg: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(cornerRadius),
    edgeWidth: Dp = OverlayThemeTokens.GlassEdgeWidth,
    showRim: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val popupBg = panelBg.copy(alpha = panelBg.alpha * 0.75f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(popupBg)
            .glassFill(isDark)
            .let { if (showRim) it.glassRim(cornerRadius, isDark) else it }
            .glassEdge(cornerRadius, isDark, edgeWidth),
        content = content
    )
}
