package com.example.myapplication.ui.shared.components.rating

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.abs

/** Результат интерполяции — всё, что нужно Canvas-лицу для отрисовки одного кадра. */
data class FaceSpec(
    val nearestTier: RatingTier,
    val backgroundColor: Color,
    val eye: EyeShape,
    val eyebrow: EyebrowShape,
    val mouth: MouthShape,
    /** Кроссфейд «вау»-овала Masterpiece: 0 — обычный рот, 1 — только овал. */
    val openMouthAlpha: Float,
)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun lerpEye(a: EyeShape, b: EyeShape, t: Float) = EyeShape(
    roundness = lerp(a.roundness, b.roundness, t),
    widthFraction = lerp(a.widthFraction, b.widthFraction, t),
    heightFraction = lerp(a.heightFraction, b.heightFraction, t),
    interPupilScale = lerp(a.interPupilScale, b.interPupilScale, t),
)

private fun lerpEyebrow(a: EyebrowShape, b: EyebrowShape, t: Float) = EyebrowShape(
    rotationDeg = lerp(a.rotationDeg, b.rotationDeg, t),
    translateY = lerp(a.translateY, b.translateY, t),
    alpha = lerp(a.alpha, b.alpha, t),
    lightness = lerp(a.lightness, b.lightness, t),
)

private fun lerpMouth(a: MouthShape, b: MouthShape, t: Float) = MouthShape(
    cornerY = lerp(a.cornerY, b.cornerY, t),
    controlY = lerp(a.controlY, b.controlY, t),
    widthFraction = lerp(a.widthFraction, b.widthFraction, t),
    isOpenOval = if (t < 0.5f) a.isOpenOval else b.isOpenOval,
)

/**
 * t ∈ [0f, 10f]. Находит сегмент по anchorT соседних кейфреймов и лерпит внутри него.
 * Это и есть «100 выражений без 100 рисунков»: непрерывный t на 5 кейфреймах.
 *
 * Овал Masterpiece не морфится из кривой рта (слом контуров), а кроссфейдится
 * по alpha на последних ~15% верхнего сегмента.
 */
fun lerpFace(t: Float, frames: List<FaceKeyframe> = RATING_KEYFRAMES): FaceSpec {
    val clamped = t.coerceIn(frames.first().tier.anchorT, frames.last().tier.anchorT)
    val i = frames.indexOfLast { it.tier.anchorT <= clamped }.coerceIn(0, frames.size - 2)
    val a = frames[i]
    val b = frames[i + 1]
    val localT = (clamped - a.tier.anchorT) / (b.tier.anchorT - a.tier.anchorT)

    val openMouthAlpha = when {
        b.mouth.isOpenOval -> ((localT - 0.85f) / 0.15f).coerceIn(0f, 1f)
        a.mouth.isOpenOval -> (1f - localT / 0.15f).coerceIn(0f, 1f)
        else -> 0f
    }

    return FaceSpec(
        nearestTier = if (localT < 0.5f) a.tier else b.tier,
        backgroundColor = lerpOklab(a.backgroundColor, b.backgroundColor, localT),
        eye = lerpEye(a.eye, b.eye, localT),
        eyebrow = lerpEyebrow(a.eyebrow, b.eyebrow, localT),
        mouth = lerpMouth(a.mouth, b.mouth, localT),
        openMouthAlpha = openMouthAlpha,
    )
}

/** Только цвет фона для t — дёшево, без сборки всего FaceSpec (трек, хало). */
fun faceColorFor(t: Float, frames: List<FaceKeyframe> = RATING_KEYFRAMES): Color {
    val clamped = t.coerceIn(frames.first().tier.anchorT, frames.last().tier.anchorT)
    val i = frames.indexOfLast { it.tier.anchorT <= clamped }.coerceIn(0, frames.size - 2)
    val a = frames[i]
    val b = frames[i + 1]
    val localT = (clamped - a.tier.anchorT) / (b.tier.anchorT - a.tier.anchorT)
    return lerpOklab(a.backgroundColor, b.backgroundColor, localT)
}

/** «Чернильный» цвет черт лица и крупной подписи — глубоко затемнённый фон. */
fun faceInkColor(backgroundColor: Color): Color =
    lerpOklab(backgroundColor, Color.Black, 0.82f)

data class RatingLabels(
    val previous: String?,
    val current: String,
    val currentValueText: String,
    val next: String?,
)

/** Три подписи под треком: пред. якорь (тускло) / текущий + число (ярко) / след. (тускло). */
fun labelsFor(t: Float, ru: Boolean): RatingLabels {
    val tiers = RatingTier.entries
    val nearestIndex = tiers.indices.minBy { abs(tiers[it].anchorT - t) }
    return RatingLabels(
        previous = tiers.getOrNull(nearestIndex - 1)?.label(ru),
        current = tiers[nearestIndex].label(ru),
        currentValueText = String.format(Locale.US, "%.1f", t),
        next = tiers.getOrNull(nearestIndex + 1)?.label(ru),
    )
}
