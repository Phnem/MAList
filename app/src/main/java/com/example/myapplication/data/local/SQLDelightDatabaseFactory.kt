package com.example.myapplication.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.myapplication.data.local.AnimeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

class SQLDelightDatabaseFactory(private val context: Context) {
    private var cachedDriver: SqlDriver? = null
    private var database: AnimeDatabase? = null

    /** При изменении триггера Flow в DataSource переподписываются на новое подключение. */
    val dbConnectionTrigger = MutableStateFlow(0)

    private fun getDriver(): SqlDriver {
        if (cachedDriver == null) {
            alignLegacyAnimeDbUserVersion()
            cachedDriver = AndroidSqliteDriver(
                schema = AnimeDatabase.Schema,
                context = context,
                name = "anime.db"
            )
        }
        return cachedDriver!!
    }

    /**
     * Legacy installs where columns were added outside SQLDelight may have new columns but stale `user_version`.
     * Bump version so SQLDelight does not re-run ALTER (duplicate column).
     */
    private fun alignLegacyAnimeDbUserVersion() {
        val dbFile = context.getDatabasePath("anime.db")
        if (!dbFile.exists()) return
        val targetVersion = AnimeDatabase.Schema.version
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            fun readAnimeColumns(): Set<String> = db.rawQuery("PRAGMA table_info(anime)", null).use { c ->
                buildSet {
                    while (c.moveToNext()) add(c.getString(1))
                }
            }
            fun ensureColumn(name: String, ddl: String) {
                try {
                    db.execSQL(ddl)
                } catch (e: Exception) {
                    Log.w("SQLDelight", "alignLegacy: column=$name", e)
                }
            }

            val colsInitial = readAnimeColumns()
            // Не полагаться только на .sqm: старая логика могла поднять user_version без ALTER.
            if (!colsInitial.contains("isAiRecommendation")) {
                ensureColumn(
                    "isAiRecommendation",
                    "ALTER TABLE anime ADD COLUMN isAiRecommendation INTEGER NOT NULL DEFAULT 0"
                )
            }
            if (!colsInitial.contains("anilist_id")) {
                ensureColumn("anilist_id", "ALTER TABLE anime ADD COLUMN anilist_id INTEGER")
            }
            if (!colsInitial.contains("mal_id")) {
                ensureColumn("mal_id", "ALTER TABLE anime ADD COLUMN mal_id INTEGER")
            }
            if (!colsInitial.contains("shikimori_id")) {
                ensureColumn("shikimori_id", "ALTER TABLE anime ADD COLUMN shikimori_id INTEGER")
            }
            if (!colsInitial.contains("anilist_not_found_at")) {
                ensureColumn("anilist_not_found_at", "ALTER TABLE anime ADD COLUMN anilist_not_found_at INTEGER")
            }
            if (!colsInitial.contains("mal_not_found_at")) {
                ensureColumn("mal_not_found_at", "ALTER TABLE anime ADD COLUMN mal_not_found_at INTEGER")
            }
            if (!colsInitial.contains("shikimori_not_found_at")) {
                ensureColumn("shikimori_not_found_at", "ALTER TABLE anime ADD COLUMN shikimori_not_found_at INTEGER")
            }
            val ver = db.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
            if (ver >= targetVersion) return@use
            val cols = readAnimeColumns()
            val hasSync = cols.contains("sync_status")
            val hasComment = cols.contains("comment")
            val hasEpisodeCheckColumns = cols.contains("anilist_id")
                && cols.contains("mal_id")
                && cols.contains("shikimori_id")
                && cols.contains("anilist_not_found_at")
                && cols.contains("mal_not_found_at")
                && cols.contains("shikimori_not_found_at")
            when {
                hasSync && hasComment && hasEpisodeCheckColumns ->
                    db.execSQL("PRAGMA user_version = $targetVersion")
                hasSync && !hasComment && targetVersion > 1 ->
                    db.execSQL("PRAGMA user_version = ${targetVersion - 1}")
            }
        }
    }

    fun getDatabase(): AnimeDatabase {
        if (database == null) {
            database = AnimeDatabase(getDriver())
        }
        return database!!
    }

    /** Закрывает старый коннект и при следующем доступе открывает новый (после .copyTo миграции). */
    fun reconnectDatabase() {
        cachedDriver?.close()
        cachedDriver = null
        database = null
        dbConnectionTrigger.value += 1
    }

    suspend fun checkpoint() {
        withContext(Dispatchers.IO) {
            try {
                getDriver().let { driver ->
                    if (driver is AndroidSqliteDriver) {
                        // Get SQLiteDatabase through reflection
                        val aClass = Class.forName("app.cash.sqldelight.driver.android.AndroidSqliteDriver")
                        val method = aClass.getDeclaredMethod("getDatabase")
                        method.isAccessible = true
                        val database = method.invoke(driver) as? SQLiteDatabase
                        database?.rawQuery("PRAGMA wal_checkpoint(FULL);", null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val busy = cursor.getInt(0)
                                val log = cursor.getInt(1)
                                val checkpointed = cursor.getInt(2)
                                Log.d("SQLDelight", "WAL checkpoint: busy=$busy, log=$log, checkpointed=$checkpointed")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Checkpoint is not critical, log and continue
                Log.w("SQLDelight", "Failed to checkpoint WAL", e)
            }
        }
    }
}
