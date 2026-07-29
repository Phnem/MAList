package com.example.myapplication.ui.shared.loading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Форма движения катаны. Границы фаз и выравнивание катаны с ножнами к концу оборота глазами
 * достоверно не проверить — здесь они закреплены как свойства.
 */
class KatanaCycleTest {

    private val eps = 1e-4f

    /** Точки внутри фазы, не задевающие её границы. */
    private fun inside(from: Float, to: Float, steps: Int = 40): List<Float> =
        (1 until steps).map { from + (to - from) * it / steps.toFloat() }

    // ---- Вытягивание -------------------------------------------------------

    @Test
    fun bladeRestsInsideScabbardDuringPause() {
        assertEquals(0f, KatanaCycle.drawOut(0f), eps)
        assertEquals(0f, KatanaCycle.drawOut(KatanaCycle.SHEATHE_END), eps)
        assertEquals(0f, KatanaCycle.drawOut(0.95f), eps)
        assertEquals(0f, KatanaCycle.drawOut(1f), eps)
    }

    @Test
    fun bladeIsFullyDrawnThroughoutTheOrbit() {
        assertEquals(1f, KatanaCycle.drawOut(KatanaCycle.DRAW_END), eps)
        assertEquals(1f, KatanaCycle.drawOut(0.45f), eps)
        assertEquals(1f, KatanaCycle.drawOut(KatanaCycle.ORBIT_END), eps)
    }

    @Test
    fun drawOutRisesMonotonically() {
        var previous = -1f
        for (p in inside(0f, KatanaCycle.DRAW_END)) {
            val value = KatanaCycle.drawOut(p)
            assertTrue("drawOut упал на p=$p", value > previous)
            previous = value
        }
    }

    @Test
    fun drawOutFallsMonotonicallyWhileSheathing() {
        var previous = 2f
        for (p in inside(KatanaCycle.ORBIT_END, KatanaCycle.SHEATHE_END)) {
            val value = KatanaCycle.drawOut(p)
            assertTrue("drawOut вырос на возврате, p=$p", value < previous)
            previous = value
        }
    }

    // ---- Оборот ------------------------------------------------------------

    @Test
    fun orbitRunsExactlyOneTurnBetweenPhaseBounds() {
        assertEquals(0f, KatanaCycle.orbitTurns(0f), eps)
        assertEquals(0f, KatanaCycle.orbitTurns(KatanaCycle.DRAW_END), eps)
        assertEquals(1f, KatanaCycle.orbitTurns(KatanaCycle.ORBIT_END), eps)
    }

    @Test
    fun katanaStaysAlignedWithScabbardAfterTheOrbit() {
        // Полный оборот = то же направление. Любое значение, кроме целого, означало бы, что
        // катана возвращается в ножны под углом.
        for (p in inside(KatanaCycle.ORBIT_END, 1f)) {
            assertEquals("оборот сорвался на p=$p", 1f, KatanaCycle.orbitTurns(p), eps)
        }
        assertEquals(1f, KatanaCycle.orbitTurns(1f), eps)
    }

    @Test
    fun orbitRisesMonotonically() {
        var previous = -1f
        for (p in inside(KatanaCycle.DRAW_END, KatanaCycle.ORBIT_END)) {
            val value = KatanaCycle.orbitTurns(p)
            assertTrue("оборот пошёл назад на p=$p", value > previous)
            previous = value
        }
    }

    // ---- Окружность --------------------------------------------------------

    @Test
    fun arcIsTracedByTheTip() {
        // Дуга обязана совпадать с положением острия: расхождение означало бы, что окружность
        // рисуется сама по себе, а не остриём.
        for (p in inside(0f, 1f, 100)) {
            assertEquals(KatanaCycle.orbitTurns(p), KatanaCycle.arcSweep(p), eps)
        }
    }

    @Test
    fun arcFadesOutExactlyWhileTheBladeReturns() {
        assertEquals(1f, KatanaCycle.arcAlpha(KatanaCycle.ORBIT_END), eps)
        assertEquals(0f, KatanaCycle.arcAlpha(KatanaCycle.SHEATHE_END), eps)
        assertEquals(0f, KatanaCycle.arcAlpha(0.95f), eps)

        var previous = 2f
        for (p in inside(KatanaCycle.ORBIT_END, KatanaCycle.SHEATHE_END)) {
            val value = KatanaCycle.arcAlpha(p)
            assertTrue("окружность вернулась на p=$p", value < previous)
            previous = value
        }
    }

    // ---- Инерция ножен -----------------------------------------------------

