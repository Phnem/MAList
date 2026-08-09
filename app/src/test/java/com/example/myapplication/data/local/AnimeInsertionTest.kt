package com.example.myapplication.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AnimeInsertionTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AnimeDatabase

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AnimeDatabase.Schema.create(driver).value
        database = AnimeDatabase(driver)
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `new series insert atomically creates released-semantics marker`() {
        database.insertNewAnime(entry("series", MediaType.SERIES), updatedAt = 1)

        assertEquals(1L, database.animeQueries.countSeriesEpisodeNormalization("series").executeAsOne())
        assertEquals("SERIES", database.animeQueries.getAnimeById("series").executeAsOne().mediaType)
    }

    @Test
    fun `new anime and movie inserts do not create series marker`() {
        database.insertNewAnime(entry("anime", MediaType.ANIME), updatedAt = 1)
        database.insertNewAnime(entry("movie", MediaType.MOVIE), updatedAt = 1)

        assertEquals(0L, database.animeQueries.countSeriesEpisodeNormalization("anime").executeAsOne())
        assertEquals(0L, database.animeQueries.countSeriesEpisodeNormalization("movie").executeAsOne())
    }

    private fun entry(id: String, mediaType: MediaType) = Anime(
        id = id,
        title = id,
        episodes = 1,
        rating = 0f,
        imageFileName = null,
        orderIndex = 0,
        dateAdded = 0,
        categoryType = mediaType.name,
        mediaType = mediaType,
    )
}
