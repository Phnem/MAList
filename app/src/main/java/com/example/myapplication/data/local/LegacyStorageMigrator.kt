package com.example.myapplication.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

enum class MigrationResult {
    Skipped,
    Completed,
    NoAccess,
}

class LegacyStorageMigrator(
    private val context: Context,
    private val storagePaths: VetroStoragePaths,
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun isPendingMigration(): Boolean {
        if (!isMigrationCompleted()) return true
        return legacyCollectionHasUncopiedFiles(storagePaths.vetroRoot)
    }

    suspend fun migrateIfNeeded(): MigrationResult = withContext(Dispatchers.IO) {
        if (isMigrationCompleted() && legacyCollectionHasUncopiedFiles(storagePaths.vetroRoot)) {
            Log.w(TAG, "Migration flag set but legacy images remain — resetting for retry")
            resetMigrationFlag()
        }

        val migrationResult = if (isMigrationCompleted()) {
            MigrationResult.Skipped
        } else {
            runInitialMigration()
        }

        syncMissingCollectionImages()
        migrationResult
    }

    suspend fun syncMissingCollectionImages(): Int = withContext(Dispatchers.IO) {
        copyLegacyCollection(storagePaths.legacyPublicRoot(), storagePaths.vetroRoot)
    }

    private suspend fun runInitialMigration(): MigrationResult {
        val legacyRoot = storagePaths.legacyPublicRoot()
        val legacyMalRoot = storagePaths.legacyMyAnimeListRoot()
        val vetroRoot = storagePaths.vetroRoot

        var migratedAny = false
        var legacyExistsButInaccessible = false

        if (legacyMalRoot.exists()) {
            if (legacyMalRoot.canRead()) {
                migrateMyAnimeListFolder(legacyMalRoot, vetroRoot)
                migratedAny = true
            } else {
                legacyExistsButInaccessible = true
            }
        }

        if (legacyRoot.exists()) {
            if (legacyRoot.canRead()) {
                copyLegacyCollection(legacyRoot, vetroRoot)
                copyLegacyJsonFiles(legacyRoot, vetroRoot)
                migrateLegacyDatabaseIfNeeded(legacyRoot)
                migratedAny = true
            } else {
                legacyExistsButInaccessible = true
            }
        }

        if (legacyExistsButInaccessible) {
            Log.w(TAG, "Legacy storage exists but is not readable; will retry on next launch")
            return MigrationResult.NoAccess
        }

        if (legacyCollectionHasUncopiedFiles(vetroRoot)) {
            Log.w(TAG, "Legacy collection still has uncopied files; will retry on next launch")
            return MigrationResult.NoAccess
        }

        if (migratedAny || (!legacyRoot.exists() && !legacyMalRoot.exists())) {
            setMigrationCompleted()
            Log.d(TAG, "Legacy public storage migration completed")
        } else {
            setMigrationCompleted()
        }
        return MigrationResult.Completed
    }

    private fun migrateMyAnimeListFolder(oldFolder: File, newFolder: File) {
        if (!oldFolder.isDirectory) return
        if (!newFolder.exists()) newFolder.mkdirs()

        oldFolder.listFiles()?.forEach { fileOrDir ->
            val targetFile = File(newFolder, fileOrDir.name)
            if (targetFile.exists()) targetFile.deleteRecursively()

            if (fileOrDir.isFile && fileOrDir.extension == "json") {
                val fixedJsonContent = fileOrDir.readText().replace(
                    "/$OLD_ROOT_FOLDER/",
                    "/$NEW_ROOT_FOLDER/",
                )
                targetFile.writeText(fixedJsonContent)
                fileOrDir.delete()
            } else {
                val isMoved = fileOrDir.renameTo(targetFile)
                if (!isMoved) {
                    fileOrDir.copyRecursively(targetFile, overwrite = true)
                    fileOrDir.deleteRecursively()
                }
            }
        }

        oldFolder.deleteRecursively()
        Log.d(TAG, "Legacy MyAnimeList folder merged into app storage")
    }

    private fun legacyCollectionHasUncopiedFiles(vetroRoot: File): Boolean {
        val legacyCollection = storagePaths.legacyCollectionDir()
        if (!legacyCollection.exists() || !legacyCollection.canRead()) return false
        val targetCollection = File(vetroRoot, COLLECTION_DIR)
        return legacyCollection.listFiles()?.any { source ->
            source.isFile && !File(targetCollection, source.name).exists()
        } == true
    }

    private fun copyLegacyCollection(legacyRoot: File, vetroRoot: File): Int {
        val legacyCollection = File(legacyRoot, COLLECTION_DIR)
        if (!legacyCollection.exists() || !legacyCollection.isDirectory || !legacyCollection.canRead()) {
            return 0
        }

        val targetCollection = File(vetroRoot, COLLECTION_DIR).also { it.mkdirs() }
        var copied = 0
        legacyCollection.listFiles()?.filter { it.isFile }?.forEach { source ->
            val target = File(targetCollection, source.name)
            if (!target.exists()) {
                runCatching {
                    source.copyTo(target, overwrite = false)
                    copied++
                }.onFailure { error ->
                    Log.w(TAG, "Failed to copy ${source.name} from legacy collection", error)
                }
            }
        }
        if (copied > 0) {
            Log.d(TAG, "Copied $copied legacy collection image(s) into app storage")
        }
        return copied
    }

    private fun copyLegacyJsonFiles(legacyRoot: File, vetroRoot: File) {
        JSON_FILES.flatMap { name ->
            listOf(name, "$name.backup").map { File(legacyRoot, it) }
        }.filter { it.exists() && it.isFile }.forEach { source ->
            val target = File(vetroRoot, source.name)
            if (!target.exists()) {
                source.copyTo(target, overwrite = false)
            }
        }
    }

    private fun migrateLegacyDatabaseIfNeeded(legacyRoot: File) {
        val legacyDb = File(legacyRoot, DB_NAME)
        if (!legacyDb.exists()) return

        val targetDbFile = context.getDatabasePath(DB_NAME)
        if (targetDbFile.exists() && targetDbFile.length() > 0) return

        targetDbFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val targetWal = File(targetDbFile.parentFile, "$DB_NAME-wal")
        val targetShm = File(targetDbFile.parentFile, "$DB_NAME-shm")
        if (targetWal.exists()) targetWal.delete()
        if (targetShm.exists()) targetShm.delete()

        legacyDb.copyTo(targetDbFile, overwrite = true)

        val legacyWal = File(legacyRoot, "$DB_NAME-wal")
        if (legacyWal.exists()) legacyWal.copyTo(targetWal, overwrite = true)
        val legacyShm = File(legacyRoot, "$DB_NAME-shm")
        if (legacyShm.exists()) legacyShm.copyTo(targetShm, overwrite = true)

        Log.d(TAG, "Legacy anime.db imported into internal storage")
    }

    private suspend fun isMigrationCompleted(): Boolean =
        dataStore.data.first()[LEGACY_PUBLIC_STORAGE_MIGRATED] == true

    private suspend fun setMigrationCompleted() {
        dataStore.edit { prefs ->
            prefs[LEGACY_PUBLIC_STORAGE_MIGRATED] = true
        }
    }

    private suspend fun resetMigrationFlag() {
        dataStore.edit { prefs ->
            prefs.remove(LEGACY_PUBLIC_STORAGE_MIGRATED)
        }
    }

    private companion object {
        private const val TAG = "LegacyStorageMigrator"
        private const val OLD_ROOT_FOLDER = "MyAnimeList"
        private const val NEW_ROOT_FOLDER = "Vetro"
        private const val COLLECTION_DIR = "collection"
        private const val DB_NAME = "anime.db"
        private val JSON_FILES = listOf("list.json", "ignored.json", "updates.json")
        private val LEGACY_PUBLIC_STORAGE_MIGRATED =
            booleanPreferencesKey("legacy_public_storage_migrated")
    }
}
