package com.example.myapplication.ui.shared.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Heroicons-style stroke check: `m4.5 12.75 6 6 9-13.5` in a 24×24 viewBox.
 * [tint] is applied when drawn with [androidx.compose.material3.Icon].
 */
val HeroCheck: ImageVector = ImageVector.Builder(
    name = "HeroCheck",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Transparent),
        stroke = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.5f, 12.75f)
        lineTo(10.5f, 18.75f)
        lineTo(19.5f, 5.25f)
    }
}.build()
