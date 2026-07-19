package com.example.myapplication.domain.recommendations

import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.normalizeForSearch
import com.example.myapplication.network.ApiSearchResult

/**
 * Отсев кандидатов: уже в библиотеке (по внешним id И по нормализованному тайтлу),
 * без обложки — до скоринга, чтобы не тратить работу на мусор.
 */
class RecommendationFilter {

    fun filter(pool: List<PoolEntry>, library: List<Anime>): List<PoolEntry> {
        val libraryTitleKeys = HashSet<String>(library.size * 2)
        val libraryAnilistIds = HashSet<Int>()
        val libraryMalIds = HashSet<Int>()
        val libraryShikimoriIds = HashSet<Int>()
        for (anime in library) {
            // Дедуп по обеим локалям: основной title + titleEn + titleRu.
            anime.title.normalizeForSearch().takeIf { it.isNotEmpty() }?.let { libraryTitleKeys.add(it) }
            anime.titleEn?.normalizeForSearch()?.takeIf { it.isNotEmpty() }?.let { libraryTitleKeys.add(it) }
            anime.titleRu?.normalizeForSearch()?.takeIf { it.isNotEmpty() }?.let { libraryTitleKeys.add(it) }
            anime.anilistId?.let { libraryAnilistIds.add(it) }
            anime.malId?.let { libraryMalIds.add(it) }
            anime.shikimoriId?.let { libraryShikimoriIds.add(it) }
        }

        return pool.filter { entry ->
            val r = entry.result
            if (r.posterUrl.isNullOrBlank()) return@filter false
            if (isInLibraryById(r, libraryAnilistIds, libraryMalIds, libraryShikimoriIds)) return@filter false
            val titleKey = r.title.normalizeForSearch()
            val altKey = r.altTitle?.normalizeForSearch().orEmpty()
            if (titleKey in libraryTitleKeys || (altKey.isNotEmpty() && altKey in libraryTitleKeys)) return@filter false
            true
        }
    }

    private fun isInLibraryById(
        result: ApiSearchResult,
        anilistIds: Set<Int>,
        malIds: Set<Int>,
        shikimoriIds: Set<Int>,
    ): Boolean {
        val id = result.externalId?.toIntOrNull() ?: return false
        return when (result.source.lowercase()) {
            "anilist" -> id in anilistIds
            "mal", "jikan" -> id in malIds
            "shikimori" -> id in shikimoriIds
            else -> false
        }
    }
}
