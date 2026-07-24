package com.example.myapplication.media.runtime

import android.util.Log
import com.example.myapplication.media.source.SanitizeHeaders
import com.example.myapplication.media.source.VetroVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

class FfmpegRemuxException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Direct HLS/DASH remux via ffmpeg stream-copy (no re-encode).
 * Cancel with [cancel] → [Process.destroy].
 */
class FfmpegRemuxRunner(
    private val binaryProvider: FfmpegBinaryProvider,
) {
    private val activeProcess = AtomicReference<Process?>(null)

    fun cancel() {
        activeProcess.getAndSet(null)?.destroy()
    }

    /**
     * @param durationSec optional known duration for progress % (stderr time= parsing).
     * @param onProgress 0..100
     */
    suspend fun remux(
        video: VetroVideo,
        outFile: File,
        durationSec: Int? = null,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val ffmpeg = binaryProvider.resolveExecutable()
            ?: throw FfmpegRemuxException("ffmpeg binary unavailable")

        outFile.parentFile?.mkdirs()
        val tmp = File(outFile.parentFile, outFile.nameWithoutExtension + ".part.mp4")
        if (tmp.exists()) tmp.delete()

        val headerStr = SanitizeHeaders.toFfmpegHeaderString(video.headers)
        val cmd = mutableListOf(
            ffmpeg.absolutePath,
            "-y",
            "-loglevel", "info",
            "-user_agent", video.headers["User-Agent"]
                ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36",
        )
        if (headerStr.isNotBlank()) {
            cmd += listOf("-headers", headerStr)
        }
        cmd += listOf(
            "-i", video.url,
            "-c", "copy",
            "-bsf:a", "aac_adtstoasc",
            "-movflags", "+faststart",
            tmp.absolutePath,
        )

        Log.i(TAG, "Starting remux → ${outFile.name}")
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        activeProcess.set(process)

        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (!coroutineContext.isActive) {
                    process.destroy()
                    throw FfmpegRemuxException("Cancelled")
                }
                val t = line ?: continue
                parseTimeSeconds(t)?.let { sec ->
                    val total = durationSec?.takeIf { it > 0 } ?: return@let
                    val pct = ((sec / total.toDouble()) * 100).toInt().coerceIn(0, 99)
                    onProgress(pct)
                }
            }
            val code = process.waitFor()
            if (code != 0) {
                throw FfmpegRemuxException("ffmpeg exited with code $code")
            }
            if (!tmp.exists() || tmp.length() < 1024) {
                throw FfmpegRemuxException("ffmpeg produced empty output")
            }
            if (outFile.exists()) outFile.delete()
            if (!tmp.renameTo(outFile)) {
                tmp.copyTo(outFile, overwrite = true)
                tmp.delete()
            }
            onProgress(100)
            outFile
        } finally {
            activeProcess.compareAndSet(process, null)
            runCatching { if (process.isAlive) process.destroy() }
            if (tmp.exists() && tmp.absolutePath != outFile.absolutePath) {
                runCatching { tmp.delete() }
            }
        }
    }

    private fun parseTimeSeconds(line: String): Double? {
        // time=00:12:34.56
        val m = TIME_RE.find(line) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].toDouble()
        return h * 3600 + min * 60 + sec
    }

    companion object {
        private const val TAG = "FfmpegRemuxRunner"
        private val TIME_RE = Regex("""time=(\d{2}):(\d{2}):(\d{2}\.\d+)""")
    }
}
