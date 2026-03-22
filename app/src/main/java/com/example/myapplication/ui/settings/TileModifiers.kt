package com.example.myapplication.ui.settings

import androidx.compose.foundation.background
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
fun BaseTile(
    tile: SettingsTile,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (tile.span == 1) 1f else 2.1f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .tileGlow(tile.accentColor)
            .padding(12.dp),
        content = content
    )
}
