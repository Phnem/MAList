package com.example.myapplication.network

/** True when letter content is predominantly Cyrillic (typical Shikimori / RU synopsis). */
internal fun isMostlyCyrillic(text: String): Boolean {
    val letters = text.filter { it.isLetter() }
    if (letters.isEmpty()) return false
    val cyrillic = letters.count { it in '\u0400'..'\u04FF' }
    return cyrillic.toFloat() / letters.length >= 0.25f
}

/**
 * EN-режим: Shikimori и явно русский текст никогда не показываем как описание.
 */
internal fun acceptEnDescription(details: AnimeDetails?): AnimeDetails? {
    if (details == null) return null
    if (details.source.equals("Shikimori", ignoreCase = true)) return null
    if (isMostlyCyrillic(details.description)) return null
    return details
}

internal fun acceptEnDescription(result: ApiSearchResult?): AnimeDetails? =
    acceptEnDescription(result?.toAnimeDetails())
