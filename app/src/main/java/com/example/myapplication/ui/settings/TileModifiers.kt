package com.example.myapplication.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import com.example.myapplication.ui.shared.theme.glassEdge
import com.example.myapplication.ui.shared.theme.glassFill

fun Modifier.tileGlow(accentColor: Color, glowAlpha: Float = 0.15f): Modifier {
    return this.drawWithCache {
        if (glowAlpha <= 0f) {
            onDrawBehind { }
        } else {
            val brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = glowAlpha), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = size.maxDimension * 0.8f
            )
            onDrawBehind { drawRect(brush = brush) }
        }
    }
}

/** Radial glow от левого края (оверлеи «по жанрам» и т.п.). */
fun Modifier.tileGlowLeading(accentColor: Color, glowAlpha: Float = 0.15f): Modifier {
    return this.drawWithCache {
        if (glowAlpha <= 0f) {
            onDrawBehind { }
        } else {
            val brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = glowAlpha), Color.Transparent),
                center = Offset(0f, size.height * 0.42f),
                radius = size.maxDimension * 0.92f
            )
            onDrawBehind { drawRect(brush = brush) }
        }
    }
}

@Composable
fun settingsTileIconBoxBg(): Color =
    if (isAppInDarkTheme()) OverlayThemeTokens.TileIconBgDark
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)

@Composable
fun BaseTile(
    tile: SettingsTile,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isAppInDarkTheme()
    val tileBg =
        if (isDark) OverlayThemeTokens.TileBackgroundDark
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val cornerRadius = 24.dp
    val shape = RoundedCornerShape(cornerRadius)
    val accentRim = tile.accentColor.copy(alpha = if (isDark) 0.45f else 0.6f)
    val accentGlow = tile.accentColor.copy(
        alpha = if (isDark) OverlayThemeTokens.TileGlowAlphaDark else OverlayThemeTokens.TileGlowAlphaLight
    )
    val glowRadiusFactor = 0.85f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (tile.span == 1) 1f else 2.1f)
            .clip(shape)
            .background(tileBg)
            .drawWithCache {
                val brush = Brush.radialGradient(
                    colors = listOf(accentGlow, Color.Transparent),
                    center = Offset(size.width, 0f),
                    radius = size.maxDimension * glowRadiusFactor
                )
                onDrawBehind { drawRect(brush = brush) }
            }
            .glassFill(isDark)
            .glassEdge(cornerRadius, isDark)
            .border(1.dp, accentRim, shape)
            .padding(12.dp),
        content = content
    )
}
