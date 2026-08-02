package com.example.myapplication.media.source

import kotlin.math.abs

/** Keeps the hoster/studio identity when a hoster list is flattened for playback or download. */
fun flattenVideosWithSource(hosters: List<VetroHoster>): List<VetroVideo> =
    hosters.flatMap { hoster ->
        hoster.videos.orEmpty().map { video ->
            video.copy(
                sourceName = video.sourceName?.takeIf(String::isNotBlank) ?: hoster.name,
            )
        }
    }

/**
 * Keeps the requested resolution first, but prefers stable HLS manifests over hotlink-protected
 * progressive placeholders when two sources advertise the same quality.
 */
fun rankVideosForResolution(
    videos: List<VetroVideo>,
    preferredResolution: Int,
): List<VetroVideo> =
    videos.distinctBy { it.url }.sortedWith(
        compareBy<VetroVideo>(
            { video ->
                video.rankedResolution()?.let { abs(it - preferredResolution) }
                    ?: Int.MAX_VALUE / 2
            },
            { video ->
                video.rankedResolution()?.let { if (it <= preferredResolution) 0 else 1 } ?: 1
            },
            { video -> video.reliabilityRank() },
            { video -> if (video.isPreferred) 0 else 1 },
            { video -> -(video.rankedResolution() ?: 0) },
        )
    )

private fun VetroVideo.rankedResolution(): Int? =
    resolution?.takeIf { it > 0 }
        ?: Regex("""(?i)\b(\d{3,4})p\b""")
            .find(label)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

private fun VetroVideo.reliabilityRank(): Int {
    val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return when {
        host.contains("libria.fun") -> 0
        isHlsOrDash -> 1
        else -> 2
    }
}
