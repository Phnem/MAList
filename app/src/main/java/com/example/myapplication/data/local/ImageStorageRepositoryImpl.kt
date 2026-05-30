package com.example.myapplication.data.local

import android.content.Context
import android.net.Uri
import com.example.myapplication.data.repository.ImageStorageRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ImageStorageRepositoryImpl(
    private val context: Context,
    private val storagePaths: VetroStoragePaths,
    private val httpClient: HttpClient,
) : ImageStorageRepository {

    private fun getImgDir(): File = storagePaths.collectionDir

    override suspend fun saveImage(uri: String, animeId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = "img_${animeId}_${System.currentTimeMillis()}.jpg"
            val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
                ?: error("Failed to open URI: $uri")
            inputStream.use { input ->
                FileOutputStream(File(getImgDir(), name)).use { output ->
                    input.copyTo(output)
                }
            }
            name
        }
    }

    override fun getImageFilePath(fileName: String): String? {
        return resolveImageFile(fileName)?.absolutePath
    }

    override fun deleteImage(fileName: String): Boolean {
        val normalized = normalizeFileName(fileName)
        val inApp = File(getImgDir(), normalized)
        return inApp.exists() && inApp.delete()
    }

    override suspend fun saveImageFromUrl(url: String, animeId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!url.startsWith("http")) error("Invalid URL: $url")
            val name = "img_${animeId}_${System.currentTimeMillis()}.jpg"
            val bytes = httpClient.get(url).bodyAsBytes()
            File(getImgDir(), name).writeBytes(bytes)
            name
        }
    }

    private fun resolveImageFile(storedName: String): File? {
        if (storedName.isBlank()) return null
        val fileName = normalizeFileName(storedName)

        if (storedName.contains('/') || storedName.contains('\\')) {
            val absolute = File(storedName)
            if (absolute.exists() && absolute.isFile) {
                promoteToAppStorage(absolute, fileName)
                return File(getImgDir(), fileName).takeIf { it.exists() } ?: absolute
            }
        }

        val inApp = File(getImgDir(), fileName)
        if (inApp.exists()) return inApp

        val legacy = File(storagePaths.legacyCollectionDir(), fileName)
        if (legacy.exists() && legacy.isFile) {
            promoteToAppStorage(legacy, fileName)
            return File(getImgDir(), fileName).takeIf { it.exists() }
        }

        return null
    }

    private fun promoteToAppStorage(source: File, fileName: String) {
        val target = File(getImgDir(), fileName)
        if (target.exists()) return
        runCatching { source.copyTo(target, overwrite = false) }
    }

    private fun normalizeFileName(stored: String): String =
        stored.substringAfterLast('/').substringAfterLast('\\')
}
