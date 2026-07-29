package com.example.myapplication.localplayer.ui

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom

/**
 * Арбитраж жестов плеера: щипок двумя пальцами против однопальцевых свайпов и тапов.
 *
 * Свободного зума у плеера нет — щипок переводит кадр ровно в одно из двух положений [VideoFit],
 * тем же путём, что кнопка разворота в доке. Состояние здесь остаётся только для арбитража:
 *
 * 1. **Число пальцев — первичный фильтр.** Щипок требует минимум двух указателей; пока опущен
 *    второй, однопальцевые свайпы блокируются флагом [multiTouchActive].
 * 2. **Debounce после щипка.** Пальцы отрываются не одновременно, и оставшийся указатель успевает
 *    проехать десяток пикселей уже как «драг» — поэтому [swipesBlocked] держит свайпы выключенными
 *    ещё [POST_PINCH_LOCK_MS] после завершения жеста.
 *
 * Ось драга ([dominantDragAxis]) и зональность экрана ([verticalZoneAt]) живут в `PlayerZoom.kt`
 * вместе с остальной чистой арифметикой — они понадобятся, когда в плеере появятся вертикальные
 * свайпы яркости и громкости. Сегодня однопальцевый жест здесь один (горизонтальная перемотка),
 * и залочивать ось не от чего.
 */
@Stable
class PlayerPinchState {

    /** Опущено больше одного указателя — однопальцевые обработчики обязаны молчать. */
    var multiTouchActive by mutableStateOf(false)
        internal set

    private var pinchEndedAt by mutableLongStateOf(0L)

    /**
     * Должны ли однопальцевые свайпы (перемотка) сейчас игнорироваться.
     *
     * Читать **в момент события**, а не при настройке `pointerInput`: значение меняется по ходу
     * жеста, и проверка, сделанная один раз при подписке, ничего не защитит.
     */
    fun swipesBlocked(): Boolean =
        multiTouchActive || SystemClock.uptimeMillis() - pinchEndedAt < POST_PINCH_LOCK_MS

    internal fun notePinchEnded() {
        pinchEndedAt = SystemClock.uptimeMillis()
    }
}

/**
 * Щипок по видео: разведение пальцев разворачивает кадр по ширине, сведение возвращает исходный —
 * ровно то же, что делает кнопка в доке, и ровно в те же два положения.
 *
 * Событие потребляется только при двух и более указателях (заведомо щипок): одиночный палец обязан
 * уйти в перемотку и тапы. Штатный `Modifier.transformable` здесь не годится по той же причине,
 * что и в ридере манги: он построен на `detectTransformGestures`, который считает панорамой и
 * одиночный палец и потребляет событие, — перемотка перестала бы работать вовсе.
 *
 * @param onFit вызывается один раз на каждое пересечение порога; повторный щипок в ту же сторону
 *   просто просит то же положение, в котором кадр уже находится.
 */
fun Modifier.playerFitGestures(
    state: PlayerPinchState,
    enabled: Boolean = true,
    onFit: (VideoFit) -> Unit,
): Modifier = if (!enabled) this else pointerInput(state, enabled) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var sawMultiTouch = false
        // Накапливаем изменение за жест: одиночное событие приносит доли процента, и порог по нему
        // не взять. После срабатывания счётчик обнуляется — внутри того же щипка можно свести
        // пальцы обратно и вернуть кадр.
        var totalZoom = 1f
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break

            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                sawMultiTouch = true
                state.multiTouchActive = true
            } else {
                // Один палец — не наше дело: пусть уходит в перемотку и тапы.
                continue
            }

            val zoom = event.calculateZoom()
            if (zoom != 1f && zoom.isFinite() && zoom > 0f) {
                totalZoom *= zoom
                pinchFitTarget(totalZoom)?.let {
                    onFit(it)
                    totalZoom = 1f
                }
            }
            // Потребляем всё движение двумя пальцами, а не только сработавшее: иначе остаток
            // щипка доедет до detectHorizontalDragGestures и превратится в перемотку.
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } while (event.changes.any { it.pressed })

        state.multiTouchActive = false
        // Метку ставим только если щипок реально был: иначе обычный тап глушил бы перемотку
        // на POST_PINCH_LOCK_MS после каждого касания.
        if (sawMultiTouch) state.notePinchEnded()
    }
}
