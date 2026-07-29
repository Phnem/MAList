package com.example.myapplication.localplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило жеста разворота кадра.
 *
 * Сам жест на JVM не проверить, а вот «щипок переводит кадр ровно в одно из двух положений и не
 * срабатывает от дрожания пальцев» проверить обязательно: произвольный масштаб пользователь уже
 * отверг, и вернуть его случайным порогом нельзя.
 */
class PlayerZoomTest {

    @Test
    fun pinch_out_asks_for_the_cropped_frame() {
        assertEquals(VideoFit.CROP, pinchFitTarget(PINCH_FIT_RATIO))
        assertEquals(VideoFit.CROP, pinchFitTarget(2f))
    }

    @Test
    fun pinch_in_asks_for_the_original_frame() {
        assertEquals(VideoFit.ORIGINAL, pinchFitTarget(1f / PINCH_FIT_RATIO))
        assertEquals(VideoFit.ORIGINAL, pinchFitTarget(0.4f))
    }

    @Test
    fun small_pinch_changes_nothing() {
        // Пальцы почти всегда чуть расходятся при обычном касании двумя точками — на таком
        // движении кадр перестраиваться не должен.
        assertNull(pinchFitTarget(1f))
        assertNull(pinchFitTarget(1.05f))
        assertNull(pinchFitTarget(0.95f))
    }

    @Test
    fun threshold_is_symmetric() {
        // Порог «наружу» и «внутрь» — одно и то же движение пальцев в разные стороны. Разъехавшись,
        // они дали бы жест, который легко увеличивает и с трудом уменьшает.
        assertEquals(VideoFit.CROP, pinchFitTarget(PINCH_FIT_RATIO))
        assertEquals(VideoFit.ORIGINAL, pinchFitTarget(1f / PINCH_FIT_RATIO))
        assertNull(pinchFitTarget(PINCH_FIT_RATIO - 0.01f))
        assertNull(pinchFitTarget(1f / (PINCH_FIT_RATIO - 0.01f)))
    }

    @Test
    fun degenerate_zoom_is_ignored() {
        // calculateZoom() отдаёт 0 или бесконечность, когда указатели совпали в точке.
        assertNull(pinchFitTarget(0f))
        assertNull(pinchFitTarget(Float.NaN))
        assertNull(pinchFitTarget(Float.POSITIVE_INFINITY))
    }

    @Test
    fun speed_hold_lives_in_the_right_half() {
        // Та же половина, что у перемотки вперёд дабл-тапом.
        assertTrue(isSpeedHoldZone(x = 900f, containerWidth = 1000f))
        assertFalse(isSpeedHoldZone(x = 100f, containerWidth = 1000f))
        // Ровно середина — ещё левая половина: границу отдаём перемотке назад.
        assertFalse(isSpeedHoldZone(x = 500f, containerWidth = 1000f))
    }

    @Test
    fun speed_hold_zone_is_empty_until_the_size_is_known() {
        // Первый кадр приходит с нулевым размером; иначе касание по левому краю сошло бы за правое.
        assertFalse(isSpeedHoldZone(x = 0f, containerWidth = 0f))
    }

    @Test
    fun hold_speed_is_double() {
        assertEquals(2f, HOLD_SPEED, 0.001f)
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
