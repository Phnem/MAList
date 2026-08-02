package com.example.myapplication.media.source

import com.example.myapplication.localplayer.domain.SkipKind
import java.net.URI
import java.util.Base64
import org.jsoup.Jsoup

internal data class ParsedJutSuSource(
    val url: String,
    val label: String,
    val resolution: Int?,
)

internal data class ParsedJutSuEpisodePage(
    val sources: List<ParsedJutSuSource>,
    val timestamps: List<VetroTimestamp>,
    val durationMs: Long,
)

/**
 * Pure parser for the player data embedded in a jut.su episode page.
 *
 * The site keeps skip markers and the reference duration in JavaScript wrapped in
 * `Base64.decode(...)`. Network headers/cookies deliberately stay in [JutSuSource].
 */
internal object JutSuEpisodePageParser {
    fun parse(html: String, pageUrl: String): ParsedJutSuEpisodePage {
        val doc = Jsoup.parse(html, pageUrl)
        val sources = doc.select("source[src]")
            .mapNotNull { element ->
                val raw = element.attr("src").trim()
                val url = element.absUrl("src").ifBlank { raw }
                if (!isPlayableJutSuVideoUrl(url, element.attr("type"))) return@mapNotNull null
                val label = element.attr("label").ifBlank {
                    element.attr("res").takeIf(String::isNotBlank)?.let { "${it}p" } ?: "mp4"
                }
                val resolution = element.attr("res").toIntOrNull()
                    ?: RESOLUTION.find(label)?.groupValues?.get(1)?.toIntOrNull()
                ParsedJutSuSource(url, label, resolution)
            }
            .distinctBy { it.url }
            .sortedByDescending { it.resolution ?: 0 }

        val config = BASE64_BLOCK.findAll(html)
            .mapNotNull { match ->
                runCatching {
                    Base64.getMimeDecoder()
                        .decode(match.groupValues[1])
                        .toString(Charsets.UTF_8)
                }.getOrNull()
            }
            .firstOrNull { decoded ->
                PLAYER_VARIABLES.any(decoded::contains)
            }
            .orEmpty()

        val durationMs = config.number("this_video_duration").toMillisecondsOrZero()
        val timestamps = buildList {
            introTimestamp(config, durationMs)?.let(::add)
            endingTimestamp(config, durationMs)?.let(::add)
        }
        return ParsedJutSuEpisodePage(sources, timestamps, durationMs)
    }

    private fun introTimestamp(config: String, durationMs: Long): VetroTimestamp? {
        var startMs = config.number("video_intro_start")?.toMilliseconds()
        var endMs = config.number("video_intro_end")?.toMilliseconds()
        if (startMs == null && endMs == null) return null

        if (startMs == null) startMs = (endMs!! - DEFAULT_INTRO_MS).coerceAtLeast(0L)
        if (endMs == null) endMs = startMs + DEFAULT_INTRO_MS
        if (durationMs > 0L) endMs = endMs.coerceAtMost(durationMs)
        if (startMs < 0L || endMs <= startMs) return null
        if (durationMs > 0L && startMs >= durationMs) return null
        return VetroTimestamp(startMs, endMs, SkipKind.OPENING)
    }

    private fun endingTimestamp(config: String, durationMs: Long): VetroTimestamp? {
        val startMs = config.number("video_outro_start")?.toMilliseconds() ?: return null
        if (startMs < 0L || durationMs <= startMs) return null
        return VetroTimestamp(startMs, durationMs, SkipKind.ENDING)
    }

    private fun String.number(name: String): Double? =
        Regex("""\b${Regex.escape(name)}\s*=\s*["']?(-?\d+(?:\.\d+)?)""")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

    private fun Double?.toMillisecondsOrZero(): Long =
        this?.toMilliseconds()?.coerceAtLeast(0L) ?: 0L

    private fun Double.toMilliseconds(): Long = (this * 1_000.0).toLong()

    private const val DEFAULT_INTRO_MS = 89_000L
    private val RESOLUTION = Regex("""(\d{3,4})""")
    private val PLAYER_VARIABLES = listOf(
        "video_intro_start",
        "video_intro_end",
        "video_outro_start",
        "this_video_duration",
    )
    private val BASE64_BLOCK = Regex(
        """Base64\.decode\s*\(\s*["']([A-Za-z0-9+/=\s]+)["']\s*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
}

internal fun ParsedJutSuEpisodePage.toSkipReference(origin: String): VetroSkipReference? =
    takeIf { durationMs > 0L && timestamps.isNotEmpty() }?.let {
        VetroSkipReference(
            segments = timestamps,
            referenceDurationMs = durationMs,
            origin = origin,
        )
    }

internal fun isPlayableJutSuVideoUrl(url: String, mimeType: String = ""): Boolean {
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return false
    val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault(
        url.substringBefore('?').lowercase(),
    )
    if (IMAGE_EXTENSIONS.any(path::endsWith)) return false
    if (path.split('/').any { segment ->
            segment.substringBeforeLast('.').lowercase() in PLACEHOLDER_STEMS
        }
    ) {
        return false
    }
    val declaredVideo = mimeType.startsWith("video/", ignoreCase = true)
    return declaredVideo || VIDEO_EXTENSIONS.any(path::endsWith)
}

private val IMAGE_EXTENSIONS = setOf(".png", ".gif", ".jpg", ".jpeg", ".webp", ".svg")
private val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".m3u8", ".mpd", ".mkv")
private val PLACEHOLDER_STEMS = setOf("pixel", "placeholder", "blank", "stub")
