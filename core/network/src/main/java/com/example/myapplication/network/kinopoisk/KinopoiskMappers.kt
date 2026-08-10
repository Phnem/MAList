package com.example.myapplication.network.kinopoisk

import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.ExternalIds
import com.example.myapplication.network.dto.KinopoiskEpisodeDto
import com.example.myapplication.network.dto.KinopoiskFilmDto
import com.example.myapplication.network.dto.KinopoiskSearchFilmDto
import com.example.myapplication.network.dto.KinopoiskSeasonDto

/** Detail card owned by the Kinopoisk adapter; rating remains on the native 0..10 scale. */
data class KinopoiskDetails(
    val id: Int,
    val name: String,
    val description: String,
    val posterUrl: String?,
    val ratingKp: Double?,
    val genres: List<String>,
    /** This provider exposes IMDb rather than a direct TMDB bridge. */
    val externalImdbId: String? = null,
    /** Kept for repository compatibility; title/IMDb resolution supplies TMDB separately. */
    val externalTmdbId: Int? = null,
    val nameRu: String? = null,
    val nameEn: String? = null,
    val originalName: String? = null,
    val year: Int? = null,
)

data class KinopoiskSeason(
    val number: Int,
    val episodes: List<KinopoiskEpisode>,
)

data class KinopoiskEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val nameRu: String?,
    val nameEn: String?,
    val synopsis: String?,
    val releaseDate: String?,
)

private fun Double?.toAppRating(): Int? = this?.let { (it * 10).toInt() }

fun KinopoiskSearchFilmDto.toApiSearchResult(categoryType: String): ApiSearchResult {
    val title = nameRu?.takeIf(String::isNotBlank)
        ?: nameEn?.takeIf(String::isNotBlank)
        .orEmpty()
    return ApiSearchResult(
        title = title,
        altTitle = nameEn?.takeIf { it.isNotBlank() && it != title },
        posterUrl = posterUrl?.takeIf(String::isNotBlank),
        episodes = if (categoryType == "MOVIE") 1 else 0,
        description = description.orEmpty(),
        type = if (categoryType == "MOVIE") "FILM" else "TV",
        genres = genres.mapNotNull { it.genre?.takeIf(String::isNotBlank) },
        rating = rating?.toDoubleOrNull().toAppRating(),
        source = "Kinopoisk",
        categoryType = categoryType,
        seasonYear = year?.toIntOrNull(),
        originalTitle = nameEn?.takeIf(String::isNotBlank),
        titleEn = nameEn?.takeIf(String::isNotBlank),
        titleRu = nameRu?.takeIf(String::isNotBlank),
        externalId = filmId.toString(),
        externalIds = ExternalIds(kinopoisk = filmId),
    )
}

fun KinopoiskFilmDto.toDetails(): KinopoiskDetails = KinopoiskDetails(
    id = kinopoiskId,
    name = nameRu?.takeIf(String::isNotBlank)
        ?: nameEn?.takeIf(String::isNotBlank)
        ?: nameOriginal?.takeIf(String::isNotBlank)
        .orEmpty(),
    description = description.orEmpty(),
    posterUrl = posterUrl?.takeIf(String::isNotBlank),
    ratingKp = ratingKinopoisk,
    genres = genres.mapNotNull { it.genre?.takeIf(String::isNotBlank) },
    externalImdbId = imdbId?.takeIf(String::isNotBlank),
    nameRu = nameRu?.takeIf(String::isNotBlank),
    nameEn = nameEn?.takeIf(String::isNotBlank),
    originalName = nameOriginal?.takeIf(String::isNotBlank),
    year = year,
)

fun KinopoiskSeasonDto.toDomain(): KinopoiskSeason = KinopoiskSeason(
    number = number,
    episodes = episodes.map(KinopoiskEpisodeDto::toDomain),
)

private fun KinopoiskEpisodeDto.toDomain(): KinopoiskEpisode = KinopoiskEpisode(
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    nameRu = nameRu,
    nameEn = nameEn,
    synopsis = synopsis,
    releaseDate = releaseDate,
)
