package com.example.myapplication.data.local.migrations

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.myapplication.data.local.AnimeDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Прогоняет ровно `13.sqm` (переход version 13 → 14) на вручную собранной пред-миграционной
 * таблице (SQLDelight не хранит исторические CREATE TABLE по версиям — `Anime.sq` всегда
 * отражает текущую/последнюю форму, поэтому для проверки самой миграции нужна reconstructed
 * форма "после 12.sqm, до 13.sqm", а не `Schema.create()`, который всегда даёт последнюю форму).
 *
 * Проверяет главный риск миграции 13: разделение `TV_SERIES` на `MOVIE`/`SERIES` по
 * `categoryType`, с дефолтом `SERIES` для неоднозначных записей, без потери данных.
 */
class Migration13Test {

    private lateinit var driver: JdbcSqliteDriver

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, V12_CREATE_TABLE, 0)
        driver.execute(null, "PRAGMA user_version = 13", 0)
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `TV_SERIES with MOVIE categoryType becomes MOVIE`() {
        seed(id = "a", mediaType = "TV_SERIES", categoryType = "MOVIE")
        migrate()
        assertEquals("MOVIE", mediaTypeOf("a"))
    }

    @Test
    fun `TV_SERIES with SERIES categoryType becomes SERIES`() {
        seed(id = "b", mediaType = "TV_SERIES", categoryType = "SERIES")
        migrate()
        assertEquals("SERIES", mediaTypeOf("b"))
    }

    @Test
    fun `TV_SERIES with ambiguous categoryType defaults to SERIES`() {
        seed(id = "c", mediaType = "TV_SERIES", categoryType = "")
        seed(id = "d", mediaType = "TV_SERIES", categoryType = "Комедия")
        migrate()
        assertEquals("SERIES", mediaTypeOf("c"))
        assertEquals("SERIES", mediaTypeOf("d"))
    }

    @Test
    fun `ANIME and MANGA rows are untouched`() {
        seed(id = "e", mediaType = "ANIME", categoryType = "ANIME")
        seed(id = "f", mediaType = "MANGA", categoryType = "MANGA")
        migrate()
        assertEquals("ANIME", mediaTypeOf("e"))
        assertEquals("MANGA", mediaTypeOf("f"))
    }

    @Test
    fun `no rows are lost and new id columns default to null`() {
        seed(id = "g", mediaType = "TV_SERIES", categoryType = "MOVIE")
        migrate()

        val db = AnimeDatabase(driver)
        val rows = db.animeQueries.getAllAnime().executeAsList()
        assertEquals(1, rows.size)
        assertNull(rows.single().tmdb_id)
        assertNull(rows.single().kinopoisk_id)
    }

    private fun seed(id: String, mediaType: String, categoryType: String) {
        driver.execute(
            null,
            """
            INSERT INTO anime(
                id, title, episodes, rating, status, updatedAt, dateAdded, categoryType, mediaType
            ) VALUES ('$id', 'Title $id', 1, 0, 'watching', 0, 0, '$categoryType', '$mediaType')
            """.trimIndent(),
            0
        )
    }

    private fun migrate() {
        // 13.sqm применяется на переходе version 13 → 14 (SQLDelight: файл N.sqm срабатывает
        // при oldVersion <= N && newVersion > N — т.е. это МИГРАЦИЯ ИЗ 13, а не В 13).
        AnimeDatabase.Schema.migrate(driver, 13, 14).value
    }

    private fun mediaTypeOf(id: String): String {
        var result: String? = null
        driver.executeQuery(
            null,
            "SELECT mediaType FROM anime WHERE id = '$id'",
            { cursor ->
                if (cursor.next().value) result = cursor.getString(0)
                QueryResult.Value(Unit)
            },
            0
        )
        return result ?: error("Row $id not found after migration")
    }

    private companion object {
        // Форма таблицы `anime` сразу после 12.sqm — без tmdb_id/kinopoisk_id/*_not_found_at,
        // которые вводит миграция 13. Держать в синхроне с историческим состоянием Anime.sq
        // на момент 12.sqm, не с текущим (текущее — в основном .sq-файле).
        const val V12_CREATE_TABLE = """
            CREATE TABLE anime (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                imagePath TEXT,
                episodes INTEGER NOT NULL,
                rating INTEGER NOT NULL,
                status TEXT NOT NULL,
                isFavorite INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL DEFAULT 0,
                dateAdded INTEGER NOT NULL,
                categoryType TEXT NOT NULL DEFAULT '',
                sync_status TEXT NOT NULL DEFAULT 'SYNCED',
                comment TEXT NOT NULL DEFAULT '',
                isAiRecommendation INTEGER NOT NULL DEFAULT 0,
                anilist_id INTEGER,
                mal_id INTEGER,
                shikimori_id INTEGER,
                anilist_not_found_at INTEGER,
                mal_not_found_at INTEGER,
                shikimori_not_found_at INTEGER,
                isPrivate INTEGER NOT NULL DEFAULT 0,
                encryptionIv TEXT,
                deletedAt INTEGER,
                mediaType TEXT NOT NULL DEFAULT 'ANIME',
                title_en TEXT,
                title_en_checked_at INTEGER,
                title_ru TEXT,
                title_ru_checked_at INTEGER
            )
        """
    }
}
