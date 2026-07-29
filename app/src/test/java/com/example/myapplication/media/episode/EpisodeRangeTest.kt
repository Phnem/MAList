package com.example.myapplication.media.episode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Границы диапазона серий стримингового плеера.
 *
 * Ошибка здесь даёт либо мёртвую кнопку, либо переход на несуществующую серию, а сам переход
 * (сеть, резолв ссылки) на JVM не проверить — поэтому проверяется ровно арифметика номеров.
 *
 * Отдельно закреплено правило спеки: «есть соседняя серия по номеру» ≠ «удалось достать ссылку».
 * Неизвестное число серий не имеет права выглядеть как «серий больше нет».
 */
class EpisodeRangeTest {

    @Test
    fun first_episode_has_no_previous() {
        assertFalse(EpisodeRange.hasPrevious(1))
        assertNull(EpisodeRange.previousOf(1))
    }

    @Test
    fun middle_episode_has_both_neighbours() {
        assertTrue(EpisodeRange.hasPrevious(5))
        assertTrue(EpisodeRange.hasNext(5, availableEpisodes = 12))
        assertEquals(4, EpisodeRange.previousOf(5))
        assertEquals(6, EpisodeRange.nextOf(5, availableEpisodes = 12))
    }

    @Test
    fun last_episode_of_season_has_no_next() {
        assertFalse(EpisodeRange.hasNext(12, availableEpisodes = 12))
        assertNull(EpisodeRange.nextOf(12, availableEpisodes = 12))
        assertTrue(EpisodeRange.hasPrevious(12))
    }

    @Test
    fun single_episode_season_has_no_neighbours() {
        assertFalse(EpisodeRange.hasPrevious(1))
        assertFalse(EpisodeRange.hasNext(1, availableEpisodes = 1))
    }

    @Test
    fun unknown_episode_count_never_reads_as_no_next_episode() {
        // Число серий не разрешено (null) или записано нулём — это «неизвестно», а не «конец».
        // Кнопка остаётся активной; выяснится всё на резолве ссылки, и это другое состояние.
        assertTrue(EpisodeRange.hasNext(3, availableEpisodes = null))
        assertTrue(EpisodeRange.hasNext(3, availableEpisodes = 0))
        assertEquals(4, EpisodeRange.nextOf(3, availableEpisodes = null))
    }

    @Test
    fun episode_beyond_the_known_count_offers_no_next() {
        // Номер серии больше известного числа (например, счётчик онгоинга отстал) — вперёд не идём,
        // назад можно.
        assertFalse(EpisodeRange.hasNext(20, availableEpisodes = 12))
        assertTrue(EpisodeRange.hasPrevious(20))
    }

    @Test
    fun episode_below_the_first_is_clamped_not_extrapolated() {
        assertFalse(EpisodeRange.hasPrevious(0))
        assertFalse(EpisodeRange.hasPrevious(-3))
    }
}
