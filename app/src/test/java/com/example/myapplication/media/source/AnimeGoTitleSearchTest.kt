package com.example.myapplication.media.source

import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.SeasonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeGoTitleSearchTest {

    private fun query(
        title: String,
        titleRu: String? = null,
        titleEn: String? = null,
        season: SeasonInfo? = null,
    ) = Anime(
        id = "food-wars",
        title = title,
        titleEn = titleEn,
        titleRu = titleRu,
        episodes = 13,
        rating = 0f,
        imageFileName = null,
        orderIndex = 0,
        dateAdded = 0,
    ).seasonSourceQuery(season)

    /**
     * Регрессия на корень отказа AnimeGo: русский каталог опрашивался английским названием
     * сезона, потому что сужение до сезона обнуляло `titleRu`.
     */
    @Test
    fun `russian alias with an ordinal marker leads the ladder`() {
        val queries = animeGoSearchQueries(
            query(
                title = "Food Wars! The Second Plate",
                titleRu = "Повар-боец Сома",
                season = SeasonInfo(
                    seasonNumber = 2,
                    episodes = 13,
                    source = "AniList",
                    title = "Shokugeki no Souma: Ni no Sara",
                ),
            )
        )

        assertEquals("Повар-боец Сома 2", queries.first())
        assertTrue(queries.contains("Повар-боец Сома"))
        assertTrue(queries.contains("Shokugeki no Souma: Ni no Sara"))
    }

    @Test
    fun `first season does not get an ordinal marker`() {
        val queries = animeGoSearchQueries(
            query(
                title = "Food Wars!",
                titleRu = "Повар-боец Сома",
                season = SeasonInfo(seasonNumber = 1, episodes = 24, source = "AniList"),
            )
        )

        assertEquals("Повар-боец Сома", queries.first())
        assertFalse(queries.any { it.endsWith(" 1") })
    }

    @Test
    fun `season without a title still gets the ordinal ladder`() {
        val queries = animeGoSearchQueries(
            query(
                title = "Food Wars!",
                titleRu = "Повар-боец Сома",
                season = SeasonInfo(seasonNumber = 3, episodes = 24, source = "Kodik"),
            )
        )

        assertEquals("Повар-боец Сома 3", queries.first())
    }

    @Test
    fun `queries are deduplicated case insensitively`() {
        val queries = animeGoSearchQueries(
            query(title = "Food Wars!", titleEn = "food wars!")
        )

        assertEquals(listOf("Food Wars!"), queries)
    }

    @Test
    fun `franchise page is rejected for a later season`() {
        assertFalse(
            animeGoPageMatchesSeason(
                pageTitle = "Повар-боец Сома",
                seasonTitle = "Shokugeki no Souma: Ni no Sara",
                franchiseTitles = listOf("Повар-боец Сома", "Food Wars!"),
                seasonNumber = 2,
            )
        )
    }

    @Test
    fun `franchise page with the ordinal marker is accepted`() {
        assertTrue(
            animeGoPageMatchesSeason(
                pageTitle = "Повар-боец Сома 2",
                seasonTitle = "Shokugeki no Souma: Ni no Sara",
                franchiseTitles = listOf("Повар-боец Сома", "Food Wars!"),
                seasonNumber = 2,
            )
        )
    }

    @Test
    fun `page matching the season title is accepted without a marker`() {
        assertTrue(
            animeGoPageMatchesSeason(
                pageTitle = "Food Wars! The Second Plate",
                seasonTitle = "Food Wars! The Second Plate",
                franchiseTitles = listOf("Повар-боец Сома"),
                seasonNumber = 2,
            )
        )
    }

    @Test
    fun `franchise page is accepted for the first season`() {
        assertTrue(
            animeGoPageMatchesSeason(
                pageTitle = "Повар-боец Сома",
                seasonTitle = null,
                franchiseTitles = listOf("Повар-боец Сома"),
                seasonNumber = 1,
            )
        )
    }

    @Test
    fun `unrelated page is rejected`() {
        assertFalse(
            animeGoPageMatchesSeason(
                pageTitle = "Наруто 2",
                seasonTitle = "Shokugeki no Souma: Ni no Sara",
                franchiseTitles = listOf("Повар-боец Сома"),
                seasonNumber = 2,
            )
        )
    }

    @Test
    fun `ordinal marker requires a standalone token`() {
        assertTrue(containsSeasonOrdinal("Повар-боец Сома 2", 2))
        assertFalse(containsSeasonOrdinal("Повар-боец Сома 12", 2))
        assertFalse(containsSeasonOrdinal("Souma 2nd", 2))
    }
}
