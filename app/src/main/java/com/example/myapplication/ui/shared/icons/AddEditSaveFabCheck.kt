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
 * Stroke check for Add/Edit glass FAB: `M4 12 L9 17 L20 6`, stroke-width 3, round caps
 * (matches user SVG viewBox 0 0 24 24). Tint comes from [androidx.compose.material3.Icon].
 */
val AddEditSaveFabCheck: ImageVector = ImageVector.Builder(
    name = "AddEditSaveFabCheck",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Transparent),
        stroke = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        strokeLineWidth = 3f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4f, 12f)
        lineTo(9f, 17f)
        lineTo(20f, 6f)
    }
}.build()
