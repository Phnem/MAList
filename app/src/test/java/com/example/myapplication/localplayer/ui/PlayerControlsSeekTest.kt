package com.example.myapplication.localplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Перемотка на +89 секунд одним тапом по пузырьку слева от нижнего дока. */
class PlayerControlsSeekTest {

    @Test
    fun `seeks forward by the full amount when duration allows it`() {
        assertEquals(101_000L, seekForwardTarget(position = 12_000L, duration = 600_000L, amountMs = 89_000L))
    }

    @Test
    fun `clamps at the known duration instead of overshooting`() {
        assertEquals(600_000L, seekForwardTarget(position = 590_000L, duration = 600_000L, amountMs = 89_000L))
    }

    @Test
    fun `does not clamp when duration is not known yet`() {
        assertEquals(101_000L, seekForwardTarget(position = 12_000L, duration = 0L, amountMs = 89_000L))
    }
}
