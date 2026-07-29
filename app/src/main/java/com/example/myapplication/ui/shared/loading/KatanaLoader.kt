package com.example.myapplication.ui.shared.loading

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Катана, очерчивающая круг: индикатор загрузки, нарисованный целиком на `Canvas` белой линией.
 *
 * Форму движения задаёт [KatanaCycle], здесь — только геометрия и рисование.
 *
 * ## Как устроен риг
 *
 * Всё живёт на одной оси — луче из центра бокса под углом [Rig.BASE_ANGLE_DEG]. Координата `x`
 * отсчитывается вдоль луча, `y` — поперёк. Пути строятся в системе координат **самого клинка**
 * (начало — остриё, `+x` в сторону навершия) и ставятся на место сдвигом вдоль оси, поэтому
 * пересобирать их при движении не нужно.
 *
 * В покое катана вложена в ножны, и композиция отцентрована по боксу — это поза с референса.
 * Вытягивание уводит катану вдоль `+x` так, что остриё оказывается ровно на окружности радиуса
 * `R`; дальше катана крутится вокруг центра, а остриё чертит линию. Ножны остаются на месте:
 * их дело — покачнуться вслед клинку и принять его обратно.
 *
 * Устье ножен — со стороны цубы, как у настоящей катаны, поэтому клинок выходит рукоятью вперёд,
 * а остриё покидает ножны последним. Отсюда и приём с отсечением: клинок обрезается по устью
 * (`clipRect`), и «выползание» получается само собой, без отдельной анимации.
 */
@Composable
fun KatanaLoader(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "katanaLoader")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(KatanaCycle.CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "katanaPhase",
    )
    // Пути переживают кадры: меняются только сдвиг и поворот, а форма — нет.
    val shapes = remember { KatanaShapes() }

    Canvas(modifier = modifier) {
        // Фаза читается внутри draw-лямбды: анимация перерисовывает, а не рекомпозирует.
        drawKatanaFrame(phase.value, color, shapes)
    }
}

/** Пропорции рига в долях радиуса очерчиваемой окружности. */
private object Rig {
    const val BLADE = 0.78f
    const val BLADE_HALF = 0.026f

    /** Прогиб клинка: остриё остаётся на оси, изгибается тело. */
    const val BOW = 0.055f

    const val GUARD_LENGTH = 0.030f
    const val GUARD_HALF = 0.075f
    const val HANDLE = 0.290f
    const val HANDLE_HALF = 0.034f
    const val HANDLE_WRAPS = 3

    const val SCABBARD_HALF = 0.044f

    /** Насколько ножны длиннее клинка — закрытый конец не упирается в остриё. */
    const val SCABBARD_SLACK = 0.020f

    /** Во сколько раз качание ножен меньше радиуса. Ножны только вздрагивают, не уезжают. */
    const val SCABBARD_NUDGE = 0.9f

    const val STROKE = 0.028f

    /** Диагональ покоя — как на референсе: навершие вверх-вправо, остриё вниз-влево. */
    const val BASE_ANGLE_DEG = -38f

    val SWORD_LENGTH = BLADE + GUARD_LENGTH + HANDLE

    /** Сдвиг острия в покое: вся сложенная композиция отцентрована по пивоту. */
    val REST_TIP = -(SWORD_LENGTH - SCABBARD_SLACK) / 2f

    /** Устье ножен в координатах рига. */
    val MOUTH = REST_TIP + BLADE
}

