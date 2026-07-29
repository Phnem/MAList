package com.example.myapplication.network

enum class AppContentType { ANIME, MOVIE, SERIES, MANGA }

/**
 * Раздел поиска → значение `ApiSearchResult.categoryType`.
 *
 * Раздел — единственный надёжный признак типа тайтла: часть источников отдаёт тип неверно
 * (Shikimori проставляет `"ANIME"` и результатам `/api/mangas`), а часть не отдаёт вовсе.
 * Поэтому выдача поиска штампуется разделом, в котором её запросили.
 */
fun AppContentType.categoryTypeName(): String = when (this) {
    AppContentType.ANIME -> "ANIME"
    AppContentType.MOVIE -> "MOVIE"
    AppContentType.SERIES -> "SERIES"
    AppContentType.MANGA -> "MANGA"
}
