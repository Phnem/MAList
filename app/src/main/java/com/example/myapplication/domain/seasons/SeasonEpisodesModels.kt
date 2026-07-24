package com.example.myapplication.domain.seasons

import kotlinx.serialization.Serializable

/**
 * Один сезон франшизы с числом серий. [episodes] — лучшее известное число серий сезона
 * (заявленное; для онгоинга без анонса — вышедшие на момент резолва).
 */
@Serializable
data class SeasonInfo(
    /** Порядковый номер сезона в цепочке (1 = первый). */
    val seasonNumber: Int,
    /** Число серий сезона (см. kdoc класса). 0 не пишем — такой сезон считается неразрешённым. */
    val episodes: Int,
    /** Сезон ещё выходит: [episodes] может расти (для UI-пометки и коротких TTL). */
    val ongoing: Boolean = false,
    val anilistId: Int? = null,
    val malId: Int? = null,
    /** Откуда взято число серий: "AniList" | "Shikimori" | "MAL". */
    val source: String,
    /** Название сезона (romaji/en) — подпись в UI, отладка сопоставления. */
    val title: String? = null,
)

/**
 * Разложение тайтла по сезонам. [complete] — у всех найденных сезонов есть счётчик серий и
 * ни один не онгоинг; неполные записи перепроверяются каждым фоновым проходом (короткий TTL),
 * полные — раз в месяц.
 */
@Serializable
data class SeasonEpisodesEntry(
    val animeId: String,
    val seasons: List<SeasonInfo> = emptyList(),
    val complete: Boolean = false,
    val resolvedAt: Long = 0L,
)
