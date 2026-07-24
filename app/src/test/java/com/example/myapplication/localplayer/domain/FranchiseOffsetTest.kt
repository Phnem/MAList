package com.example.myapplication.localplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FranchiseOffsetTest {

    private val franchise = listOf(
        FranchiseSeason(malId = 10, episodes = 24), // сезон 1
        FranchiseSeason(malId = 20, episodes = 12), // сезон 2
        FranchiseSeason(malId = 30, episodes = 13), // сезон 3
    )

    @Test
    fun example_from_spec_abs26_maps_to_season2_ep2() {
        // 26 - 24 = 2 → сезон 2 (mal 20), эпизод 2
        assertEquals(20 to 2, offsetAbsoluteEpisode(franchise, 26))
    }

    @Test
    fun first_season_passthrough() {
        assertEquals(10 to 1, offsetAbsoluteEpisode(franchise, 1))
        assertEquals(10 to 24, offsetAbsoluteEpisode(franchise, 24))
    }

    @Test
    fun boundary_into_third_season() {
        // 24 + 12 = 36 конец 2 сезона; 37 → сезон 3 эпизод 1
        assertEquals(30 to 1, offsetAbsoluteEpisode(franchise, 37))
        assertEquals(30 to 13, offsetAbsoluteEpisode(franchise, 49))
    }

    @Test
    fun overflow_returns_null() {
        assertNull(offsetAbsoluteEpisode(franchise, 50))
    }

    @Test
    fun non_positive_returns_null() {
        assertNull(offsetAbsoluteEpisode(franchise, 0))
        assertNull(offsetAbsoluteEpisode(franchise, -3))
    }

    @Test
    fun skips_zero_length_seasons() {
        val withUnknown = listOf(
            FranchiseSeason(10, 0), // неизвестно число серий — пропускаем
            FranchiseSeason(20, 12),
        )
        assertEquals(20 to 5, offsetAbsoluteEpisode(withUnknown, 5))
    }
}
