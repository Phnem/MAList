package com.example.myapplication.media.source

import com.example.myapplication.localplayer.domain.SkipKind
import kotlinx.serialization.Serializable

@Serializable
data class VetroSubtitleTrack(
    val url: String,
    val lang: String,
    val mimeType: String = "text/vtt",
)

@Serializable
data class VetroTimestamp(
    val startMs: Long,
    val endMs: Long,
    val kind: SkipKind,
)

/**
 * Resolved playable stream. Independent of Animetail's Video model
 * (no mutable status, no okhttp Headers, no MPV-only fields).
 */
@Serializable
data class VetroVideo(
    val url: String,
    val label: String,
    val sourceName: String? = null,
    val resolution: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<VetroSubtitleTrack> = emptyList(),
    val audioTracks: List<VetroSubtitleTrack> = emptyList(),
    val timestamps: List<VetroTimestamp> = emptyList(),
    val isPreferred: Boolean = false,
    val resolvedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "VetroVideo.url must be http(s), got: $url"
        }
    }

    val isHlsOrDash: Boolean
        get() {
            val lower = url.lowercase()
            return lower.contains(".m3u8") || lower.contains(".mpd") ||
                lower.contains("m3u8") || lower.contains("mpd")
        }
}

@Serializable
data class VetroHoster(
    val name: String,
    val url: String = "",
    val videos: List<VetroVideo>? = null,
    val lazy: Boolean = false,
)

object SanitizeHeaders {
    /** Drop or scrub CR/LF so headers cannot be used for injection. */
    fun sanitize(headers: Map<String, String>): Map<String, String> =
        headers.mapNotNull { (k, v) ->
            val key = k.trim()
            if (key.isEmpty() || key.contains('\r') || key.contains('\n')) return@mapNotNull null
            val value = v.replace("\r", "").replace("\n", " ").trim()
            if (value.isEmpty()) null else key to value
        }.toMap()

    /** ffmpeg `-headers` format: `Key: Value\r\nKey2: Value2\r\n` */
    fun toFfmpegHeaderString(headers: Map<String, String>): String =
        sanitize(headers).entries.joinToString("") { (k, v) -> "$k: $v\r\n" }
}
