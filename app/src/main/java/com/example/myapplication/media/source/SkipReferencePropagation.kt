package com.example.myapplication.media.source

internal fun List<VetroHoster>.withPropagatedSkipReference(): List<VetroHoster> {
    val references = flatMap { hoster ->
        listOfNotNull(hoster.skipReference) +
            hoster.videos.orEmpty().mapNotNull(VetroVideo::skipReference)
    }
    val episodeReference = references.firstOrNull {
        it.origin.equals(JUTSU_REFERENCE_ORIGIN, ignoreCase = true)
    } ?: return this

    return map { hoster ->
        val hosterReference = hoster.skipReference ?: episodeReference
        hoster.copy(
            skipReference = hosterReference,
            videos = hoster.videos?.map { video ->
                if (video.skipReference == null) {
                    video.copy(skipReference = hosterReference)
                } else {
                    video
                }
            },
        )
    }
}

internal fun List<VetroHoster>.playableHosters(): List<VetroHoster> =
    mapNotNull { hoster ->
        val videos = hoster.videos.orEmpty()
            .filter { isPlayableVetroVideoUrl(it.url) }
            .distinctBy(VetroVideo::url)
        hoster.copy(videos = videos).takeIf { videos.isNotEmpty() }
    }.distinctBy { hoster ->
        hoster.videos.orEmpty().joinToString("|", transform = VetroVideo::url)
    }

internal fun List<VetroHoster>.hasPlayableVideo(): Boolean =
    any { hoster -> hoster.videos.orEmpty().any { isPlayableVetroVideoUrl(it.url) } }

internal fun isPlayableVetroVideoUrl(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return false
    val path = uri.path.orEmpty().lowercase()
    if (path.substringAfterLast('/').startsWith("pixel.")) return false
    return GENERIC_NON_VIDEO_EXTENSIONS.none(path::endsWith)
}

private const val JUTSU_REFERENCE_ORIGIN = "jut.su"
private val GENERIC_NON_VIDEO_EXTENSIONS = setOf(
    ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
)
