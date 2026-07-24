package com.example.myapplication.media.download

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
        runCatching { tmp.delete() }

        try {
            tmp.outputStream().buffered().use { output ->
                playlist.segmentUrls.forEachIndexed { index, segmentUrl ->
                    if (isCancelled()) throw HlsDownloadCancelledException()
                    val request = request(segmentUrl, video)
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code} для HLS-сегмента")
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
                            throw IOException("HLS-сегмент вернул $contentType")
                        }
                        val body = response.body ?: throw IOException("Пустой HLS-сегмент")
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                if (isCancelled()) throw HlsDownloadCancelledException()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
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
        }
    }

    private fun resolveMediaPlaylist(
        url: String,
        video: VetroVideo,
        depth: Int,
    ): MediaPlaylist {
        if (depth > MAX_PLAYLIST_DEPTH) throw IOException("Слишком глубокий HLS-плейлист")
        val lines = fetchPlaylist(url, video)
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

    private fun fetchPlaylist(url: String, video: VetroVideo): List<String> {
        client.newCall(request(url, video)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} для HLS-плейлиста")
            val text = response.body?.string() ?: throw IOException("Пустой HLS-плейлист")
            if (!text.trimStart().startsWith("#EXTM3U")) {
                throw IOException("Источник вернул не HLS-плейлист")
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

    class HlsDownloadCancelledException : IOException("HLS download cancelled")

    companion object {
        private const val MAX_PLAYLIST_DEPTH = 2
        private val MAP_URI = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
        private val RESOLUTION = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
        private val BANDWIDTH = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
    }
}
