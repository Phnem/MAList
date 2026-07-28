package com.example.myapplication.domain.seasons

import kotlinx.serialization.Serializable

/**
 * Один сезон франшизы с числом серий.
 *
 * [episodes] — сколько серий ДОСТУПНО сейчас: у завершённого сезона это заявленное число,
 * у онгоинга — только вышедшие (4 из анонсированных 12 → 4). Именно по нему строятся списки
 * серий, скачивание и плеер, чтобы не показывать невышедшие. Анонсированный итог — [totalEpisodes].
 */
@Serializable
data class SeasonInfo(
    /** Порядковый номер сезона в цепочке (1 = первый). */
    val seasonNumber: Int,
    /** Доступные серии (см. kdoc класса). 0 не пишем — такой сезон считается неразрешённым. */
    val episodes: Int,
    /** Заявленное число серий сезона; null = не анонсировано. Для UI-подписи «4 / 12». */
    val totalEpisodes: Int? = null,
    /** Сезон ещё выходит: [episodes] будет расти (для UI-пометки и коротких TTL). */
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
    /**
     * Версия семантики резолва. Записи со старой версией считаются протухшими независимо от TTL:
     * иначе «полный» расклад, собранный прежним алгоритмом, провисел бы в кэше до месяца.
     * Поднимать при каждом изменении того, ЧТО кладём в [seasons] (см. [CURRENT_SCHEMA]).
     */
    val schema: Int = 0,
) {
    companion object {
        /**
         * 1 — обход франшизы идёт СКВОЗЬ фильмы/OVA (иначе цепочка рвалась и терялись сезоны),
         * а [SeasonInfo.episodes] у онгоинга = вышедшие серии, не анонсированные.
         * 2 — сорванный обход больше не выдаётся за полный результат; версия поднята, чтобы
         *     выбросить уже накопленные «complete» записи из одного сезона (они жили месяц).
         */
        const val CURRENT_SCHEMA = 2
    }
}
