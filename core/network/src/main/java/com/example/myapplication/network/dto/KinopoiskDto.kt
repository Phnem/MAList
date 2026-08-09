package com.example.myapplication.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class KinopoiskSearchResponseDto(
    val docs: List<KinopoiskMovieDto> = emptyList()
)

@Serializable
data class KinopoiskMovieDto(
    val id: Int,
    val name: String? = null,
    val enName: String? = null,
    val alternativeName: String? = null,
    val description: String? = null,
    /** `movie`/`tv-series`/`cartoon`/`anime`/`animated-series` — поиск фильтрует по нему запросом. */
    val type: String? = null,
    val year: Int? = null,
    val rating: KinopoiskRatingDto? = null,
    val poster: KinopoiskPosterDto? = null,
    val genres: List<KinopoiskGenreDto> = emptyList(),
    val movieLength: Int? = null,
    val seasonsInfo: List<KinopoiskSeasonInfoDto> = emptyList(),
    val externalId: KinopoiskExternalIdDto? = null,
)

@Serializable
data class KinopoiskRatingDto(
    val kp: Double? = null,
    val imdb: Double? = null,
)

@Serializable
data class KinopoiskPosterDto(
    val url: String? = null,
    val previewUrl: String? = null,
)

@Serializable
data class KinopoiskGenreDto(
    val name: String? = null,
)

@Serializable
data class KinopoiskSeasonInfoDto(
    val number: Int? = null,
    val episodesCount: Int? = null,
)

/** Мост к другим каталогам — `tmdb` даёт прямую связку Kinopoisk-результата с TMDB-карточкой. */
@Serializable
data class KinopoiskExternalIdDto(
    val tmdb: Int? = null,
    val imdb: String? = null,
)
