package com.example.myapplication.ui.shared.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Глубина bottom sheet без рамок/бликов/фасок: контраст даёт ЗАТЕМНЁННЫЙ ФОН за шторкой
 * (scrim, см. `OverlayThemeTokens.ScrimAlpha` и `scrimMaxAlpha` scaffold'а), а сама панель —
 * тёмно-синяя база + едва заметный вертикальный градиент (низ = база, верх чуть светлее) и одна
 * мягкая тень, отделяющая лист от фона. Никаких контуров/rim'ов/specular — их убрали намеренно.
 */

/** Контейнер шторки: мягкая тень → clip → база → едва заметный градиент. */
fun Modifier.iosSheetContainer(
    shape: Shape,
    isDark: Boolean,
    base: Color,
): Modifier = this
    .iosSheetTopShadow(shape, isDark)
    .clip(shape)
    .background(base)
    .iosSheetGlass(shape, base)

/** Одна мягкая тень над кромкой — «лист над затемнённым фоном». Нативный `shadowElevation`. */
fun Modifier.iosSheetTopShadow(shape: Shape, isDark: Boolean): Modifier {
    val c = Color.Black.copy(alpha = if (isDark) 0.16f else 0.10f)
    return this.shadow(
        elevation = if (isDark) 12.dp else 10.dp,
        shape = shape,
        clip = false,
        ambientColor = c,
        spotColor = c,
    )
}

/** Едва заметный вертикальный градиент внутри панели: верх чуть светлее базы, низ = база. */
fun Modifier.iosSheetGlass(
    shape: Shape,
    base: Color,
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = (outline as? Outline.Generic)?.path ?: Path().apply { addOutline(outline) }
    val gradient = Brush.verticalGradient(
        0f to lerp(base, Color.White, 0.035f),
        1f to base,
    )
    onDrawBehind { drawPath(path, brush = gradient) }
}
