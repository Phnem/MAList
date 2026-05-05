package com.example.myapplication.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresses user-selected screenshots for Visual Search — caps memory use before trace.moe/Gemini.
 */
suspend fun compressVisualSearchImage(context: Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (srcW, srcH) = resolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                throw IllegalArgumentException("Invalid image dimensions")
            }
            Pair(opts.outWidth, opts.outHeight)
        } ?: throw IllegalArgumentException("Cannot open image stream")

        val sample = calculateSampleSizeForMaxLongEdge(srcW, srcH, MAX_LONG_EDGE_PX)

        val decoded = resolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeStream(stream, null, opts)
                ?: throw IllegalArgumentException("Failed to decode image")
        } ?: throw IllegalArgumentException("Cannot open image stream")

        var bitmap: Bitmap = decoded
        try {
            bitmap = scaledToMaxLongEdge(decoded, MAX_LONG_EDGE_PX)
            if (bitmap !== decoded) decoded.recycle()

            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IllegalArgumentException("Failed to encode JPEG")
                }
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

private fun calculateSampleSizeForMaxLongEdge(width: Int, height: Int, maxLongEdge: Int): Int {
    val longEdge = max(width, height)
    if (longEdge <= maxLongEdge) return 1
    var sample = 1
    while (longEdge / sample > maxLongEdge) {
        sample *= 2
    }
    return sample
}

private fun scaledToMaxLongEdge(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val longEdge = max(w, h)
    if (longEdge <= maxLongEdge) return bitmap
    val scale = maxLongEdge.toFloat() / longEdge
    val nw = (w * scale).roundToInt().coerceAtLeast(1)
    val nh = (h * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
}

private const val MAX_LONG_EDGE_PX = 1024
private const val JPEG_QUALITY = 80