    @Test
    fun scabbardRestsWhenNothingMoves() {
        assertEquals(0f, KatanaCycle.scabbardAxialLag(0f), eps)
        assertEquals(0f, KatanaCycle.scabbardAxialLag(KatanaCycle.DRAW_END), eps)
        assertEquals(0f, KatanaCycle.scabbardAxialLag(0.45f), eps)
        assertEquals(0f, KatanaCycle.scabbardAxialLag(KatanaCycle.SHEATHE_END), eps)
        assertEquals(0f, KatanaCycle.scabbardAxialLag(1f), eps)
    }

    @Test
    fun scabbardIsDraggedAlongTheDraw() {
        val values = inside(0f, KatanaCycle.DRAW_END).map { KatanaCycle.scabbardAxialLag(it) }
        assertTrue("ножны не шелохнулись при вытягивании", values.any { abs(it) > 1e-3f })
        // Первое движение — вслед за катаной, то есть наружу.
        assertTrue("ножны дёрнулись против хода вытягивания", values.first() > 0f)
    }

    @Test
    fun scabbardIsNudgedAlongTheReturn() {
        val values = inside(KatanaCycle.ORBIT_END, KatanaCycle.SHEATHE_END)
            .map { KatanaCycle.scabbardAxialLag(it) }
        assertTrue("ножны не шелохнулись на возврате", values.any { abs(it) > 1e-3f })
        // Возврат идёт внутрь — пружина сдвигает ножны туда же.
        assertTrue("ножны дёрнулись против хода возврата", values.first() < 0f)
    }

    @Test
    fun scabbardSpringDecays() {
        val extrema = localExtrema(
            inside(0f, KatanaCycle.DRAW_END, 200).map { KatanaCycle.scabbardAxialLag(it) }
        )
        assertTrue("нужно минимум два экстремума, чтобы говорить о затухании", extrema.size >= 2)
        extrema.zipWithNext { first, second ->
            assertTrue("колебание ножен не затухает: $first → $second", abs(second) < abs(first))
        }
    }

    @Test
    fun scabbardTrailsBehindDuringTheOrbitAndCatchesUpByItsEnd() {
        assertEquals(0f, KatanaCycle.scabbardAngularLag(KatanaCycle.DRAW_END), eps)
        // К концу оборота отставания нет: иначе катана вошла бы в ножны, не поравнявшись с ними.
        assertEquals(0f, KatanaCycle.scabbardAngularLag(KatanaCycle.ORBIT_END), eps)
        assertEquals(0f, KatanaCycle.scabbardAngularLag(0.8f), eps)

        val mid = KatanaCycle.scabbardAngularLag((KatanaCycle.DRAW_END + KatanaCycle.ORBIT_END) / 2f)
        assertTrue("ножны не волочатся за катаной", abs(mid) > 1e-3f)
        assertTrue("ножны обгоняют катану вместо того, чтобы отставать", mid < 0f)
    }

    // ---- Устойчивость ------------------------------------------------------

    @Test
    fun inputOutsideTheCycleIsClamped() {
        assertEquals(KatanaCycle.drawOut(0f), KatanaCycle.drawOut(-3f), eps)
        assertEquals(KatanaCycle.drawOut(1f), KatanaCycle.drawOut(4f), eps)
        assertEquals(KatanaCycle.orbitTurns(1f), KatanaCycle.orbitTurns(9f), eps)
        assertEquals(KatanaCycle.arcAlpha(0f), KatanaCycle.arcAlpha(-1f), eps)
        assertEquals(KatanaCycle.scabbardAxialLag(1f), KatanaCycle.scabbardAxialLag(2f), eps)
        assertEquals(KatanaCycle.scabbardAngularLag(0f), KatanaCycle.scabbardAngularLag(-7f), eps)
    }

    @Test
    fun everyValueStaysFinite() {
        for (p in inside(-0.5f, 1.5f, 400)) {
            assertTrue(KatanaCycle.drawOut(p).isFinite())
            assertTrue(KatanaCycle.orbitTurns(p).isFinite())
            assertTrue(KatanaCycle.arcSweep(p).isFinite())
            assertTrue(KatanaCycle.arcAlpha(p).isFinite())
            assertTrue(KatanaCycle.scabbardAxialLag(p).isFinite())
            assertTrue(KatanaCycle.scabbardAngularLag(p).isFinite())
        }
    }

    /** Значения в точках смены знака производной. */
    private fun localExtrema(values: List<Float>): List<Float> =
        values.indices.drop(1).dropLast(1).mapNotNull { i ->
            val rising = values[i] - values[i - 1]
            val falling = values[i + 1] - values[i]
            if (rising > 0f && falling < 0f || rising < 0f && falling > 0f) values[i] else null
        }
}
