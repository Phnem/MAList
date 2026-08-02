package com.example.myapplication.ui.shared.loading

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Compact loading indicator modeled after the supplied bubble-cluster reference.
 *
 * The animated phase is read in the draw pass, so the Canvas is invalidated without recomposing
 * the surrounding episode row or player controls on every frame.
 */
@Composable
fun BubbleClusterLoader(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "bubbleCluster")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = BubbleClusterMotion.CYCLE_MILLIS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bubbleClusterPhase",
    )

    Canvas(modifier = modifier.sizeIn(minWidth = 24.dp, minHeight = 18.dp)) {
        drawBubbleCluster(
            frame = BubbleClusterMotion.frame(phase.value),
            color = color,
        )
    }
}

private fun DrawScope.drawBubbleCluster(
    frame: BubbleClusterFrame,
    color: Color,
) {
    val unit = min(size.width / 5.65f, size.height / 3.15f)
    val center = Offset(size.width / 2f, size.height / 2f)
    val largeRadius = unit * 0.72f

    frame.bubbles.forEach { bubble ->
        val radiusX = largeRadius * bubble.scaleX
        val radiusY = largeRadius * bubble.scaleY
        val bubbleCenter = Offset(
            x = center.x + bubble.x * unit,
            y = center.y + bubble.y * unit,
        )

        drawOval(
            color = color.copy(alpha = 0.12f),
            topLeft = Offset(
                bubbleCenter.x - radiusX * 1.10f,
                bubbleCenter.y - radiusY * 1.10f,
            ),
            size = Size(radiusX * 2.20f, radiusY * 2.20f),
        )
        drawOval(
            color = color,
            topLeft = Offset(bubbleCenter.x - radiusX, bubbleCenter.y - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f),
        )
    }

    if (frame.satelliteAlpha > 0.01f) {
        val radius = unit * 0.20f
        drawCircle(
            color = color.copy(alpha = frame.satelliteAlpha),
            radius = radius,
            center = Offset(
                x = center.x + frame.satelliteX * unit,
                y = center.y + frame.satelliteY * unit,
            ),
        )
    }
}
