package com.example.myapplication.data.local

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.AnimeUpdate
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

private const val TAG = "MigrationManager"
private const val ROOT = "Vetro"
private const val LIST_FILE_NAME = "list.json"
private const val IGNORED_FILE_NAME = "ignored.json"
private const val UPDATES_FILE_NAME = "updates.json"
private val MIGRATION_COMPLETED = booleanPreferencesKey("migration_completed")

class MigrationManager(
    private val context: Context,
    private val localDataSource: AnimeLocalDataSource,
    private val dataStore: DataStore<Preferences>
) {

    suspend fun runMigration() {
        // Safety net: if the flag says "completed" but the DB is empty
        // and list.json or list.json.backup still exists, a previous buggy run set the flag
        // without actually importing. Reset and retry.
        if (isMigrationCompleted()) {
            val dbEmpty = try { localDataSource.getAnimeCount() == 0 } catch (e: Exception) { true }
            if (dbEmpty) {
                val root = getRoot()
                val listFile = File(root, LIST_FILE_NAME)
                val listBackupFile = File(root, "$LIST_FILE_NAME.backup")
                val hasListFile = (listFile.exists() && listFile.length() > 10) || 
                                 (listBackupFile.exists() && listBackupFile.length() > 10)
                if (hasListFile) {
                    Log.w(TAG, "DB is empty but migration flag was set and list.json(.backup) exists — resetting flag")
                    resetMigrationFlag()
                } else {
                    Log.d(TAG, "Migration already completed, skipping")
                    return
                }
            } else {
                Log.d(TAG, "Migration already completed (DB has data), skipping")
                return
            }
        }

        val root = getRoot()

        // Check if we can actually access the storage directory.
        // On Android 11+, MANAGE_EXTERNAL_STORAGE must be granted first.
        // If not granted yet, root.exists()/canRead() will return false even
        // though the files are really there. In that case we must NOT mark
        // migration as completed — we need to retry later after permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Log.w(TAG, "Storage permission not granted yet, deferring migration")
            return
        }

        if (!root.exists()) {
            Log.d(TAG, "Root directory does not exist: ${root.absolutePath}, nothing to migrate")
            setMigrationCompleted()
            return
        }

        // Try original files first, then .backup files
        val listFile = findFile(root, LIST_FILE_NAME)
        val ignoredFile = findFile(root, IGNORED_FILE_NAME)
        val updatesFile = findFile(root, UPDATES_FILE_NAME)

        val hasAnyFile = listFile != null || ignoredFile != null || updatesFile != null
        if (!hasAnyFile) {
            Log.d(TAG, "No JSON files found in ${root.absolutePath}, nothing to migrate")
            setMigrationCompleted()
            return
        }

        // Only migrate if the DB is actually empty (avoid duplicates on re-run)
        val dbCount = try { localDataSource.getAnimeCount() } catch (e: Exception) { 0 }

        var migratedCount = 0

        if (listFile != null && dbCount == 0) {
            Log.d(TAG, "Migrating ${listFile.name}...")
            val list = parseListJson(listFile)
            if (list.isNotEmpty()) {
                localDataSource.insertAllAnime(list)
                migratedCount += list.size
                Log.d(TAG, "Migrated ${list.size} anime entries")
            } else {
                Log.w(TAG, "Parsed list is empty from ${listFile.name}")
            }
        }

        if (ignoredFile != null) {
            Log.d(TAG, "Migrating ${ignoredFile.name}...")
            val ignored = parseIgnoredJson(ignoredFile)
            ignored.forEach { (id, eps) ->
                localDataSource.addIgnored(id, eps)
            }
            if (ignored.isNotEmpty()) Log.d(TAG, "Migrated ${ignored.size} ignored entries")
        }

        if (updatesFile != null) {
            Log.d(TAG, "Migrating ${updatesFile.name}...")
            val updates = parseUpdatesJson(updatesFile)
            if (updates.isNotEmpty()) {
                localDataSource.setUpdates(updates)
                Log.d(TAG, "Migrated ${updates.size} update entries")
            }
        }

        setMigrationCompleted()
        Log.d(TAG, "Migration completed. Total anime migrated: $migratedCount")

        exportDatabaseToPublicFolder()

        if (listFile != null && listFile.exists()) listFile.delete()
        if (ignoredFile != null && ignoredFile.exists()) ignoredFile.delete()
        if (updatesFile != null && updatesFile.exists()) updatesFile.delete()
        File(root, "$LIST_FILE_NAME.backup").delete()
        File(root, "$IGNORED_FILE_NAME.backup").delete()
        File(root, "$UPDATES_FILE_NAME.backup").delete()
    }

    private fun exportDatabaseToPublicFolder() {
        try {
            val internalDb = context.getDatabasePath("anime.db")
            val publicDbFile = File(getRoot(), "anime.db")
            val internalWal = context.getDatabasePath("anime.db-wal")
            val internalShm = context.getDatabasePath("anime.db-shm")
            if (internalDb.exists()) {
                internalDb.copyTo(publicDbFile, overwrite = true)
                Log.d(TAG, "Database exported to public folder: ${publicDbFile.absolutePath}")
            }
            if (internalWal.exists()) {
                internalWal.copyTo(File(getRoot(), "anime.db-wal"), overwrite = true)
            }
            if (internalShm.exists()) {
                internalShm.copyTo(File(getRoot(), "anime.db-shm"), overwrite = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export DB to public folder", e)
        }
    }
    
    private fun findFile(root: File, fileName: String): File? {
        val originalFile = File(root, fileName)
        if (originalFile.exists()) return originalFile
        
        val backupFile = File(root, "$fileName.backup")
        if (backupFile.exists()) {
            Log.d(TAG, "Found $fileName.backup instead of $fileName")
            return backupFile
        }
        
        return null
    }

    suspend fun isMigrationCompleted(): Boolean {
        return dataStore.data.first()[MIGRATION_COMPLETED] == true
    }

    private suspend fun setMigrationCompleted() {
        dataStore.edit { prefs ->
            prefs[MIGRATION_COMPLETED] = true
        }
    }

    private suspend fun resetMigrationFlag() {
        dataStore.edit { prefs ->
            prefs.remove(MIGRATION_COMPLETED)
        }
    }

    private fun getRoot(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ROOT)
    }

    private fun parseListJson(file: File): List<Anime> {
        val restoredList = mutableListOf<Anime>()
        try {
            val json = file.readText()
            if (json.isBlank()) return restoredList
            val arr = Json.parseToJsonElement(json).jsonArray
            for (element in arr) {
                try {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                    val title = obj["title"]?.jsonPrimitive?.content ?: "Unknown Title"
                    val episodes = obj["episodes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val rating = obj["rating"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val orderIndex = obj["orderIndex"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val dateAdded = obj["dateAdded"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
                    val imageFileName = obj["imageFileName"]?.jsonPrimitive?.content
                    val isFavorite = obj["isFavorite"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val tags = (obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()).toImmutableList()
                    val categoryType = obj["categoryType"]?.jsonPrimitive?.content ?: ""
                    val comment = obj["comment"]?.jsonPrimitive?.content ?: ""
                    restoredList.add(
                        Anime(
                            id = id,
                            title = title,
                            episodes = episodes,
                            rating = rating,
                            imageFileName = imageFileName,
                            orderIndex = orderIndex,
                            dateAdded = dateAdded,
                            isFavorite = isFavorite,
                            tags = tags,
                            categoryType = categoryType,
                            comment = comment
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse anime entry", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse list.json", e)
        }
        return restoredList.sortedBy { it.orderIndex }
    }

    private fun parseIgnoredJson(file: File): Map<String, Int> {
        return try {
            val obj = Json.parseToJsonElement(file.readText()).jsonObject
            obj.keys.associateWith { key -> obj[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0 }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ignored.json", e)
            emptyMap()
        }
    }

    private fun parseUpdatesJson(file: File): List<AnimeUpdate> {
        return try {
            val arr = Json.parseToJsonElement(file.readText()).jsonArray
            arr.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    AnimeUpdate(
                        animeId = obj["animeId"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        title = obj["title"]?.jsonPrimitive?.content ?: "",
                        currentEpisodes = obj["currentEpisodes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        newEpisodes = obj["newEpisodes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        source = obj["source"]?.jsonPrimitive?.content ?: ""
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse update entry", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse updates.json", e)
            emptyList()
        }
    }
}
