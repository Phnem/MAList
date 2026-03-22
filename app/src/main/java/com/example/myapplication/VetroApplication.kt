package com.example.myapplication

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.myapplication.di.appModule
import com.example.myapplication.di.databaseModule
import com.example.myapplication.di.viewModelModule
import com.example.myapplication.network.di.coreNetworkModule
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File

class VetroApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        runVetroDbMigrationIfNeeded()
        startKoin {
            androidContext(this@VetroApplication)
            modules(
                coreNetworkModule,
                appModule,
                databaseModule,
                viewModelModule
            )
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .build()
    }

    /**
     * Миграция базы из папки Vetro.
     * Ожидает выдачи прав и перезаписывает пустую БД при первом удачном доступе.
     */
    private fun runVetroDbMigrationIfNeeded() {
        try {
            val prefs = getSharedPreferences("vetro_migration", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_db_migrated", false)) return

            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val vetroDir = File(documentsDir, "Vetro")
            val vetroDbFile = File(vetroDir, "anime.db")
            if (!vetroDbFile.exists()) return

            val targetDbFile = getDatabasePath("anime.db")
            targetDbFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            val targetWal = File(targetDbFile.parentFile, "anime.db-wal")
            val targetShm = File(targetDbFile.parentFile, "anime.db-shm")
            if (targetWal.exists()) targetWal.delete()
            if (targetShm.exists()) targetShm.delete()

            vetroDbFile.copyTo(targetDbFile, overwrite = true)

            val vetroWal = File(vetroDir, "anime.db-wal")
            if (vetroWal.exists()) vetroWal.copyTo(targetWal, overwrite = true)
            val vetroShm = File(vetroDir, "anime.db-shm")
            if (vetroShm.exists()) vetroShm.copyTo(targetShm, overwrite = true)

            prefs.edit().putBoolean("is_db_migrated", true).apply()
            Log.d("Migration", "Успешно мигрировали базу данных из Vetro")
        } catch (e: Exception) {
            Log.e("Migration", "Ошибка при миграции из папки Vetro: ${e.message}")
        }
    }

}
