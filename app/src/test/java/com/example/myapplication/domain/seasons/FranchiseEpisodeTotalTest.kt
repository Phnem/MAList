package com.example.myapplication.domain.seasons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FranchiseEpisodeTotalTest {

    private fun season(number: Int, episodes: Int) = SeasonInfo(
        seasonNumber = number,
        episodes = episodes,
        source = "AniList",
    )

    private fun entry(vararg seasons: SeasonInfo) = SeasonEpisodesEntry(
        animeId = "id",
        seasons = seasons.toList(),
    )

    @Test
    fun `multi-season layout sums every season`() {
        val layout = entry(season(1, 13), season(2, 12), season(3, 12))
        assertEquals(37, franchiseEpisodeTotal(layout, storedEpisodes = 12))
    }

    @Test
    fun `single-season layout matches the stored count`() {
        assertEquals(12, franchiseEpisodeTotal(entry(season(1, 12)), storedEpisodes = 12))
    }

    /** «Расклад не готов» — это неизвестность, а не ноль: знаменатель остаётся прежним. */
    @Test
    fun `missing layout falls back to the stored count`() {
        assertEquals(12, franchiseEpisodeTotal(null, storedEpisodes = 12))
    }

    @Test
    fun `empty layout falls back to the stored count`() {
        assertEquals(12, franchiseEpisodeTotal(entry(), storedEpisodes = 12))
    }

    /** Нечего показать вовсе — счётчик обязан остаться без знаменателя, а не выдумать его. */
    @Test
    fun `nothing known stays zero so the counter drops the denominator`() {
        assertEquals(0, franchiseEpisodeTotal(null, storedEpisodes = 0))
        assertEquals(0, franchiseEpisodeTotal(entry(), storedEpisodes = 0))
    }

    /**
     * Регрессия «просмотрено 15 / 12».
     *
     * Числитель считается сквозной нумерацией: серии сезонов до текущего плюс номер текущей
     * серии (см. `HomeViewModel.watchedEpisodes`). Пока в раскладе есть текущий сезон, сумма по
     * франшизе не может оказаться меньше — иначе шкалы снова разъехались бы.
     */
    @Test
    fun `franchise total never trails the franchise-scale watched counter`() {
        val layout = entry(season(1, 12), season(2, 12), season(3, 12))
        val total = franchiseEpisodeTotal(layout, storedEpisodes = 12)

        for (currentSeason in 1..3) {
            for (episode in 1..12) {
                val watched = layout.seasons
                    .filter { it.seasonNumber < currentSeason }
                    .sumOf { it.episodes } + episode
                assertTrue(
                    "S${currentSeason}E$episode: просмотрено $watched > всего $total",
                    watched <= total,
                )
            }
        }
    }

    /** Прежнее поведение на односезонном тайтле: числитель и знаменатель совпадают на финале. */
    @Test
    fun `single season title still ends exactly at the denominator`() {
        val layout = entry(season(1, 12))
        assertEquals(12, franchiseEpisodeTotal(layout, storedEpisodes = 12))
    }
}
