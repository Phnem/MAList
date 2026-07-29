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
    /** Имя API, отдавшего карточку («AniList», «Shikimori»), а не первоисточник тайтла. */
    val source: String,
    val airedOn: String? = null,
    /** Формат выпуска сырым кодом источника: `TV`, `ONA`, `MOVIE`, `tv`, `ova`… null = не отдал. */
    val format: String? = null,
    /** Первоисточник тайтла: `MANGA`, `LIGHT_NOVEL`, `ORIGINAL`… (AniList/Jikan). */
    val sourceMaterial: String? = null,
    /** Главная студия. Несколько студий не носим: в карточке помещается одна. */
    val studio: String? = null,
    /** Сезон выхода сырым кодом: `WINTER`, `SPRING`, `SUMMER`, `FALL`. */
    val season: String? = null,
    val seasonYear: Int? = null,
)

fun ApiSearchResult.toAnimeDetails(): AnimeDetails = AnimeDetails(
    title = title,
    altTitle = altTitle,
    description = description,
    type = type,
    status = statusRaw.orEmpty(),
    episodesAired = episodes,
    episodesTotal = episodes.takeIf { it > 0 },
    nextEpisode = null,
    genres = genres,
    rating = rating,
    posterUrl = posterUrl,
    source = source,
    format = format,
    sourceMaterial = sourceMaterial,
    studio = studio,
    season = season,
    seasonYear = seasonYear,
)
