package com.example.myapplication.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import com.example.myapplication.ui.shared.theme.BrandDeepRed
import com.example.myapplication.ui.shared.theme.BrandOrangeBright
import kotlin.random.Random

private val GradientNearBlack = Color(0xFF1A0505)

/**
 * Диагональная волна брендового градиента поверх чёрного.
 *
 * @param progress intro wipe 0→1 (TL→BR reveal)
 * @param exitProgress outro 0→1 (волна уходит дальше + fade в чёрный)
 */
@Composable
fun SplashWaveBackground(
    progress: Float,
    exitProgress: Float,
    modifier: Modifier = Modifier,
) {
    val grainPoints = remember {
        val rnd = Random(42)
        List(380) { Offset(rnd.nextFloat(), rnd.nextFloat()) }
    }

    val overallAlpha = (1f - exitProgress).coerceIn(0f, 1f)
    val wave = (progress + exitProgress * 0.85f).coerceAtLeast(0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (wave > 0.001f && overallAlpha > 0.001f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = overallAlpha
                    },
            ) {
                val w = size.width
                val h = size.height

                val gradient = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to BrandOrangeBright,
                        0.35f to BrandDeepRed,
                        0.72f to GradientNearBlack,
                        1.0f to Color.Black,
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h),
                )
                drawRect(brush = gradient)

                val grainAlpha = 0.08f * progress.coerceIn(0f, 1f)
                if (grainAlpha > 0.005f) {
                    val points = grainPoints.map { Offset(it.x * w, it.y * h) }
                    drawPoints(
                        points = points,
                        pointMode = PointMode.Points,
                        color = Color.White.copy(alpha = grainAlpha),
                        strokeWidth = 1.4f,
                        cap = StrokeCap.Round,
                    )
                }

                // Мягкая диагональная маска wipe
                val edge = 0.08f
                val whiteEnd = (wave - edge).coerceIn(0f, 1f)
                val clearStart = wave.coerceIn(0f, 1f)
                val mask = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.White,
                        whiteEnd to Color.White,
                        clearStart to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h),
                )
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
        }
    }
}

/** Alpha маски волны в точке (для синхронного reveal wordmark). */
fun splashWaveAlphaAt(
    xFrac: Float,
    yFrac: Float,
    progress: Float,
    exitProgress: Float,
    softEdgeFrac: Float = 0.08f,
): Float {
    val wave = progress + exitProgress * 0.85f
    val overall = (1f - exitProgress).coerceIn(0f, 1f)
    val tDiag = ((xFrac + yFrac) / 2f).coerceIn(0f, 1f)
    val local = ((wave - tDiag) / softEdgeFrac).coerceIn(0f, 1f)
    val a = local * local * (3f - 2f * local)
    return a * overall
}
