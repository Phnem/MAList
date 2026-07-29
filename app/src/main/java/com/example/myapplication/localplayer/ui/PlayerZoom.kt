package com.example.myapplication.localplayer.ui

import kotlin.math.abs

/**
 * Арифметика зума плеера — без Compose и без Android, чтобы её можно было покрыть обычным тестом.
 *
 * Сам жест на JVM не проверить, а вот «кадр не уезжает за край» и «масштаб не уходит в
 * бесконечность» проверить обязательно: ошибка здесь оставляет пользователя с картинкой,
 * утащенной в пустоту, без способа вернуть её на место.
 */

const val MIN_PLAYER_SCALE = 1f
const val MAX_PLAYER_SCALE = 4f

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

/** Ось, вдоль которой залочен текущий однопальцевый драг. */
enum class DragAxis { Undecided, Horizontal, Vertical }

/** Что регулирует вертикальный свайп — зависит от половины экрана, где он начат. */
enum class VerticalZone { Brightness, Volume }

fun clampPlayerScale(requested: Float): Float =
    requested.coerceIn(MIN_PLAYER_SCALE, MAX_PLAYER_SCALE)

/**
 * Ограничивает сдвиг по одной оси половиной «свеса» — той части кадра, которая при текущем
 * масштабе вышла за пределы контейнера.
 *
 * На масштабе 1 свеса нет, поэтому и сдвиг нулевой: любое смещение открыло бы пустоту у края.
 */
fun clampPlayerOffset(offset: Float, scale: Float, containerSize: Float): Float {
    if (containerSize <= 0f) return 0f
    val overhang = (containerSize * scale - containerSize) / 2f
    if (overhang <= 0f) return 0f
    return offset.coerceIn(-overhang, overhang)
}

/**
 * Считается ли кадр увеличенным.
 *
 * С допуском, а не сравнением с 1f: масштаб набегает произведением множителей жеста, и точное
 * единичное значение после pinch'а туда-обратно практически недостижимо — кадр залипал бы в
 * режиме перетаскивания на масштабе вроде 1.0000001, отбирая у пользователя перемотку.
 */
fun isPlayerZoomed(scale: Float): Boolean = scale > MIN_PLAYER_SCALE + 0.01f

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

/** Левая половина экрана — яркость, правая — громкость. */
fun verticalZoneAt(x: Float, containerWidth: Float): VerticalZone =
    if (containerWidth > 0f && x > containerWidth / 2f) VerticalZone.Volume else VerticalZone.Brightness
