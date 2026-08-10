package com.example.myapplication.network.dto

import kotlinx.serialization.Serializable

/** `GET /api/v2.1/films/search-by-keyword`. */
@Serializable
data class KinopoiskSearchResponseDto(
    val films: List<KinopoiskSearchFilmDto> = emptyList(),
)

@Serializable
data class KinopoiskSearchFilmDto(
    val filmId: Int,
    val nameRu: String? = null,
    val nameEn: String? = null,
    /** `FILM`, `TV_SERIES`, `MINI_SERIES`, `TV_SHOW`. */
    val type: String? = null,
    val year: String? = null,
    val description: String? = null,
    val countries: List<KinopoiskCountryDto> = emptyList(),
    val genres: List<KinopoiskGenreDto> = emptyList(),
    /** The legacy search contract transports the 0..10 rating as a string. */
    val rating: String? = null,
    val posterUrl: String? = null,
    val posterUrlPreview: String? = null,
)

/** `GET /api/v2.2/films/{id}`. */
@Serializable
data class KinopoiskFilmDto(
    val kinopoiskId: Int,
    val imdbId: String? = null,
    val nameRu: String? = null,
    val nameEn: String? = null,
    val nameOriginal: String? = null,
    val posterUrl: String? = null,
    val posterUrlPreview: String? = null,
    val ratingKinopoisk: Double? = null,
    val description: String? = null,
    val type: String? = null,
    val year: Int? = null,
    val countries: List<KinopoiskCountryDto> = emptyList(),
    val genres: List<KinopoiskGenreDto> = emptyList(),
)

@Serializable
data class KinopoiskCountryDto(
    val country: String? = null,
)

@Serializable
data class KinopoiskGenreDto(
    val genre: String? = null,
)

/** `GET /api/v2.2/films/{id}/seasons`. Known catalogue layout only, never released count. */
@Serializable
data class KinopoiskSeasonResponseDto(
    val total: Int = 0,
    val items: List<KinopoiskSeasonDto> = emptyList(),
)

@Serializable
data class KinopoiskSeasonDto(
    val number: Int,
    val episodes: List<KinopoiskEpisodeDto> = emptyList(),
)

@Serializable
data class KinopoiskEpisodeDto(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val nameRu: String? = null,
    val nameEn: String? = null,
    val synopsis: String? = null,
    val releaseDate: String? = null,
)
