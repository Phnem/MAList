package com.example.myapplication.media.episode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Когда конец серии означает «включай следующую», а когда — «ничего не делай». */
class AutoAdvanceTest {

    @Test
    fun finished_episode_with_a_successor_advances() {
        assertTrue(shouldAutoAdvance(enabled = true, durationMs = 1_400_000L, hasNext = true, switching = false))
    }

    @Test
    fun disabled_setting_never_advances() {
        assertFalse(shouldAutoAdvance(enabled = false, durationMs = 1_400_000L, hasNext = true, switching = false))
    }

    @Test
    fun last_episode_stays_where_it_is() {
        assertFalse(shouldAutoAdvance(enabled = true, durationMs = 1_400_000L, hasNext = false, switching = false))
    }

    @Test
    fun unknown_duration_is_not_a_finished_episode() {
        // Пустой или сорвавшийся источник тоже отдаёт STATE_ENDED — но серия при этом не досмотрена.
        assertFalse(shouldAutoAdvance(enabled = true, durationMs = 0L, hasNext = true, switching = false))
    }

    @Test
    fun switch_already_in_flight_is_not_restarted() {
        // Иначе повторный STATE_ENDED во время резолва перепрыгнул бы серию.
        assertFalse(shouldAutoAdvance(enabled = true, durationMs = 1_400_000L, hasNext = true, switching = true))
    }
}
