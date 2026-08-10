package com.example.myapplication.media.source

import android.content.Context
import java.net.URI

/**
 * Safe direct-media fallback.
 *
 * Arbitrary pages used to be passed to yt-dlp inside Chaquopy. Apart from being unreliable for
 * many anime sites, that path loads CPython's subprocess module and can destabilize the whole
 * Android process. Page extraction now belongs to native site-specific sources; this fallback
 * accepts only an already-direct HTTP media URL.
 */
class UrlSource(
    @Suppress("UNUSED_PARAMETER") context: Context? = null,
) {
    fun canResolveDirect(url: String): Boolean {
        val parsed = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (parsed.scheme !in setOf("http", "https")) return false
        val value = url.lowercase()
        return DIRECT_MEDIA_MARKERS.any(value::contains)
    }

    suspend fun resolveFromWebUrl(
        webUrl: String,
        preferredHeight: Int = 720,
        downloadAllowed: Boolean = false,
    ): List<VetroHoster> {
        if (!canResolveDirect(webUrl)) return emptyList()
        val resolution = Regex("""(?i)(?:^|[_./-])(\d{3,4})p?(?:[_./?-]|$)""")
            .find(webUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val label = resolution?.let { "${it}p" } ?: "Auto"
        return listOf(
            VetroHoster(
                name = "Direct media",
                url = webUrl,
                videos = listOf(
                    VetroVideo(
                        url = webUrl,
                        label = label,
                        resolution = resolution,
                        headers = mapOf("User-Agent" to VetroHttpSource.DEFAULT_UA),
                        isPreferred = resolution == null || resolution <= preferredHeight,
                        downloadAllowed = downloadAllowed,
                    )
                ),
            )
        )
    }

    private companion object {
        val DIRECT_MEDIA_MARKERS = listOf(".m3u8", ".mpd", ".mp4", ".m4v", ".webm")
    }
}
