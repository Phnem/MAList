package com.example.myapplication.media.source

import com.example.myapplication.localplayer.domain.SkipKind
import kotlinx.serialization.Serializable
import java.net.URI

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

@Serializable
data class VetroSkipReference(
    val segments: List<VetroTimestamp>,
    val referenceDurationMs: Long,
    val origin: String,
)

@Serializable
@JvmInline
value class PlaybackCredentialRef(val value: String) {
    init {
        require(value.matches(Regex("(?:webdav|jellyfin|emby):[0-9a-f]{24}"))) {
            "Invalid playback credential reference"
        }
    }
}

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
    val skipReference: VetroSkipReference? = null,
    val isPreferred: Boolean = false,
    /** The configured owner/provider explicitly permits offline download. */
    val downloadAllowed: Boolean = false,
    /** Encrypted-store lookup key used to rehydrate sensitive headers in background work. */
    val credentialRef: PlaybackCredentialRef? = null,
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

internal fun VetroVideo.withoutPersistedSecrets(): VetroVideo = copy(
    headers = headers.filter { (key, value) ->
        isPersistableHeader(key, value)
    }
)

internal fun VetroVideo.isSafeForBackgroundPersistence(): Boolean {
    val unsupportedHeaders = headers.any { (key, value) ->
        !isPersistableHeader(key, value)
    }
    if (unsupportedHeaders && credentialRef == null) return false
    return !containsSensitiveQuery(url)
}

private fun isPersistableHeader(key: String, value: String): Boolean {
    if (PERSISTED_HEADER_ALLOWLIST.none { allowed -> key.equals(allowed, ignoreCase = true) }) {
        return false
    }
    if (key.equals("Referer", ignoreCase = true) || key.equals("Origin", ignoreCase = true)) {
        return !containsSensitiveQuery(value)
    }
    return true
}

private fun containsSensitiveQuery(url: String): Boolean {
    val query = runCatching { URI(url).rawQuery }.getOrNull().orEmpty()
    if (query.isBlank()) return false
    val keys = query.split('&').map { it.substringBefore('=').lowercase() }
    return keys.any { key -> SENSITIVE_QUERY_KEYS.any(key::contains) }
}

private val PERSISTED_HEADER_ALLOWLIST = setOf("User-Agent", "Accept", "Referer", "Origin")
private val SENSITIVE_QUERY_KEYS = setOf(
    "token", "signature", "sig", "key", "auth", "jwt", "policy", "expires", "x-amz-",
)

@Serializable
data class VetroHoster(
    val name: String,
    val url: String = "",
    val videos: List<VetroVideo>? = null,
    val skipReference: VetroSkipReference? = null,
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
