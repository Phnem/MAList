package com.example.myapplication.localplayer.ui

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom

/**
 * Состояние зума видео и арбитраж жестов плеера.
 *
 * Разведение жестов сделано по схеме, которую используют MX Player / VLC / Just Player
 * (вводные пользователя, решение D-5 в MASTER_PLAN):
 *
 * 1. **Число пальцев — первичный фильтр.** Зум требует минимум двух указателей; пока опущен
 *    второй, однопальцевые свайпы блокируются флагом [multiTouchActive].
 * 2. **Состояние зума меняет трактовку драга.** При [isZoomed] однопальцевое перетаскивание —
 *    это панорама по увеличенному кадру, а не перемотка.
 * 3. **Debounce после pinch.** Пальцы отрываются не одновременно, и оставшийся указатель успевает
 *    проехать десяток пикселей уже как «драг» — поэтому [swipesBlocked] держит свайпы
 *    выключенными ещё [POST_PINCH_LOCK_MS] после завершения pinch'а.
 *
 * Ось драга ([dominantDragAxis]) и зональность экрана ([verticalZoneAt]) живут в `PlayerZoom.kt`
 * вместе с остальной чистой арифметикой — они понадобятся, когда в плеере появятся вертикальные
 * свайпы яркости и громкости. Сегодня однопальцевый жест здесь один (горизонтальная перемотка),
 * и залочивать ось не от чего.
 */
@Stable
class PlayerZoomState {

    var scale by mutableFloatStateOf(MIN_PLAYER_SCALE)
        private set

    var offsetX by mutableFloatStateOf(0f)
        private set

    var offsetY by mutableFloatStateOf(0f)
        private set

    /** Опущено больше одного указателя — однопальцевые обработчики обязаны молчать. */
    var multiTouchActive by mutableStateOf(false)
        internal set

    private var pinchEndedAt by mutableLongStateOf(0L)

    val isZoomed: Boolean get() = isPlayerZoomed(scale)

    /**
     * Должны ли однопальцевые свайпы (перемотка) сейчас игнорироваться.
     *
     * Читать **в момент события**, а не при настройке `pointerInput`: значение меняется по ходу
     * жеста, и проверка, сделанная один раз при подписке, ничего не защитит.
     */
    fun swipesBlocked(): Boolean =
        multiTouchActive || SystemClock.uptimeMillis() - pinchEndedAt < POST_PINCH_LOCK_MS

    internal fun applyTransform(zoomChange: Float, pan: Offset, container: IntSize) {
        scale = clampPlayerScale(scale * zoomChange)
        // Сдвиг пересчитывается под НОВЫЙ масштаб: если сначала подвинуть, а потом уменьшить,
        // кадр останется съехавшим за край.
        offsetX = clampPlayerOffset(offsetX + pan.x, scale, container.width.toFloat())
        offsetY = clampPlayerOffset(offsetY + pan.y, scale, container.height.toFloat())
    }

    /** Панорама одним пальцем по уже увеличенному кадру. */
    internal fun pan(dx: Float, dy: Float, container: IntSize) {
        offsetX = clampPlayerOffset(offsetX + dx, scale, container.width.toFloat())
        offsetY = clampPlayerOffset(offsetY + dy, scale, container.height.toFloat())
    }

    internal fun notePinchEnded() {
        pinchEndedAt = SystemClock.uptimeMillis()
    }

    /** Сброс при смене серии (FR-3a): новая серия не должна открываться увеличенной. */
    fun reset() {
        scale = MIN_PLAYER_SCALE
        offsetX = 0f
        offsetY = 0f
        multiTouchActive = false
    }
}

/**
 * Пинч-зум и панорама поверх видео.
 *
 * Событие потребляется только когда пальцев больше одного (заведомо pinch) либо кадр уже
 * увеличен (тогда таскание — панорама). Штатный `Modifier.transformable` здесь не годится по той
 * же причине, что и в ридере манги: он построен на `detectTransformGestures`, который считает
 * панорамой и одиночный палец и потребляет событие, — перемотка перестала бы работать вовсе.
 */
fun Modifier.playerZoomGestures(
    state: PlayerZoomState,
    enabled: Boolean = true,
): Modifier = if (!enabled) this else pointerInput(state, enabled) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var sawMultiTouch = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break

            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                sawMultiTouch = true
                state.multiTouchActive = true
            }

            // Один палец на неувеличенном кадре — не наше дело: пусть уходит в перемотку и тапы.
            if (pressed < 2 && !state.isZoomed) continue

            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            if (zoom != 1f || pan != Offset.Zero) {
                state.applyTransform(zoom, pan, size)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })

        state.multiTouchActive = false
        // Метку ставим только если pinch реально был: иначе обычный тап глушил бы перемотку
        // на POST_PINCH_LOCK_MS после каждого касания.
        if (sawMultiTouch) state.notePinchEnded()
    }
}
