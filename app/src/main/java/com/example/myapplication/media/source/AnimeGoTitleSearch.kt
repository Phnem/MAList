package com.example.myapplication.media.source

import com.example.myapplication.sync.TitleMatcher

/**
 * Чистый слой подбора запросов и подтверждения страницы для AnimeGo — по образцу
 * [JutSuTitleSearch][selectJutSuSearchResponse]. Вынесен из источника, чтобы лестницу
 * можно было проверить без сети: до этого у AnimeGo не было ни одного теста.
 */

/**
 * Лестница поисковых запросов, по убыванию точности.
 *
 * AnimeGo — русский каталог, и сезоны в нём называются «<франшиза> <номер>». Раньше сюда
 * уходило английское название сезона из AniList, потому что сужение до сезона обнуляло
 * русский алиас, — запрос не находил ничего.
 */
internal fun animeGoSearchQueries(query: SeasonSourceQuery): List<String> = buildList {
    val anime = query.anime
    val russian = anime.titleRu?.trim()?.takeIf(String::isNotEmpty)
    val seasonTitle = anime.title.trim().takeIf { query.seasonIdentifiable && it.isNotEmpty() }

    // 1. Русский алиас с порядковым маркером: «Повар-боец Сома 2».
    if (russian != null && query.seasonNumber > 1) add("$russian ${query.seasonNumber}")
    // 2. Русский франшизный алиас. Для сезона ≥2 приведёт на страницу франшизы, поэтому
    //    результат обязан пройти подтверждение в animeGoPageMatchesSeason.
    if (russian != null) add(russian)
    // 3. Английское сезонное название — прежнее поведение, теперь последняя ступень.
    seasonTitle?.let(::add)
    anime.title.trim().takeIf(String::isNotEmpty)?.let(::add)
    anime.titleEn?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
}.distinctBy(String::lowercase)

/**
 * Подтверждение, что найденная страница — запрошенный сезон, а не первый сезон франшизы.
 *
 * Совпадения с франшизным названием для сезона ≥2 недостаточно: у AnimeGo под каждым сезоном
 * своя страница, и франшизный запрос приводит на первую из них. Такой результат принимается
 * только при порядковом маркере в названии страницы.
 */
internal fun animeGoPageMatchesSeason(
    pageTitle: String,
    seasonTitle: String?,
    franchiseTitles: List<String>,
    seasonNumber: Int,
    threshold: Double = VetroHttpSource.TITLE_MATCH_THRESHOLD,
): Boolean {
    if (pageTitle.isBlank()) return false
    val remote = listOf(pageTitle)
    if (
        seasonTitle != null &&
        seasonTitle.isNotBlank() &&
        TitleMatcher.bestScore(seasonTitle, remote) >= threshold
    ) {
        return true
    }
    val franchiseScore = franchiseTitles
        .filter(String::isNotBlank)
        .maxOfOrNull { TitleMatcher.bestScore(it, remote) }
        ?: 0.0
    if (franchiseScore < threshold) return false
    return seasonNumber <= 1 || containsSeasonOrdinal(pageTitle, seasonNumber)
}

/** Номер сезона отдельным токеном: «Сома 2» — да, «Сома 2nd» и «Сома 12» — нет. */
internal fun containsSeasonOrdinal(text: String, seasonNumber: Int): Boolean =
    Regex("""(?:^|[^\p{L}\p{N}])${Regex.escape(seasonNumber.toString())}(?:$|[^\p{L}\p{N}])""")
        .containsMatchIn(text.lowercase())
