package com.example.myapplication.localplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ограничители зума плеера.
 *
 * Чистая арифметика вынесена из жестового кода: сам жест на JVM не проверить, а вот «кадр не
 * уезжает за край» и «масштаб не уходит в бесконечность» проверить обязательно — иначе
 * пользователь утаскивает картинку в пустоту и не может вернуть.
 */
class PlayerZoomTest {

    @Test
    fun scale_stays_within_bounds() {
        assertEquals(MIN_PLAYER_SCALE, clampPlayerScale(0.1f), 0.001f)
        assertEquals(MAX_PLAYER_SCALE, clampPlayerScale(99f), 0.001f)
        assertEquals(2f, clampPlayerScale(2f), 0.001f)
    }

    @Test
    fun unzoomed_frame_cannot_be_dragged_at_all() {
        // На масштабе 1 двигать нечего: любой сдвиг открыл бы чёрную полосу у края.
        assertEquals(0f, clampPlayerOffset(500f, scale = 1f, containerSize = 1080f), 0.001f)
        assertEquals(0f, clampPlayerOffset(-500f, scale = 1f, containerSize = 1080f), 0.001f)
    }

    @Test
    fun offset_is_capped_by_the_overhang() {
        // При масштабе 2 за края уходит ровно половина ширины — значит и сдвиг не больше половины.
        val max = clampPlayerOffset(Float.MAX_VALUE, scale = 2f, containerSize = 1000f)
        assertEquals(500f, max, 0.001f)
        assertEquals(-500f, clampPlayerOffset(-Float.MAX_VALUE, scale = 2f, containerSize = 1000f), 0.001f)
    }

    @Test
    fun offset_within_bounds_is_untouched() {
        assertEquals(120f, clampPlayerOffset(120f, scale = 2f, containerSize = 1000f), 0.001f)
    }

    @Test
    fun zero_container_does_not_produce_nan() {
        // Первый кадр может прийти с нулевым размером — деления и NaN тут быть не должно.
        assertEquals(0f, clampPlayerOffset(50f, scale = 3f, containerSize = 0f), 0.001f)
    }

    @Test
    fun zoomed_flag_has_a_tolerance() {
        // Точное сравнение с 1f после серии умножений почти никогда не срабатывает,
        // и кадр «залипал» бы в режиме pan на масштабе 1.0000001.
        assertFalse(isPlayerZoomed(1f))
        assertFalse(isPlayerZoomed(1.001f))
        assertTrue(isPlayerZoomed(1.2f))
    }

    @Test
    fun dominant_axis_locks_to_the_larger_component() {
        assertEquals(DragAxis.Horizontal, dominantDragAxis(dx = 30f, dy = 5f, thresholdPx = 16f))
        assertEquals(DragAxis.Vertical, dominantDragAxis(dx = 4f, dy = 40f, thresholdPx = 16f))
    }

    @Test
    fun axis_stays_undecided_below_the_threshold() {
        // Пока палец не прошёл порог, ось выбирать рано: на первых пикселях дрожание руки
        // назначило бы её случайно.
        assertEquals(DragAxis.Undecided, dominantDragAxis(dx = 3f, dy = 2f, thresholdPx = 16f))
    }

    @Test
    fun vertical_zone_splits_at_the_middle() {
        assertEquals(VerticalZone.Brightness, verticalZoneAt(x = 100f, containerWidth = 1000f))
        assertEquals(VerticalZone.Volume, verticalZoneAt(x = 900f, containerWidth = 1000f))
    }

    @Test
    fun zero_width_defaults_to_brightness_zone() {
        assertEquals(VerticalZone.Brightness, verticalZoneAt(x = 0f, containerWidth = 0f))
    }
}
