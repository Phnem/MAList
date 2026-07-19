package com.example.myapplication.network

/**
 * Unified model for API search results (Shikimori, AniList, Jikan, TMDB).
 * Used for display in search UI and for adding to local DB.
 */
data class ApiSearchResult(
    val title: String,
    val altTitle: String?,
    val posterUrl: String?,
    val episodes: Int,
    val description: String,
    val type: String,
    val genres: List<String>,
    val rating: Int?,
    val source: String,
    val categoryType: String,
    val externalId: String?,
    /** MAL id, если источник отдал его дополнительно к [externalId] (напр. Shikimori detail → myanimelist_id). */
    val malId: Int? = null,
)
