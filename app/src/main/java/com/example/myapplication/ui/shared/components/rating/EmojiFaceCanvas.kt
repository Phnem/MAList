package com.example.myapplication.ui.shared.components.rating

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/**
 * Процедурное лицо смайлика. Все параметры — из [lerpFace], вычисленного
 * ВНУТРИ draw-лямбды ([tProvider] читается только в draw-фазе — ноль
 * recomposition во время драга).
 *
 * Геометрия в долях стороны лица S: глаза на 0.38S, рот на 0.70S,
 * единый примитив глаза drawRoundRect (капсула↔круг через cornerRadius).
 */
@Composable
fun EmojiFaceCanvas(
    tProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val spec = lerpFace(tProvider())
        drawFace(spec)
    }
}

private fun DrawScope.drawFace(spec: FaceSpec) {
    val s = min(size.width, size.height)
    if (s <= 0f) return
    val cx = size.width / 2f
    val cy = size.height / 2f
    val top = cy - s / 2f
    val ink = faceInkColor(spec.backgroundColor)

    // ---- Глаза ----
    val eye = spec.eye
    val eyeCy = top + 0.40f * s
    val halfGap = 0.23f * s * eye.interPupilScale
    val eyeW = eye.widthFraction * s
    val eyeH = eye.heightFraction * s
    val corner = (min(eyeW, eyeH) / 2f) * (0.85f + 0.15f * eye.roundness)
    listOf(cx - halfGap, cx + halfGap).forEach { ex ->
        drawRoundRect(
            color = ink,
            topLeft = Offset(ex - eyeW / 2f, eyeCy - eyeH / 2f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(corner, corner),
        )
    }

    // ---- Брови ----
    val brow = spec.eyebrow
    if (brow.alpha > 0.01f) {
        val browW = eyeW * 1.05f
        val browStroke = 0.045f * s
        val browCy = eyeCy - eyeH / 2f - 0.11f * s + brow.translateY * s * 0.5f
        val browColor = lerpOklab(ink, Color(0xFFF2FFE6), brow.lightness)
            .copy(alpha = brow.alpha)
        // Свечение светлых бровей Masterpiece — широкий полупрозрачный дубль-штрих.
        val glowColor = Color.White.copy(alpha = 0.35f * brow.lightness * brow.alpha)

        listOf(-1f, 1f).forEach { side ->
            val bx = cx + side * halfGap
            // «Домиком»: внутренние концы выше. Знак поворота зеркален по сторонам.
            rotate(degrees = -side * brow.rotationDeg, pivot = Offset(bx, browCy)) {
                val path = Path().apply {
                    moveTo(bx - browW / 2f, browCy + 0.02f * s)
                    quadraticTo(bx, browCy - 0.055f * s, bx + browW / 2f, browCy + 0.02f * s)
                }
                if (glowColor.alpha > 0.01f) {
                    drawPath(path, glowColor, style = Stroke(browStroke * 2.4f, cap = StrokeCap.Round))
                }
                drawPath(path, browColor, style = Stroke(browStroke, cap = StrokeCap.Round))
            }
        }
    }

    // ---- Рот ----
    val mouth = spec.mouth
    val mouthCy = top + 0.72f * s
    val mouthW = mouth.widthFraction * s
    val curveAlpha = 1f - spec.openMouthAlpha
    if (curveAlpha > 0.01f) {
        val yCorner = mouthCy + mouth.cornerY * s
        val yControl = mouthCy + mouth.controlY * s * 2f
        val path = Path().apply {
            moveTo(cx - mouthW / 2f, yCorner)
            quadraticTo(cx, yControl, cx + mouthW / 2f, yCorner)
        }
        drawPath(
            path,
            ink.copy(alpha = curveAlpha),
            style = Stroke(0.05f * s, cap = StrokeCap.Round),
        )
    }
    if (spec.openMouthAlpha > 0.01f) {
        // Открытый овал «вау» (Masterpiece) — кроссфейд, не морфинг кривой.
        val ovalW = mouthW.coerceAtLeast(0.16f * s)
        val ovalH = ovalW * 1.35f
        drawOval(
            color = ink.copy(alpha = spec.openMouthAlpha),
            topLeft = Offset(cx - ovalW / 2f, mouthCy - ovalH / 2f + 0.02f * s),
            size = Size(ovalW, ovalH),
        )
    }
}
