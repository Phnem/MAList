package com.example.myapplication.network

/**
 * Episode artwork exposed by a metadata provider. AniList's streaming episode list is sparse for
 * some titles, so callers should use [EpisodeArtworkBundle.coverImageUrl] as a visual fallback.
 */
data class EpisodeArtwork(
    val episodeNumber: Int,
    val title: String?,
    val imageUrl: String,
    val sourceUrl: String?,
    val site: String?,
)

data class EpisodeArtworkBundle(
    val coverImageUrl: String?,
    val episodes: List<EpisodeArtwork>,
)
