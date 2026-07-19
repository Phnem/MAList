package com.example.myapplication.network

data class AnimeDetails(
    val title: String,
    val altTitle: String?,
    val description: String,
    val type: String,
    val status: String,
    val episodesAired: Int,
    val episodesTotal: Int?,
    val nextEpisode: String?,
    val genres: List<String>,
    val rating: Int?,
    val posterUrl: String?,
    val source: String,
    val airedOn: String? = null
)

fun ApiSearchResult.toAnimeDetails(): AnimeDetails = AnimeDetails(
    title = title,
    altTitle = altTitle,
    description = description,
    type = type,
    status = "",
    episodesAired = episodes,
    episodesTotal = episodes.takeIf { it > 0 },
    nextEpisode = null,
    genres = genres,
    rating = rating,
    posterUrl = posterUrl,
    source = source,
)