private fun DrawScope.drawKatanaFrame(
    phase: Float,
    color: Color,
    shapes: KatanaShapes,
) {
    val side = min(size.width, size.height)
    if (side <= 0f) return

    // Радиус подобран так, чтобы вынутая катана (остриё на окружности, навершие снаружи)
    // укладывалась в бокс целиком.
    val radius = side * 0.215f
    shapes.updateFor(radius)

    val stroke = Stroke(
        width = (radius * Rig.STROKE).coerceAtLeast(1f),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    val pivot = Offset(size.width / 2f, size.height / 2f)

    val drawOut = KatanaCycle.drawOut(phase)
    val tipX = Rig.REST_TIP * radius + (radius - Rig.REST_TIP * radius) * drawOut
    val nudge = KatanaCycle.scabbardAxialLag(phase) * Rig.SCABBARD_NUDGE * radius
    val mouthX = Rig.MOUTH * radius + nudge

    // ——— Окружность: её оставляет остриё, поэтому конец дуги и есть остриё ———
    val sweep = KatanaCycle.arcSweep(phase)
    val arcAlpha = KatanaCycle.arcAlpha(phase)
    if (sweep > 0f && arcAlpha > 0f) {
        drawArc(
            color = color.copy(alpha = color.alpha * arcAlpha),
            startAngle = Rig.BASE_ANGLE_DEG,
            sweepAngle = 360f * sweep,
            useCenter = false,
            topLeft = Offset(pivot.x - radius, pivot.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = stroke,
        )
    }

    // ——— Ножны: стоят на месте, только вздрагивают вдоль оси ———
    withTransform({
        translate(pivot.x, pivot.y)
        rotate(Rig.BASE_ANGLE_DEG, Offset.Zero)
        translate(Rig.REST_TIP * radius + nudge, 0f)
    }) {
        drawPath(shapes.scabbard, color, style = stroke)
        drawPath(shapes.mouthBand, color, style = stroke)
    }

    // ——— Катана ———
    withTransform({
        translate(pivot.x, pivot.y)
        rotate(Rig.BASE_ANGLE_DEG + 360f * KatanaCycle.orbitTurns(phase), Offset.Zero)
        translate(tipX, 0f)
    }) {
        // Клинок видно только за устьем: пока катана вложена, ножны его закрывают, а на
        // вытягивании он выползает сам собой — сначала у цубы, остриё последним.
        val visibleFrom = mouthX - tipX
        val far = radius * 4f
        clipRect(left = visibleFrom, top = -far, right = far, bottom = far) {
            drawPath(shapes.blade, color, style = stroke)
        }
        drawPath(shapes.guard, color, style = stroke)
        drawPath(shapes.handle, color, style = stroke)
        drawPath(shapes.wraps, color, style = stroke)
    }
}

/**
 * Контуры катаны и ножен в координатах клинка: начало — остриё, `+x` к навершию.
 *
 * Пересобираются только при смене размера холста: внутри кадра ни одного `Path`.
 */
private class KatanaShapes {

    private var builtFor = Float.NaN

    val blade = Path()
    val guard = Path()
    val handle = Path()
    val wraps = Path()
    val scabbard = Path()
    val mouthBand = Path()

    fun updateFor(radius: Float) {
        if (radius == builtFor) return
        builtFor = radius
        blade.reset()
        guard.reset()
        handle.reset()
        wraps.reset()
        scabbard.reset()
        mouthBand.reset()
        build(radius)
    }

    private fun build(r: Float) {
        val bladeLength = Rig.BLADE * r
        val bow = Rig.BOW * r
        val bladeHalf = Rig.BLADE_HALF * r
        val scabbardHalf = Rig.SCABBARD_HALF * r
        val slack = Rig.SCABBARD_SLACK * r
        val guardLength = Rig.GUARD_LENGTH * r
        val guardHalf = Rig.GUARD_HALF * r
        val handleLength = Rig.HANDLE * r
        val handleHalf = Rig.HANDLE_HALF * r

        // Осевая линия клинка — парабола: остриё на оси, тело уходит в сторону обуха.
        fun axis(x: Float): Float {
            val t = x / bladeLength
            return -bow * t * t
        }

        fun slope(x: Float): Float = -2f * bow * x / (bladeLength * bladeLength)

        /**
         * Парабола точно представима квадратичной кривой Безье, поэтому контрольная точка
         * берётся из касательной, а не подгоняется на глаз.
         */
        fun controlX(from: Float, to: Float): Float = (from + to) / 2f

        fun controlY(from: Float, to: Float): Float = axis(from) + slope(from) * (to - from) / 2f

        // ---- Клинок: обух → скруглённое остриё → лезвие ----
        val noseAt = bladeHalf
        blade.moveTo(bladeLength, axis(bladeLength) - bladeHalf)
        blade.quadraticTo(
            controlX(bladeLength, noseAt),
            controlY(bladeLength, noseAt) - bladeHalf,
            noseAt,
            axis(noseAt) - bladeHalf,
        )
        // Контрольная точка в −noseAt выводит вершину кривой ровно в x = 0: остриё лежит на
        // окружности, а не рядом с ней.
        blade.quadraticTo(-noseAt, axis(0f), noseAt, axis(noseAt) + bladeHalf)
        blade.quadraticTo(
            controlX(noseAt, bladeLength),
            controlY(noseAt, bladeLength) + bladeHalf,
            bladeLength,
            axis(bladeLength) + bladeHalf,
        )
        blade.close()

        // ---- Цуба ----
        val guardAxis = axis(bladeLength)
        guard.addRoundRect(
            RoundRect(
                rect = Rect(
                    left = bladeLength,
                    top = guardAxis - guardHalf,
                    right = bladeLength + guardLength,
                    bottom = guardAxis + guardHalf,
                ),
                cornerRadius = CornerRadius(guardLength / 2f, guardLength / 2f),
            )
        )

        // ---- Рукоять с оплёткой ----
        val handleFrom = bladeLength + guardLength
        val handleTo = handleFrom + handleLength
        handle.addRoundRect(
            RoundRect(
                rect = Rect(
                    left = handleFrom,
                    top = guardAxis - handleHalf,
                    right = handleTo,
                    bottom = guardAxis + handleHalf,
                ),
                cornerRadius = CornerRadius(handleHalf, handleHalf),
            )
        )
        val wrapSpan = (handleTo - handleFrom) * 0.78f
        val wrapStart = handleFrom + (handleTo - handleFrom - wrapSpan) / 2f
        val wrapStep = wrapSpan / Rig.HANDLE_WRAPS
        repeat(Rig.HANDLE_WRAPS) { i ->
            val a = wrapStart + wrapStep * i
            val b = a + wrapStep
            wraps.moveTo(a, guardAxis - handleHalf)
            wraps.lineTo(b, guardAxis + handleHalf)
            wraps.moveTo(a, guardAxis + handleHalf)
            wraps.lineTo(b, guardAxis - handleHalf)
        }

        // ---- Ножны: тот же изгиб, что у клинка, — иначе он в них не ляжет ----
        val closedEnd = -slack
        val mouth = bladeLength
        val closedRound = closedEnd + scabbardHalf
        scabbard.moveTo(mouth, axis(mouth) - scabbardHalf)
        scabbard.quadraticTo(
            controlX(mouth, closedRound),
            controlY(mouth, closedRound) - scabbardHalf,
            closedRound,
            axis(closedRound) - scabbardHalf,
        )
        scabbard.quadraticTo(
            closedEnd - scabbardHalf,
            axis(closedEnd),
            closedRound,
            axis(closedRound) + scabbardHalf,
        )
        scabbard.quadraticTo(
            controlX(closedRound, mouth),
            controlY(closedRound, mouth) + scabbardHalf,
            mouth,
            axis(mouth) + scabbardHalf,
        )
        scabbard.close()

        val bandAt = mouth - scabbardHalf * 1.6f
        mouthBand.moveTo(bandAt, axis(bandAt) - scabbardHalf * 1.22f)
        mouthBand.lineTo(bandAt, axis(bandAt) + scabbardHalf * 1.22f)
    }
}

@Preview(widthDp = 320, heightDp = 320)
@Composable
private fun KatanaLoaderPreview() {
    Box(
        modifier = Modifier
            .size(320.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        KatanaLoader(modifier = Modifier.size(280.dp))
    }
}
