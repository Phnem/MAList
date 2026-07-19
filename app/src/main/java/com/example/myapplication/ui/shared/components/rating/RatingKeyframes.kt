package com.example.myapplication.ui.shared.components.rating

import androidx.compose.ui.graphics.Color

/**
 * Пять опорных «якорей» модели лица на непрерывной шкале 0.0–10.0.
 * Это НЕ пять допустимых значений: между якорями лицо интерполируется
 * покадрово (см. [lerpFace]) — 101 позиция шкалы даёт 101 уникальное выражение.
 * Цвета фонов замерены пипеткой по референс-скриншотам.
 */
enum class RatingTier(val anchorT: Float, val labelEn: String, val labelRu: String) {
    BAD(0f, "Bad", "Плохо"),
    NOT_BAD(2.5f, "Not bad", "Так себе"),
    NORMAL(5f, "Normal", "Нормально"),
    GOOD(7.5f, "Good", "Хорошо"),
    MASTERPIECE(10f, "Masterpiece", "Шедевр");

    fun label(ru: Boolean): String = if (ru) labelRu else labelEn
}

/**
 * @param roundness 0f — приплюснутая капсула, 1f — идеальный круг
 * @param widthFraction ширина глаза как доля ширины лица
 * @param heightFraction высота глаза как доля ширины лица
 * @param interPupilScale множитель расстояния между глазами
 */
data class EyeShape(
    val roundness: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val interPupilScale: Float = 1f,
)

/**
 * @param rotationDeg наклон «домиком» (внутренний конец выше при > 0)
 * @param translateY сдвиг вверх (доля ширины лица, отрицательное = выше)
 * @param alpha 0f — бровь не рисуется
 * @param lightness 0f — тёмные «чернильные», 1f — светящиеся светлые (Masterpiece)
 */
data class EyebrowShape(
    val rotationDeg: Float,
    val translateY: Float,
    val alpha: Float,
    val lightness: Float = 0f,
)

/**
 * Рот — кубическая кривая по краевым/контрольным точкам.
 * @param cornerY подъём уголков (доля ширины лица; + = вниз/грусть)
 * @param controlY прогиб центра (+ = улыбка вверх, − = грусть вниз)
 * @param widthFraction ширина рта как доля ширины лица
 * @param isOpenOval спец-случай Masterpiece — открытый овал «вау»
 */
data class MouthShape(
    val cornerY: Float,
    val controlY: Float,
    val widthFraction: Float,
    val isOpenOval: Boolean = false,
)

data class FaceKeyframe(
    val tier: RatingTier,
    val backgroundColor: Color,
    val eye: EyeShape,
    val eyebrow: EyebrowShape,
    val mouth: MouthShape,
)

/** Стартовые приближения по скриншотам; подравнивались глазами при сборке. */
val RATING_KEYFRAMES: List<FaceKeyframe> = listOf(
    FaceKeyframe(
        RatingTier.BAD, Color(0xFFF94637),
        EyeShape(roundness = 1f, widthFraction = 0.17f, heightFraction = 0.17f),
        EyebrowShape(rotationDeg = 0f, translateY = 0f, alpha = 0f),
        MouthShape(cornerY = 0.10f, controlY = -0.15f, widthFraction = 0.34f),
    ),
    FaceKeyframe(
        RatingTier.NOT_BAD, Color(0xFFFCA01C),
        EyeShape(roundness = 0.15f, widthFraction = 0.24f, heightFraction = 0.075f),
        EyebrowShape(rotationDeg = 15f, translateY = -0.10f, alpha = 1f),
        MouthShape(cornerY = 0.03f, controlY = -0.05f, widthFraction = 0.28f),
    ),
    FaceKeyframe(
        RatingTier.NORMAL, Color(0xFFFCC61E),
        EyeShape(roundness = 0.10f, widthFraction = 0.21f, heightFraction = 0.06f),
        EyebrowShape(rotationDeg = 0f, translateY = 0f, alpha = 0f),
        MouthShape(cornerY = 0f, controlY = 0f, widthFraction = 0.24f),
    ),
    FaceKeyframe(
        RatingTier.GOOD, Color(0xFF97E03D),
        EyeShape(roundness = 1f, widthFraction = 0.185f, heightFraction = 0.185f, interPupilScale = 0.94f),
        EyebrowShape(rotationDeg = 0f, translateY = -0.10f, alpha = 0f),
        MouthShape(cornerY = -0.02f, controlY = 0.16f, widthFraction = 0.30f),
    ),
    FaceKeyframe(
        RatingTier.MASTERPIECE, Color(0xFF89E04D),
        EyeShape(roundness = 1f, widthFraction = 0.21f, heightFraction = 0.21f, interPupilScale = 0.90f),
        EyebrowShape(rotationDeg = -12f, translateY = -0.20f, alpha = 0.9f, lightness = 1f),
        MouthShape(cornerY = 0f, controlY = 0f, widthFraction = 0.17f, isOpenOval = true),
    ),
)
