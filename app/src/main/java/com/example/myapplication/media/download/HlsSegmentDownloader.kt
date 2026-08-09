package com.example.myapplication.media.download

import android.util.Log
import com.example.myapplication.media.source.SanitizeHeaders
import com.example.myapplication.media.source.VetroVideo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import kotlin.math.abs

/**
 * Android-native VOD HLS downloader.
 *
 * AniLiberty exposes media playlists made of MPEG-TS segments. Concatenated TS packets remain a
 * valid stream which Media3 can sniff and play locally; no external executable or shell process is
 * required. fMP4 playlists are also supported when they provide EXT-X-MAP.
 *
 * A single dropped connection must not cost the whole episode: CDNs close long-lived keep-alive
 * sessions mid-body (OkHttp surfaces that as `EOFException`), and an episode is hundreds of
 * segments. Each segment is therefore fetched into a staging file and retried on transport errors;
 * only a complete segment is appended to the output, so a half-read body can never corrupt it.
 */
class HlsSegmentDownloader(
    private val client: OkHttpClient,
) {
    fun download(
        video: VetroVideo,
        destination: File,
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): File {
        val playlist = resolveMediaPlaylist(video.url, video, depth = 0)
        if (playlist.segmentUrls.isEmpty()) throw IOException("HLS-плейлист не содержит сегментов")

        destination.parentFile?.mkdirs()
        val tmp = File(destination.parentFile, destination.nameWithoutExtension + ".part.mp4")
        val staging = File(destination.parentFile, destination.nameWithoutExtension + ".seg.tmp")
        runCatching { tmp.delete() }
        runCatching { staging.delete() }

        try {
            tmp.outputStream().buffered().use { output ->
                playlist.segmentUrls.forEachIndexed { index, segmentUrl ->
                    if (isCancelled()) throw HlsDownloadCancelledException()
                    fetchSegmentWithRetry(segmentUrl, video, staging, index, isCancelled)
                    staging.inputStream().use { it.copyTo(output) }
                    runCatching { staging.delete() }
                    onProgress(
                        (((index + 1L) * 100L) / playlist.segmentUrls.size)
                            .toInt()
                            .coerceIn(0, 99)
                    )
                }
            }
            if (!MediaFileValidator.isPlayableVideo(tmp)) {
                throw IOException("Сегменты HLS не образовали поддерживаемый видеофайл")
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("Не удалось заменить временный файл HLS")
            }
            if (!tmp.renameTo(destination)) {
                tmp.copyTo(destination, overwrite = true)
                if (!tmp.delete()) throw IOException("Не удалось завершить HLS-загрузку")
            }
            onProgress(100)
            return destination
        } catch (error: Exception) {
            runCatching { tmp.delete() }
            throw error
        } finally {
            runCatching { staging.delete() }
        }
    }

    /**
     * Retries transport failures (dropped connection, read timeout, truncated body). A refusal the
     * server means — bad status, non-video payload — is permanent and fails on the first answer.
     */
    private fun fetchSegmentWithRetry(
        segmentUrl: String,
        video: VetroVideo,
        staging: File,
        index: Int,
        isCancelled: () -> Boolean,
    ) {
        var lastError: IOException? = null
        repeat(SEGMENT_ATTEMPTS) { attempt ->
            if (isCancelled()) throw HlsDownloadCancelledException()
            try {
                fetchSegment(segmentUrl, video, staging, isCancelled)
                return
            } catch (cancelled: HlsDownloadCancelledException) {
                throw cancelled
            } catch (permanent: PermanentSegmentException) {
                throw permanent
            } catch (error: IOException) {
                lastError = error
                runCatching { staging.delete() }
                runCatching {
                    Log.w(
                        TAG,
                        "segment $index attempt ${attempt + 1}/$SEGMENT_ATTEMPTS failed: " +
                            (error.message ?: error.javaClass.simpleName),
                    )
                }
                if (attempt < SEGMENT_ATTEMPTS - 1) {
                    Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1))
                }
            }
        }
        val reason = lastError?.message ?: lastError?.javaClass?.simpleName.orEmpty()
        throw IOException("Сегмент $index не скачался за $SEGMENT_ATTEMPTS попыток: $reason", lastError)
    }

    private fun fetchSegment(
        segmentUrl: String,
        video: VetroVideo,
        staging: File,
        isCancelled: () -> Boolean,
    ) {
        client.newCall(request(segmentUrl, video)).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code in RETRYABLE_STATUSES) {
                    throw IOException("HTTP ${response.code} для HLS-сегмента")
                }
                throw PermanentSegmentException("HTTP ${response.code} для HLS-сегмента")
            }
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
                throw PermanentSegmentException("HLS-сегмент вернул $contentType")
            }
            val body = response.body ?: throw IOException("Пустой HLS-сегмент")
            val declaredLength = body.contentLength().takeIf { it > 0L }
            var written = 0L
            staging.outputStream().buffered().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (isCancelled()) throw HlsDownloadCancelledException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                    }
                }
            }
            // A short body is the silent form of the dropped-connection failure: OkHttp only raises
            // EOFException for chunked/gzip streams, a truncated Content-Length response just ends.
            if (declaredLength != null && written < declaredLength) {
                throw IOException("Сегмент оборван: $written из $declaredLength байт")
            }
        }
    }

    private fun resolveMediaPlaylist(
        url: String,
        video: VetroVideo,
        depth: Int,
    ): MediaPlaylist {
        if (depth > MAX_PLAYLIST_DEPTH) throw IOException("Слишком глубокий HLS-плейлист")
        val lines = fetchPlaylistWithRetry(url, video)
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }) {
            val variants = parseVariants(lines, url)
            val selected = variants.minWithOrNull(
                compareBy<Variant>(
                    { variant ->
                        val requested = video.resolution
                        val height = variant.height
                        if (requested == null || height == null) 0 else abs(requested - height)
                    },
                    { -it.bandwidth },
                )
            ) ?: throw IOException("Master HLS-плейлист не содержит вариантов")
            return resolveMediaPlaylist(selected.url, video, depth + 1)
        }

        if (lines.any { it.startsWith("#EXT-X-BYTERANGE", ignoreCase = true) }) {
            throw IOException("HLS byte-range пока не поддерживается")
        }
        val encrypted = lines.firstOrNull {
            it.startsWith("#EXT-X-KEY", ignoreCase = true) &&
                !it.contains("METHOD=NONE", ignoreCase = true)
        }
        if (encrypted != null) throw IOException("Зашифрованный HLS пока не поддерживается")

        val result = mutableListOf<String>()
        lines.firstOrNull { it.startsWith("#EXT-X-MAP", ignoreCase = true) }
            ?.let(::extractMapUri)
            ?.let { result += resolveUrl(url, it) }
        lines.asSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { resolveUrl(url, it) }
            .forEach(result::add)
        return MediaPlaylist(result)
    }

    private fun fetchPlaylistWithRetry(url: String, video: VetroVideo): List<String> {
        var lastError: IOException? = null
        repeat(PLAYLIST_ATTEMPTS) { attempt ->
            try {
                return fetchPlaylist(url, video)
            } catch (permanent: PermanentSegmentException) {
                throw IOException(permanent.message)
            } catch (error: IOException) {
                lastError = error
                if (attempt < PLAYLIST_ATTEMPTS - 1) {
                    Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1))
                }
            }
        }
        throw IOException(
            "HLS-плейлист не открылся: ${lastError?.message.orEmpty()}",
            lastError,
        )
    }

    private fun fetchPlaylist(url: String, video: VetroVideo): List<String> {
        client.newCall(request(url, video)).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code in RETRYABLE_STATUSES) {
                    throw IOException("HTTP ${response.code} для HLS-плейлиста")
                }
                throw PermanentSegmentException("HTTP ${response.code} для HLS-плейлиста")
            }
            val text = response.body?.string() ?: throw IOException("Пустой HLS-плейлист")
            if (!text.trimStart().startsWith("#EXTM3U")) {
                throw PermanentSegmentException("Источник вернул не HLS-плейлист")
            }
            return text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        }
    }

    private fun parseVariants(lines: List<String>, baseUrl: String): List<Variant> =
        buildList {
            lines.forEachIndexed { index, line ->
                if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) return@forEachIndexed
                val next = lines.drop(index + 1).firstOrNull { !it.startsWith("#") } ?: return@forEachIndexed
                val height = RESOLUTION.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val bandwidth = BANDWIDTH.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                add(Variant(resolveUrl(baseUrl, next), height, bandwidth))
            }
        }

    private fun request(url: String, video: VetroVideo): Request =
        Request.Builder().url(url).apply {
            SanitizeHeaders.sanitize(video.headers).forEach { (name, value) -> header(name, value) }
        }.build()

    private fun resolveUrl(base: String, child: String): String =
        runCatching { URI(base).resolve(child).toString() }
            .getOrElse { throw IOException("Некорректный URL HLS-сегмента", it) }

    private fun extractMapUri(line: String): String? =
        MAP_URI.find(line)?.groupValues?.getOrNull(1)

    private data class MediaPlaylist(val segmentUrls: List<String>)
    private data class Variant(val url: String, val height: Int?, val bandwidth: Long)

    /** The server answered and said no — retrying the same request cannot change the answer. */
    private class PermanentSegmentException(message: String) : IOException(message)

    class HlsDownloadCancelledException : IOException("HLS download cancelled")

    companion object {
        private const val TAG = "HlsSegmentDownloader"
        private const val MAX_PLAYLIST_DEPTH = 2
        private const val SEGMENT_ATTEMPTS = 4
        private const val PLAYLIST_ATTEMPTS = 3
        private const val RETRY_BACKOFF_MS = 800L
        private val RETRYABLE_STATUSES = setOf(408, 425, 429, 500, 502, 503, 504)
        private val MAP_URI = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
        private val RESOLUTION = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
        private val BANDWIDTH = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
    }
}
