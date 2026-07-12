package com.example.myapplication.ui.shared.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size

/**
 * iOS-подобный «squircle» / continuous-corner shape (гайдбук §3.1).
 *
 * Обычный [androidx.compose.foundation.shape.RoundedCornerShape] режет угол дугой окружности
 * (кривизна скачком меняется на стыке прямая↔дуга). iOS использует непрерывную кривизну
 * (superellipse), из-за чего углы выглядят «мягче» при том же визуальном радиусе.
 *
 * Здесь угол аппроксимируется кубической Безье: точки касания отодвинуты от вершины на
 * `radius·(1+smoothing)`, а контрольные ручки подобраны так, чтобы кривизна плавно нарастала
 * (нет резкого стыка). Это не пиксель-в-пиксель Apple-squircle, но визуально читается как
 * continuous corner и не тянет внешнюю зависимость.
 *
 * @param radius целевой визуальный радиус угла
 * @param smoothing 0..1, доля «растекания» кривой на прямой участок; iOS ≈ 0.6 (гайдбук §3.1).
 */
class SquircleShape(
    private val radius: Dp,
    private val smoothing: Float = 0.6f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { radius.toPx() }
        return Outline.Generic(squirclePath(size, r, r, r, r, smoothing))
    }
}

/**
 * Squircle с индивидуальными радиусами углов (для асимметричного морфинга капсула→лист, §7).
 */
class SquircleCornerShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val smoothing: Float = 0.6f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val ts = with(density) { topStart.toPx() }
        val te = with(density) { topEnd.toPx() }
        val be = with(density) { bottomEnd.toPx() }
        val bs = with(density) { bottomStart.toPx() }
        // LTR предполагается (в приложении RTL не поддержан) — start=left, end=right.
        return Outline.Generic(squirclePath(size, ts, te, be, bs, smoothing))
    }
}

private fun squirclePath(
    size: Size,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    smoothing: Float,
): Path {
    val w = size.width
    val h = size.height
    val maxR = minOf(w, h) / 2f
    val tl = topLeft.coerceIn(0f, maxR)
    val tr = topRight.coerceIn(0f, maxR)
    val br = bottomRight.coerceIn(0f, maxR)
    val bl = bottomLeft.coerceIn(0f, maxR)

    // Точка касания на расстоянии r от вершины — прямой участок ребра сохраняется (иначе на
    // коротких элементах углы схлопываются в остриё). «Squircle»-полноту даёт контрольная точка k,
    // а НЕ вынос точки касания.
    fun reach(r: Float) = r.coerceIn(0f, maxR)
    // k=0.5523 — точная четверть круга; больше → угол «полнее»/непрерывнее (superellipse-ощущение).
    val k = (0.55191502449f + smoothing * 0.16f).coerceAtMost(0.92f)

    val path = Path()
    val tlReach = reach(tl)
    val trReach = reach(tr)
    val brReach = reach(br)
    val blReach = reach(bl)

    // Старт на верхнем ребре, сразу после левого-верхнего угла.
    path.moveTo(tlReach, 0f)

    // Верхнее ребро → правый-верхний угол.
    path.lineTo(w - trReach, 0f)
    if (tr > 0f) {
        path.cubicTo(
            w - trReach + trReach * k, 0f,
            w, trReach - trReach * k,
            w, trReach,
        )
    } else {
        path.lineTo(w, 0f)
    }

    // Правое ребро → правый-нижний угол.
    path.lineTo(w, h - brReach)
    if (br > 0f) {
        path.cubicTo(
            w, h - brReach + brReach * k,
            w - brReach + brReach * k, h,
            w - brReach, h,
        )
    } else {
        path.lineTo(w, h)
    }

    // Нижнее ребро → левый-нижний угол.
    path.lineTo(blReach, h)
    if (bl > 0f) {
        path.cubicTo(
            blReach - blReach * k, h,
            0f, h - blReach + blReach * k,
            0f, h - blReach,
        )
    } else {
        path.lineTo(0f, h)
    }

    // Левое ребро → левый-верхний угол.
    path.lineTo(0f, tlReach)
    if (tl > 0f) {
        path.cubicTo(
            0f, tlReach - tlReach * k,
            tlReach - tlReach * k, 0f,
            tlReach, 0f,
        )
    } else {
        path.lineTo(0f, 0f)
    }

    path.close()
    return path
}

/**
 * Капсула (pill) с непрерывной кривизной — источник геометрического матчинга кнопка→лист (§7).
 * По сути squircle с radius = height/2, но smoothing чуть ниже, чтобы торцы не «пузырились».
 */
fun CapsuleShape(smoothing: Float = 0.35f): Shape = GenericShape { size, _ ->
    addPath(squirclePath(size, size.height / 2f, size.height / 2f, size.height / 2f, size.height / 2f, smoothing))
}
