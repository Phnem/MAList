package com.example.myapplication.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ImageCompressionMigrator(
    private val db: AnimeDatabase,
    private val storagePaths: VetroStoragePaths
) {
    suspend fun compressExistingImages() = withContext(Dispatchers.IO) {
        val allAnime = db.animeQueries.getAllAnime().executeAsList()
        val collectionDir = storagePaths.collectionDir

        if (!collectionDir.exists()) return@withContext

        var compressedCount = 0

        for (anime in allAnime) {
            val imagePath = anime.imagePath ?: continue
            
            // Skip cloud images and http URLs
            if (imagePath.startsWith("collection-attachment://") || imagePath.startsWith("http")) {
                continue
            }

            // Also check the file name in DB. 
            // In DB it might just be the filename or an absolute path (rare but possible).
            val normalizedName = imagePath.substringAfterLast('/').substringAfterLast('\\')
            var file = File(collectionDir, normalizedName)

            if (!file.exists()) {
                // If the DB points to a file that doesn't exist, maybe it was deleted 
                // but the user restored the original .jpg or .png from the legacy folder!
                val baseName = normalizedName.substringBeforeLast("_c.webp").substringBeforeLast('.')
                val possibleJpg = File(collectionDir, "$baseName.jpg")
                val possiblePng = File(collectionDir, "$baseName.png")
                val possibleJpeg = File(collectionDir, "$baseName.jpeg")
                file = when {
                    possibleJpg.exists() -> possibleJpg
                    possiblePng.exists() -> possiblePng
                    possibleJpeg.exists() -> possibleJpeg
                    else -> continue // File truly doesn't exist
                }
            }
            
            // If it's already a webp and is small enough, skip.
            // But if it's large, we might still want to downscale and compress it.
            // Let's compress if file size > 300KB OR if it's not .webp.
            val isWebp = file.extension.equals("webp", ignoreCase = true)
            if (isWebp && file.length() < 300 * 1024) {
                continue
            }

            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                
                // Downscale if needed
                val maxDim = 1200f
                val width = bitmap.width
                val height = bitmap.height
                val scale = if (width > maxDim || height > maxDim) {
                    maxDim / Math.max(width, height)
                } else 1f
                
                val scaledBitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (width * scale).toInt(),
                        (height * scale).toInt(),
                        true
                    )
                } else bitmap

                // Write compressed to a new temp file
                val outStream = java.io.ByteArrayOutputStream()
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                scaledBitmap.compress(format, 80, outStream)
                
                val compressedBytes = outStream.toByteArray()
                
                // If the compressed size is significantly better, or if we are changing extension
                val newFileName = normalizedName.substringBeforeLast('.') + "_c.webp"
                val newFile = File(collectionDir, newFileName)
                
                FileOutputStream(newFile).use { it.write(compressedBytes) }
                
                // Update database
                db.animeQueries.updateAnimeImagePath(imagePath = newFileName, id = anime.id)
                db.animeQueries.markPendingSync(anime.id)
                
                // Delete old file
                if (file.absolutePath != newFile.absolutePath) {
                    file.delete()
                }

                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                bitmap.recycle()
                
                compressedCount++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        Log.d("ImageCompressionMigrator", "Compressed $compressedCount legacy images")
    }
}
