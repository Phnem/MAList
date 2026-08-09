package com.example.myapplication.ui.details

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.DetailsLookupRequest
import com.example.myapplication.network.ExternalIds

internal fun Anime.toDetailsLookupRequest(language: AppLanguage): DetailsLookupRequest =
    DetailsLookupRequest(
        title = when (language) {
            AppLanguage.RU -> titleRu?.takeIf(String::isNotBlank) ?: title
            AppLanguage.EN -> titleEn?.takeIf(String::isNotBlank) ?: title
        },
        language = language,
        isManga = mediaType == MediaType.MANGA,
        remangaId = null,
        malId = malId,
        anilistId = anilistId,
        titleEn = titleEn,
        shikimoriId = shikimoriId,
        externalIds = ExternalIds(tmdb = tmdbId, kinopoisk = kinopoiskId),
        appContentType = when (mediaType) {
            MediaType.ANIME -> AppContentType.ANIME
            MediaType.MANGA -> AppContentType.MANGA
            MediaType.MOVIE -> AppContentType.MOVIE
            MediaType.SERIES -> AppContentType.SERIES
        },
    )

/** ANIME сохраняет старый EN-гейт; MOVIE/SERIES умеют резолвиться по id или title. */
internal fun Anime.canLookupDetails(language: AppLanguage): Boolean {
    if (language != AppLanguage.EN || mediaType != MediaType.ANIME) return title.isNotBlank()
    return anilistId != null || malId != null || shikimoriId != null || !titleEn.isNullOrBlank()
}
