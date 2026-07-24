package com.example.myapplication.media.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.myapplication.media.source.SanitizeHeaders
import com.example.myapplication.media.source.VetroVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.net.URI

/**
 * Downloads a ranked list of resolved media candidates.
 *
 * Every candidate is written to a temporary file and validated as an actual MP4 before it can
 * replace an existing episode. This prevents CDN error pages and 1x1 PNG placeholders from being
 * treated as downloaded episodes.
 */
class MediaDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val okHttpClient: OkHttpClient by inject()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureNotificationChannel()
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Vetro")
            .setContentText("Загрузка эпизода…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        val notificationId = stableNotificationId(jobId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val label = inputData.getString(KEY_LABEL) ?: "Auto"
        val outDir = inputData.getString(KEY_OUT_DIR) ?: return@withContext Result.failure()
        val episode = inputData.getInt(KEY_EPISODE, 0).takeIf { it > 0 }
            ?: return@withContext Result.failure()
        val legacyVideo = VetroVideo(
            url = url,
            label = label,
            resolution = resolutionFromLabel(label),
            headers = parseHeaders(inputData.getString(KEY_HEADERS_JSON).orEmpty()),
            isPreferred = true,
        )
        val candidates = parseCandidates(inputData.getString(KEY_CANDIDATES_JSON).orEmpty())
            .ifEmpty { listOf(legacyVideo) }
            .distinctBy { it.url }

        val outputDirectory = File(outDir)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            return@withContext fail(jobId, "Не удалось создать папку загрузки")
        }

        try {
            setForeground(getForegroundInfo())
        } catch (error: Exception) {
            Log.w(TAG, "Foreground promotion rejected, continuing safely", error)
        }

        val outFile = File(outputDirectory, "E${episode.toString().padStart(3, '0')}.mp4")
        if (outFile.exists() && !MediaFileValidator.isPlayableMp4(outFile)) {
            runCatching { outFile.delete() }
        }
        MediaJobBus.update(MediaJobProgress(jobId, "downloading", 0))

        try {
            val failures = mutableListOf<String>()
            var downloaded = false
            candidates.forEachIndexed { index, video ->
                if (downloaded) return@forEachIndexed
                checkCancelled(jobId)
                val candidateFile = candidateFile(outFile)
                cleanupCandidate(candidateFile)
                try {
                    Log.i(
                        TAG,
                        "Trying ${index + 1}/${candidates.size}: ${video.label} " +
                            "${if (video.isHlsOrDash) "HLS/DASH" else "progressive"} " +
                            "host=${safeHost(video.url)}",
                    )
                    if (video.isHlsOrDash) {
                        if (!video.url.contains(".m3u8", ignoreCase = true)) {
                            throw IOException("Компонент HLS-загрузки недоступен")
                        }
                        downloadSeekableHls(video, candidateFile) { percent ->
                            if (MediaJobBus.isCancelRequested(jobId)) {
                                throw DownloadCancelledException()
                            } else {
                                MediaJobBus.update(
                                    MediaJobProgress(jobId, "downloading", percent)
                                )
                            }
                        }
                    } else {
                        downloadProgressive(video, candidateFile, jobId)
                    }
                    if (!MediaFileValidator.isPlayableVideo(candidateFile)) {
                        throw IOException("Источник вернул не видео")
                    }
                    installCandidate(candidateFile, outFile)
                    downloaded = true
                    Log.i(TAG, "Download completed with host=${safeHost(video.url)}")
                } catch (cancelled: DownloadCancelledException) {
                    cleanupCandidate(candidateFile)
                    throw cancelled
                } catch (error: Exception) {
                    cleanupCandidate(candidateFile)
                    val reason = error.message ?: error.javaClass.simpleName
                    failures += "${safeHost(video.url)}: $reason"
                    Log.w(TAG, "Candidate failed: host=${safeHost(video.url)} reason=$reason")
                }
            }

            checkCancelled(jobId)
            if (!downloaded || !MediaFileValidator.isPlayableMp4(outFile)) {
                throw IOException(
                    failures.take(3).joinToString(
                        prefix = "Ни один источник не отдал видео: ",
                        separator = "; ",
                    )
                )
            }
            MediaJobBus.clearCancel(jobId)
            MediaJobBus.update(MediaJobProgress(jobId, "success", 100, outFile.absolutePath))
            Result.success(workDataOf(KEY_FILE_PATH to outFile.absolutePath))
        } catch (_: DownloadCancelledException) {
            cleanupCandidate(candidateFile(outFile))
            MediaJobBus.clearCancel(jobId)
            MediaJobBus.update(MediaJobProgress(jobId, "cancelled"))
            Result.failure()
        } catch (error: Exception) {
            Log.e(TAG, "Download failed for episode $episode", error)
            cleanupCandidate(candidateFile(outFile))
            if (!MediaFileValidator.isPlayableMp4(outFile)) {
                runCatching { outFile.delete() }
            }
            fail(jobId, error.message ?: "Ошибка загрузки")
        }
    }

    private fun downloadHls(
        video: VetroVideo,
        destination: File,
        onProgress: (Int) -> Unit,
    ) {
        HlsSegmentDownloader(okHttpClient).download(
            video = video,
            destination = destination,
            onProgress = onProgress,
            isCancelled = {
                val cancelRequested =
                    MediaJobBus.isCancelRequested(inputData.getString(KEY_JOB_ID).orEmpty()) || isStopped
                if (cancelRequested) throw DownloadCancelledException()
                false
            },
        )
    }

    private fun downloadSeekableHls(
        video: VetroVideo,
        destination: File,
        onProgress: (Int) -> Unit,
    ) {
        val transport = File(
            destination.parentFile,
            destination.nameWithoutExtension + ".transport.ts",
        )
        runCatching { transport.delete() }
        val cancelled = {
            val requested =
                MediaJobBus.isCancelRequested(inputData.getString(KEY_JOB_ID).orEmpty()) || isStopped
            if (requested) throw DownloadCancelledException()
            false
        }
        try {
            HlsSegmentDownloader(okHttpClient).download(
                video = video,
                destination = transport,
                onProgress = { onProgress((it * 9) / 10) },
                isCancelled = cancelled,
            )
            onProgress(92)
            NativeMediaRemuxer.remuxToMp4(
                source = transport,
                destination = destination,
                isCancelled = cancelled,
            )
            onProgress(100)
        } finally {
            runCatching { transport.delete() }
        }
    }

    private fun downloadProgressive(
        video: VetroVideo,
        destination: File,
        jobId: String,
    ) {
        val request = Request.Builder().url(video.url).apply {
            SanitizeHeaders.sanitize(video.headers).forEach { (name, value) ->
                header(name, value)
            }
            if (safeHost(video.url).endsWith("jut.su")) {
                header("Range", "bytes=0-")
            }
        }.build()
        val tmp = File(destination.parentFile, destination.nameWithoutExtension + ".part.mp4")
        runCatching { tmp.delete() }

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} при загрузке")
                }
                val body = response.body ?: throw IOException("Пустой ответ сервера")
                val contentType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()
                if (
                    contentType.startsWith("image/") ||
                    contentType.startsWith("text/") ||
                    contentType.contains("json")
                ) {
                    throw IOException("Сервер вернул $contentType вместо видео")
                }
                val total = body.contentLength().takeIf { it > 0L }
                if (total != null && total < MIN_PLAUSIBLE_VIDEO_BYTES) {
                    throw IOException("Ответ слишком мал для видео: $total байт")
                }
                body.byteStream().use { input ->
                    tmp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            checkCancelled(jobId)
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            total?.let {
                                val percent = ((copied * 100L) / it).toInt().coerceIn(0, 99)
                                MediaJobBus.update(
                                    MediaJobProgress(jobId, "downloading", percent)
                                )
                            }
                        }
                    }
                }
            }
            if (!MediaFileValidator.isPlayableMp4(tmp)) {
                throw IOException("Загруженный ответ не является MP4 (${tmp.length()} байт)")
            }
            if (!tmp.renameTo(destination)) {
                tmp.copyTo(destination, overwrite = true)
                if (!tmp.delete()) Log.w(TAG, "Could not delete ${tmp.name}")
            }
        } catch (error: Exception) {
            runCatching { tmp.delete() }
            throw error
        }
    }

    private fun installCandidate(candidate: File, outFile: File) {
        if (outFile.exists() && !outFile.delete()) {
            throw IOException("Не удалось заменить старый файл")
        }
        if (!candidate.renameTo(outFile)) {
            candidate.copyTo(outFile, overwrite = true)
            if (!candidate.delete()) Log.w(TAG, "Could not delete ${candidate.name}")
        }
    }

    private fun candidateFile(outFile: File): File =
        File(outFile.parentFile, outFile.nameWithoutExtension + ".candidate.mp4")

    private fun cleanupCandidate(candidate: File) {
        runCatching { candidate.delete() }
        runCatching {
            File(candidate.parentFile, candidate.nameWithoutExtension + ".part.mp4").delete()
        }
    }

    private fun checkCancelled(jobId: String) {
        if (MediaJobBus.isCancelRequested(jobId) || isStopped) {
            throw DownloadCancelledException()
        }
    }

    private fun fail(jobId: String, message: String): Result {
        MediaJobBus.update(MediaJobProgress(jobId, "failed", error = message))
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Загрузки видео",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Прогресс загрузки эпизодов"
                setShowBadge(false)
            }
        )
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
        }.getOrElse { emptyMap() }
    }

    private fun parseCandidates(raw: String): List<VetroVideo> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<VetroVideo>>(raw)
        }.onFailure {
            Log.w(TAG, "Could not decode media candidates: ${it.message}")
        }.getOrElse { emptyList() }
    }

    private fun resolutionFromLabel(label: String): Int? =
        Regex("""(?i)\b(\d{3,4})p\b""")
            .find(label)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun stableNotificationId(jobId: String): Int =
        (jobId.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    private fun safeHost(url: String): String =
        runCatching { URI(url).host.orEmpty() }.getOrDefault("unknown")

    private class DownloadCancelledException : Exception()

    companion object {
        private const val TAG = "MediaDownloadWorker"
        private const val CHANNEL_ID = "vetro_media_download"
        private const val MIN_PLAUSIBLE_VIDEO_BYTES = 128 * 1024L

        const val KEY_JOB_ID = "job_id"
        const val KEY_URL = "url"
        const val KEY_LABEL = "label"
        const val KEY_OUT_DIR = "out_dir"
        const val KEY_EPISODE = "episode"
        const val KEY_ANIME_ID = "anime_id"
        const val KEY_QUALITY = "quality"
        const val KEY_DURATION_SEC = "duration_sec"
        const val KEY_HEADERS_JSON = "headers_json"
        const val KEY_CANDIDATES_JSON = "candidates_json"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_ERROR = "error"
        const val WORK_NAME = "vetro_media_download"
    }
}
