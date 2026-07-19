package com.example.myapplication.ui.shared.components.pulltorefresh

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.shared.theme.BrandOrange
import com.example.myapplication.ui.shared.theme.BrandOrangeBright
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Чистый брендовый pull-to-refresh индикатор. Живёт ЗА контентом; уезжающий вниз контент открывает
 * его в зазоре сверху. Во время пулла кольцо «заполняется» по прогрессу жеста (+ ведущая точка),
 * при загрузке — вращающаяся комета-дуга с градиентным хвостом. Возврат — плавное затухание/усадка.
 *
 * Позиция/масштаб/альфа — строго в draw-фазе (graphicsLayer/Canvas), без рекомпозиции на кадр.
 *
 * @param revealMax высота зазора (в dp) при полном пулле — в нём вертикально центрируется спиннер.
 */
@Composable
fun BoxScope.PullRefreshIndicator(
    controller: PullRefreshController,
    revealMax: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(revealMax)
            .graphicsLayer {
                // Плавное появление ещё до порога срабатывания.
                alpha = (controller.revealFraction() / 0.28f).coerceIn(0f, 1f)
            },
    ) {
        val reveal = controller.revealFraction()
        val committed = controller.appear.value
        val progress = controller.pullState.distanceFraction.coerceIn(0f, 1f)

        val strokeW = 3.dp.toPx()
        val baseR = 12.dp.toPx()
        // Лёгкий «поп» при появлении.
        val ringR = baseR * (0.65f + 0.35f * max(progress, committed))
        // Спиннер вертикально по центру раскрытого зазора.
        val center = Offset(size.width / 2f, reveal * size.height / 2f)
        val arcTopLeft = Offset(center.x - ringR, center.y - ringR)
        val arcSize = Size(ringR * 2f, ringR * 2f)

        // Фоновая дорожка кольца.
        drawCircle(
            color = BrandOrange.copy(alpha = 0.16f),
            radius = ringR,
            center = center,
            style = Stroke(width = strokeW),
        )

        if (committed > 0.02f) {
            // ---- Загрузка: вращающаяся комета с градиентным хвостом ----
            rotate(controller.spin.value, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.55f to BrandOrange.copy(alpha = 0.15f * committed),
                        1.0f to BrandOrangeBright.copy(alpha = committed),
                        center = center,
                    ),
                    startAngle = 15f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
        } else {
            // ---- Пулл: дуга-прогресс от 12 часов + ведущая точка ----
            val sweep = progress * 300f
            drawArc(
                color = BrandOrange,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
            if (progress > 0.02f) {
                val a = (-90f + sweep) * (PI / 180f).toFloat()
                drawCircle(
                    color = BrandOrangeBright,
                    radius = strokeW * 0.9f,
                    center = Offset(center.x + ringR * cos(a), center.y + ringR * sin(a)),
                )
            }
        }
    }
}
