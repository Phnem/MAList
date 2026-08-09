package com.example.myapplication.network.kinopoisk

import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.ExternalIds
import com.example.myapplication.network.dto.KinopoiskMovieDto

/** Карточка Kinopoisk-результата для деталей/репара — рейтинг оставлен как есть (0..10), не ×10 (см. [toApiSearchResult] для конвертации в общую шкалу). */
data class KinopoiskDetails(
    val id: Int,
    val name: String,
    val description: String,
    val posterUrl: String?,
    val ratingKp: Double?,
    val genres: List<String>,
    val externalTmdbId: Int?,
)

private fun KinopoiskMovieDto.displayName(): String =
    name ?: alternativeName ?: ""

private fun KinopoiskMovieDto.posterUrl(): String? =
    poster?.url?.takeIf { it.isNotBlank() }

/**
 * Kinopoisk-рейтинг `kp` — уже 0..10 с десятичной точностью; переводим в общую 0..100 шкалу
 * приложения (та же, что TMDB `vote_average * 10`), чтобы дальше по конвейеру (`RatingScale`)
 * оба источника обрабатывались одинаково.
 */
private fun Double?.toAppRating(): Int? = this?.let { (it * 10).toInt() }

fun KinopoiskMovieDto.toApiSearchResult(categoryType: String): ApiSearchResult = ApiSearchResult(
    title = displayName(),
    altTitle = alternativeName?.takeIf { it != name },
    posterUrl = posterUrl(),
    episodes = if (categoryType == "MOVIE") 1 else 0,
    description = description.orEmpty(),
    type = if (categoryType == "MOVIE") "Movie" else "TV",
    genres = genres.mapNotNull { it.name },
    rating = rating?.kp.toAppRating(),
    source = "Kinopoisk",
    categoryType = categoryType,
    seasonYear = year,
    originalTitle = alternativeName?.takeIf { it.isNotBlank() },
    externalId = id.toString(),
    externalIds = ExternalIds(kinopoisk = id, tmdb = externalId?.tmdb),
)

fun KinopoiskMovieDto.toDetails(): KinopoiskDetails = KinopoiskDetails(
    id = id,
    name = displayName(),
    description = description.orEmpty(),
    posterUrl = posterUrl(),
    ratingKp = rating?.kp,
    genres = genres.mapNotNull { it.name },
    externalTmdbId = externalId?.tmdb,
)
