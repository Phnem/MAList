package com.example.myapplication.network.movie

import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.ExternalIds

/**
 * Дедуп при merge результатов TMDB+Kinopoisk (см. .scratch/movie-series-infra/spec.md §10) —
 * консервативный каскад, ранние ступени строгие, поздние — с confidence-порогом:
 *
 * 1. точное совпадение `tmdbId`;
 * 2. точное совпадение `kinopoiskId` (если TMDB-моста нет);
 * 3. нормализованное название + год — точное совпадение;
 * 4. нормализованное название + год + высокий порог похожести — последний фоллбэк
 *    (транслитерация/диакритика: "Shogun" vs "Shōgun").
 *
 * Год участвует в КАЖДОЙ title-based ступени (3 и 4) — без него разные тайтлы с одинаковым
 * названием (ремейки/адаптации разных лет) схлопнулись бы в одну запись. При сомнении —
 * НЕ мержить: лишняя карточка в поиске безопаснее ложно склеенных id.
 */
object MovieResultDeduper {

    fun merge(results: List<ApiSearchResult>): List<ApiSearchResult> {
        val merged = mutableListOf<ApiSearchResult>()
        for (candidate in results) {
            val matchIndex = merged.indexOfFirst { isDuplicate(it, candidate) }
            if (matchIndex == -1) {
                merged += candidate
            } else {
                merged[matchIndex] = mergeIds(merged[matchIndex], candidate)
            }
        }
        return merged
    }

    private fun isDuplicate(a: ApiSearchResult, b: ApiSearchResult): Boolean {
        if (a.categoryType != b.categoryType) return false
        val aTmdb = a.externalIds.tmdb
        val bTmdb = b.externalIds.tmdb
        if (aTmdb != null && bTmdb != null) return aTmdb == bTmdb

        val aKinopoisk = a.externalIds.kinopoisk
        val bKinopoisk = b.externalIds.kinopoisk
        if (aKinopoisk != null && aKinopoisk == bKinopoisk) return true

        val sameYear = a.seasonYear != null && a.seasonYear == b.seasonYear
        if (!sameYear) return false

        val aOriginal = a.originalTitle?.let(MovieTitleMatcher::exactKey).orEmpty()
        val bOriginal = b.originalTitle?.let(MovieTitleMatcher::exactKey).orEmpty()
        if (aOriginal.isNotEmpty() && aOriginal == bOriginal) return true

        return MovieTitleMatcher.isMatch(a.title, listOfNotNull(b.title, b.altTitle)) ||
            a.altTitle?.let { MovieTitleMatcher.isMatch(it, listOf(b.title)) } == true
    }

    /** Найденное совпадение остаётся отображаемой карточкой (первый источник в списке
     *  побеждает по названию/постеру/т.д.), но забирает id, которых у него не было. */
    private fun mergeIds(kept: ApiSearchResult, other: ApiSearchResult): ApiSearchResult = kept.copy(
        title = kept.title.ifBlank { other.title },
        altTitle = kept.altTitle ?: other.altTitle,
        posterUrl = kept.posterUrl ?: other.posterUrl,
        episodes = kept.episodes.takeIf { it > 0 } ?: other.episodes,
        description = kept.description.ifBlank { other.description },
        type = kept.type.ifBlank { other.type },
        genres = (kept.genres + other.genres).distinctBy { it.lowercase() },
        rating = kept.rating ?: other.rating,
        externalIds = ExternalIds(
            tmdb = kept.externalIds.tmdb ?: other.externalIds.tmdb,
            kinopoisk = kept.externalIds.kinopoisk ?: other.externalIds.kinopoisk,
        ),
        seasonYear = kept.seasonYear ?: other.seasonYear,
        originalTitle = kept.originalTitle ?: other.originalTitle,
    )
}
