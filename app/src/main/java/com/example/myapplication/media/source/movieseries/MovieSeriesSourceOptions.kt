package com.example.myapplication.media.source.movieseries

import com.example.myapplication.media.source.VetroHoster
import com.example.myapplication.media.source.VetroVideo

/** One quality a source offers, with the exact stream behind it. */
data class SourceQualityOption(
    val label: String,
    val resolution: Int?,
    val video: VetroVideo,
)

/** One source the user can choose: a provider, or a named translation within one. */
data class SourceOption(
    val name: String,
    val qualities: List<SourceQualityOption>,
) {
    /** Best available stream from this source, used when the user picks the source alone. */
    val preferredVideo: VetroVideo get() = qualities.first().video
}

/**
 * The choice offered for a movie or episode: which source, then which quality.
 *
 * Built from the already-ranked hosters, so the first entry is the one the cascade would have picked
 * anyway. Presenting the rest is what section 17 of the brief asks for — several providers with
 * different translations and qualities, rather than one silent decision.
 */
object MovieSeriesSourceOptions {

    fun from(hosters: List<VetroHoster>): List<SourceOption> = hosters
        .mapNotNull { hoster ->
            val qualities = hoster.videos.orEmpty()
                .distinctBy(VetroVideo::url)
                .map { video -> video.toQualityOption() }
                .sortedWith(
                    compareByDescending<SourceQualityOption> { it.resolution ?: 0 }
                        .thenBy { it.label }
                )
            // A source with nothing playable is not an option; showing it would be a dead end.
            if (qualities.isEmpty()) null else SourceOption(hoster.name, qualities)
        }

    private fun VetroVideo.toQualityOption(): SourceQualityOption {
        val detected = resolution?.takeIf { it > 0 } ?: RESOLUTION_IN_LABEL
            .find(label)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        return SourceQualityOption(
            // Falls back to whatever the source called it: an unlabelled stream is still playable,
            // and inventing a number would be a lie.
            label = detected?.let { "${it}p" } ?: label.ifBlank { "Auto" },
            resolution = detected,
            video = this,
        )
    }

    private val RESOLUTION_IN_LABEL = Regex("""(?i)\b(\d{3,4})p\b""")
}
