package com.example.myapplication.media.source

import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.SeasonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonSourceAnimeTest {

    private fun anime(
        title: String,
        titleEn: String? = null,
        titleRu: String? = null,
        episodes: Int = 24,
    ) = Anime(
        id = "food-wars",
        title = title,
        titleEn = titleEn,
        titleRu = titleRu,
        episodes = episodes,
        rating = 0f,
        imageFileName = null,
        orderIndex = 0,
        dateAdded = 0,
    )

    @Test
    fun `selected season title leads the aliases`() {
        val query = anime(
            title = "Food Wars! The Second Plate",
            titleEn = "Food Wars! The Second Plate",
            titleRu = "Повар-боец Сома",
            episodes = 13,
        ).seasonSourceQuery(
            SeasonInfo(
                seasonNumber = 2,
                episodes = 13,
                anilistId = 32282,
                malId = 32282,
                source = "AniList",
                title = "Shokugeki no Souma: Ni no Sara",
            )
        )

        assertEquals("Shokugeki no Souma: Ni no Sara", query.anime.title)
        assertEquals("Shokugeki no Souma: Ni no Sara", query.anime.titleEn)
        assertEquals(32282, query.anime.anilistId)
        assertEquals(2, query.seasonNumber)
        assertTrue(query.seasonIdentifiable)
    }

    /**
     * Регрессия на корень отказа AnimeGo/Kodik/AniLibria: русский алиас — единственный, которым
     * русские каталоги находят тайтл, и сужение до сезона не имеет права его выбрасывать.
     */
    @Test
    fun `russian franchise alias survives season scoping`() {
        val query = anime(
            title = "Food Wars! The Second Plate",
            titleEn = "Food Wars! The Second Plate",
            titleRu = "Повар-боец Сома",
            episodes = 13,
        ).seasonSourceQuery(
            SeasonInfo(
                seasonNumber = 2,
                episodes = 13,
                source = "AniList",
                title = "Shokugeki no Souma: Ni no Sara",
            )
        )

        assertEquals("Повар-боец Сома", query.anime.titleRu)
    }

    /**
     * Регрессия на второй корень: строка сезона от источника просмотра названия не несёт,
     * и раньше это выключало AnimeGo и yummy-путь Kodik целиком.
     */
    @Test
    fun `later season without a title still yields a usable query`() {
        val query = anime(title = "Food Wars!", titleRu = "Повар-боец Сома")
            .seasonSourceQuery(
                SeasonInfo(seasonNumber = 2, episodes = 13, source = "Kodik")
            )

        assertEquals("Food Wars!", query.anime.title)
        assertEquals("Повар-боец Сома", query.anime.titleRu)
        assertEquals(2, query.seasonNumber)
        assertFalse(query.seasonIdentifiable)
    }

    @Test
    fun `no season keeps the row untouched`() {
        val row = anime(title = "Food Wars!", titleRu = "Повар-боец Сома")

        val query = row.seasonSourceQuery(null)

        assertEquals(row, query.anime)
        assertEquals(1, query.seasonNumber)
        assertFalse(query.seasonIdentifiable)
    }

    @Test
    fun `non positive season number is clamped to one`() {
        val query = anime(title = "Food Wars!")
            .seasonSourceQuery(SeasonInfo(seasonNumber = 0, episodes = 24, source = "local"))

        assertEquals(1, query.seasonNumber)
    }
}
