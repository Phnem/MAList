package com.example.myapplication.media.source

import com.example.myapplication.domain.seasons.SeasonInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Регрессия на дефект 2026-08-02: вместо второго сезона играл первый.
 *
 * Yummy-путь `KodikSource` о сезоне не знал вовсе. Русское название франшизы намеренно остаётся
 * названием ПЕРВОГО сезона (`SeasonSourceAnime`), поэтому релиз первого сезона выигрывал поиск,
 * а серия выбиралась по одному лишь номеру: запросили S2E1 — получили S1E1.
 */
class KodikYummySeasonTest {

    private fun season(number: Int, title: String?) = SeasonInfo(
        seasonNumber = number,
        episodes = 12,
        source = "AniList",
        title = title,
    )

    /** Тот самый контрпример из логката. */
    @Test
    fun `season one release cannot serve season two`() {
        assertFalse(
            yummyReleaseServesSeason(
                releaseTitles = listOf(
                    "Низкоуровневый персонаж Томодзаки",
                    "Bottom-Tier Character Tomozaki",
                ),
                slug = "tomozaki-kun",
                seasonInfo = season(2, "Bottom-Tier Character Tomozaki 2nd Stage"),
            )
        )
    }

    @Test
    fun `sequel release with an ordinal marker serves season two`() {
        assertTrue(
            yummyReleaseServesSeason(
                releaseTitles = listOf("Низкоуровневый персонаж Томодзаки 2"),
                slug = "tomozaki-kun-2",
                seasonInfo = season(2, "Bottom-Tier Character Tomozaki 2nd Stage"),
            )
        )
    }

    /** Маркер в слаге засчитывается: русские каталоги часто не несут его в названии. */
    @Test
    fun `ordinal marker in the slug alone is enough`() {
        assertTrue(
            yummyReleaseServesSeason(
                releaseTitles = listOf("Низкоуровневый персонаж Томодзаки"),
                slug = "tomozaki-kun-2",
                seasonInfo = season(2, "Bottom-Tier Character Tomozaki 2nd Stage"),
            )
        )
    }

    /** Точное совпадение с сезонным названием — доказательство само по себе. */
    @Test
    fun `exact season title match serves the season`() {
        assertTrue(
            yummyReleaseServesSeason(
                releaseTitles = listOf("Bottom-Tier Character Tomozaki 2nd Stage"),
                slug = "tomozaki-kun-second",
                seasonInfo = season(2, "Bottom-Tier Character Tomozaki 2nd Stage"),
            )
        )
    }

    /** Первый сезон однозначен: доказывать нечего, прежнее поведение сохраняется. */
    @Test
    fun `season one needs no proof`() {
        assertTrue(
            yummyReleaseServesSeason(
                releaseTitles = listOf("Низкоуровневый персонаж Томодзаки"),
                slug = "tomozaki-kun",
                seasonInfo = season(1, "Bottom-Tier Character Tomozaki"),
            )
        )
    }

    /** Сезон не выбран — путь работает как до правки. */
    @Test
    fun `absent season info needs no proof`() {
        assertTrue(
            yummyReleaseServesSeason(
                releaseTitles = listOf("Низкоуровневый персонаж Томодзаки"),
                slug = "tomozaki-kun",
                seasonInfo = null,
            )
        )
    }

    /** Соседний сезон не проходит по чужому маркеру. */
    @Test
    fun `second season release cannot serve the third`() {
        assertFalse(
            yummyReleaseServesSeason(
                releaseTitles = listOf("Низкоуровневый персонаж Томодзаки 2"),
                slug = "tomozaki-kun-2",
                seasonInfo = season(3, "Bottom-Tier Character Tomozaki 3rd Stage"),
            )
        )
    }
}
