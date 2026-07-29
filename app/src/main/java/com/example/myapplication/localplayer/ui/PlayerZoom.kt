package com.example.myapplication.localplayer.ui

import kotlin.math.abs

/**
 * Арифметика жестов плеера — без Compose и без Android, чтобы её можно было покрыть обычным тестом.
 *
 * Свободного масштаба у плеера нет: у кадра ровно два положения ([VideoFit]), и щипок — второй вход
 * в то же состояние, что кнопка разворота в доке. Поэтому здесь живёт не «во сколько раз увеличить»,
 * а «в какую сторону пользователь развёл пальцы и достаточно ли решительно».
 */

/**
 * Во сколько раз должно измениться расстояние между пальцами, чтобы кадр перестроился.
 *
 * Порог, а не любое изменение: два пальца почти никогда не опускаются на экран строго одновременно
 * и на неизменное расстояние, и без запаса кадр перещёлкивался бы от простого касания двумя
 * точками.
 */
const val PINCH_FIT_RATIO = 1.15f

/** Порог, после которого ось драга считается выбранной (см. [dominantDragAxis]). */
const val DRAG_AXIS_THRESHOLD_DP = 12f

/**
 * Задержка перед тем, как однопальцевые жесты снова начнут слушаться после того, как палец с
 * pinch'а отпущен.
 *
 * Без неё завершение pinch'а почти всегда триггерит seek: пальцы отрываются не одновременно, и
 * оставшийся указатель успевает проехать десяток пикселей уже как «драг».
 */
const val POST_PINCH_LOCK_MS = 180L

/**
 * Скорость на время удержания пальца ([isSpeedHoldZone]). Абсолютная, а не множитель к текущей:
 * пользователь просил «переходит на 2x», и от выбранной в меню 1.5× жест должен давать те же 2×,
 * что от единицы.
 */
const val HOLD_SPEED = 2f

/** Ось, вдоль которой залочен текущий однопальцевый драг. */
enum class DragAxis { Undecided, Horizontal, Vertical }

/** Что регулирует вертикальный свайп — зависит от половины экрана, где он начат. */
enum class VerticalZone { Brightness, Volume }

/**
 * В какое положение просит перевести кадр щипок, накопивший изменение [totalZoom].
 *
 * `null` = ни в какое: движение не дотянуло до порога. Вырожденные значения (ноль, бесконечность,
 * NaN) `calculateZoom()` отдаёт, когда указатели сошлись в одну точку, — они тоже ничего не меняют.
 */
fun pinchFitTarget(totalZoom: Float): VideoFit? = when {
    !totalZoom.isFinite() || totalZoom <= 0f -> null
    totalZoom >= PINCH_FIT_RATIO -> VideoFit.CROP
    totalZoom <= 1f / PINCH_FIT_RATIO -> VideoFit.ORIGINAL
    else -> null
}

/**
 * Выбирает доминирующую ось драга — но только после того, как палец прошёл [thresholdPx].
 *
 * До порога возвращает [DragAxis.Undecided]: на первых пикселях дрожание руки назначило бы ось
 * случайно, и палец, начавший перемотку, соскальзывал бы в яркость.
 */
fun dominantDragAxis(dx: Float, dy: Float, thresholdPx: Float): DragAxis {
    if (abs(dx) < thresholdPx && abs(dy) < thresholdPx) return DragAxis.Undecided
    return if (abs(dx) >= abs(dy)) DragAxis.Horizontal else DragAxis.Vertical
}

/**
 * Та ли это половина экрана, где удержание ускоряет воспроизведение.
 *
 * Правая — та же, что у перемотки вперёд дабл-тапом: жест «пропустить скучное» логично держать с
 * той стороны, куда и так двигают время. Нулевая ширина (первый кадр) зоной не считается — иначе
 * касание по левому краю сработало бы как правое.
 */
fun isSpeedHoldZone(x: Float, containerWidth: Float): Boolean =
    containerWidth > 0f && x > containerWidth / 2f

/** Левая половина экрана — яркость, правая — громкость. */
fun verticalZoneAt(x: Float, containerWidth: Float): VerticalZone =
    if (containerWidth > 0f && x > containerWidth / 2f) VerticalZone.Volume else VerticalZone.Brightness
