package com.example.myapplication.network

/**
 * Явные названия тайтла из внешнего API (без «схлопывания» как в [ApiSearchResult]).
 * Используется обогащением EN-названий: нам важно точно знать english/romaji/native
 * и внешние id, а не единый display-title.
 */
data class EnrichedTitles(
    val anilistId: Int? = null,
    val malId: Int? = null,
    val shikimoriId: Int? = null,
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val russian: String? = null,
) {
    /** Непустое английское название (если есть). */
    val englishOrNull: String? get() = english?.trim()?.takeIf { it.isNotEmpty() }

    /** Непустое русское название (если есть). */
    val russianOrNull: String? get() = russian?.trim()?.takeIf { it.isNotEmpty() }

    /** Кандидаты для сопоставления по названию (english/romaji/native/russian). */
    fun matchCandidates(): List<String> =
        listOfNotNull(english, romaji, native, russian).map { it.trim() }.filter { it.isNotEmpty() }
}
