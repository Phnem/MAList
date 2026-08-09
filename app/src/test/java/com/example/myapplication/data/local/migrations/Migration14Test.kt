package com.example.myapplication.data.local.migrations

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.myapplication.data.local.AnimeDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class Migration14Test {
    private lateinit var driver: JdbcSqliteDriver

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "CREATE TABLE anime (id TEXT PRIMARY KEY NOT NULL)", 0)
        driver.execute(null, "INSERT INTO anime(id) VALUES ('series')", 0)
        driver.execute(null, "PRAGMA user_version = 14", 0)
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `migration adds persistent series normalization marker`() {
        AnimeDatabase.Schema.migrate(driver, 14, 15).value
        val queries = AnimeDatabase(driver).animeQueries

        assertEquals(0L, queries.countSeriesEpisodeNormalization("series").executeAsOne())
        queries.markSeriesEpisodesNormalized("series")
        assertEquals(1L, queries.countSeriesEpisodeNormalization("series").executeAsOne())
    }
}
