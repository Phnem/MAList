package com.example.myapplication.ui.shared.components.rating

import androidx.compose.ui.graphics.Color
import kotlin.math.cbrt

/**
 * Лерп цвета в OKLab (формулы Björn Ottosson) — переходы красный→жёлтый→зелёный
 * без «грязных» промежуточных тонов, которые даёт RGB/HSL-лерп.
 *
 * ВАЖНО (перф): вызывать только в draw-фазе (Canvas/drawWithCache/graphicsLayer),
 * не в теле @Composable — иначе recomposition на каждый кадр драга.
 */
internal fun lerpOklab(from: Color, to: Color, t: Float): Color {
    if (t <= 0f) return from
    if (t >= 1f) return to
    val a = srgbToOklab(from)
    val b = srgbToOklab(to)
    val l = a[0] + (b[0] - a[0]) * t
    val aa = a[1] + (b[1] - a[1]) * t
    val bb = a[2] + (b[2] - a[2]) * t
    val alpha = from.alpha + (to.alpha - from.alpha) * t
    return oklabToSrgb(l, aa, bb, alpha)
}

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f
    else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

private fun linearToSrgb(c: Float): Float =
    if (c <= 0.0031308f) c * 12.92f
    else (1.055f * Math.pow(c.toDouble(), 1.0 / 2.4).toFloat() - 0.055f)

private fun srgbToOklab(color: Color): FloatArray {
    val r = srgbToLinear(color.red)
    val g = srgbToLinear(color.green)
    val b = srgbToLinear(color.blue)

    val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
    val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
    val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b

    val l3 = cbrt(l)
    val m3 = cbrt(m)
    val s3 = cbrt(s)

    return floatArrayOf(
        0.2104542553f * l3 + 0.7936177850f * m3 - 0.0040720468f * s3,
        1.9779984951f * l3 - 2.4285922050f * m3 + 0.4505937099f * s3,
        0.0259040371f * l3 + 0.7827717662f * m3 - 0.8086757660f * s3,
    )
}

private fun oklabToSrgb(l: Float, a: Float, b: Float, alpha: Float): Color {
    val l3 = l + 0.3963377774f * a + 0.2158037573f * b
    val m3 = l - 0.1055613458f * a - 0.0638541728f * b
    val s3 = l - 0.0894841775f * a - 1.2914855480f * b

    val ll = l3 * l3 * l3
    val mm = m3 * m3 * m3
    val ss = s3 * s3 * s3

    val r = +4.0767416621f * ll - 3.3077115913f * mm + 0.2309699292f * ss
    val g = -1.2684380046f * ll + 2.6097574011f * mm - 0.3413193965f * ss
    val bb = -0.0041960863f * ll - 0.7034186147f * mm + 1.7076147010f * ss

    return Color(
        red = linearToSrgb(r).coerceIn(0f, 1f),
        green = linearToSrgb(g).coerceIn(0f, 1f),
        blue = linearToSrgb(bb).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f),
    )
}
