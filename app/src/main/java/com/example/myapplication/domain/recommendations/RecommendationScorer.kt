package com.example.myapplication.domain.recommendations

import com.example.myapplication.domain.normalizeForSearch
import com.example.myapplication.network.ApiSearchResult

/**
 * Кандидат из related-графа одного сида (до агрегации/скоринга).
 */
data class PoolEntry(
    val result: ApiSearchResult,
    val seedTitle: String,
    /** Пользовательский рейтинг сида (0..10); 0 — сид не оценён. */
    val seedRating: Float,
)

/**
 * Скоринг кандидатов: жанровый вектор + co-occurrence + рейтинг источника + вес сида.
 *
 * Co-occurrence — самый сильный сигнал related-графа: тайтл, который источники
 * рекомендуют сразу к нескольким сидам пользователя, почти наверняка релевантен,
 * даже если у него не распарсились жанры (Jikan recommendations их не отдаёт).
 */
class RecommendationScorer(
    private val affinity: Map<String, Float>,
    /** Сырой жанр источника → тег приложения (id GenreRepository). */
    private val genreToTagId: (String) -> String?,
    /**
     * Источник, чьи метаданные (тайтл/описание) предпочесть при склейке дублей.
     * RU → "Shikimori" (русские названия), EN → "AniList".
     */
    private val preferredSource: String? = null,
) {

    /** Агрегирует дубли между сидами/источниками и возвращает отсортированный топ. */
    fun scoreAndRank(pool: List<PoolEntry>, limit: Int): List<RecommendationItem> {
        val groups = groupDuplicates(pool)
        return groups.mapNotNull { (key, entries) ->
            val best = entries.maxBy { completeness(it.result) }
            val coverUrl = entries.firstNotNullOfOrNull { it.result.posterUrl?.takeIf { url -> url.isNotBlank() } }
                ?: return@mapNotNull null
            val score = score(entries)
            val bestSeed = entries.maxBy { it.seedRating }
            RecommendationItem(
                key = key,
                title = best.result.title,
                altTitle = best.result.altTitle,
                coverUrl = coverUrl,
                genres = entries.firstNotNullOfOrNull { e -> e.result.genres.takeIf { it.isNotEmpty() } }.orEmpty(),
                externalRating = entries.firstNotNullOfOrNull { normalizedRating(it.result) },
                episodes = entries.maxOf { it.result.episodes },
                description = best.result.description,
                source = best.result.source,
                externalId = best.result.externalId,
                categoryType = best.result.categoryType,
                score = score,
                seedTitle = bestSeed.seedTitle,
            )
        }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun score(entries: List<PoolEntry>): Float {
        val genres = entries.firstNotNullOfOrNull { e -> e.result.genres.takeIf { it.isNotEmpty() } }.orEmpty()
        val genreScore = genres
            .mapNotNull { genreToTagId(it) }
            .mapNotNull { affinity[it] }
            .let { if (it.isEmpty()) 0f else it.average().toFloat() }

        val coOccurrence = (COOCCURRENCE_STEP * (entries.size - 1)).coerceAtMost(COOCCURRENCE_CAP)

        val rating = entries.firstNotNullOfOrNull { normalizedRating(it.result) } ?: 0
        val ratingBoost = (rating / 100f) * RATING_WEIGHT

        val seedBoost = (entries.maxOf { it.seedRating } / 10f) * SEED_WEIGHT

        return genreScore + coOccurrence + ratingBoost + seedBoost
    }

    /** Рейтинг источника, приведённый к 0..100 (AniList отдаёт 0..100, Shikimori/Jikan 0..10). */
    private fun normalizedRating(result: ApiSearchResult): Int? =
        result.rating?.let { if (it <= 10) it * 10 else it }?.coerceIn(0, 100)

    /** Полнота метаданных — из дублей берём самый информативный; приоритетный источник доминирует. */
    private fun completeness(result: ApiSearchResult): Int {
        var c = 0
        if (result.source.equals(preferredSource, ignoreCase = true)) c += 100
        if (!result.posterUrl.isNullOrBlank()) c += 4
        if (result.genres.isNotEmpty()) c += 2
        if (result.rating != null) c += 1
        if (result.description.isNotBlank()) c += 1
        return c
    }

    /**
     * Склейка дублей между источниками. У одного тайтла разные id в AniList/Shikimori/MAL
     * и разные пары названий (english+romaji vs romaji+russian), поэтому кандидаты
     * объединяются, если совпадает ЛЮБОЕ из нормализованных названий (title или altTitle).
     */
    private fun groupDuplicates(pool: List<PoolEntry>): Map<String, List<PoolEntry>> {
        val keyToGroup = HashMap<String, String>()
        val groups = LinkedHashMap<String, MutableList<PoolEntry>>()
        for (entry in pool) {
            val keys = titleKeys(entry.result)
            if (keys.isEmpty()) continue
            val groupId = keys.firstNotNullOfOrNull { keyToGroup[it] } ?: keys.first()
            keys.forEach { keyToGroup[it] = groupId }
            groups.getOrPut(groupId) { mutableListOf() }.add(entry)
        }
        return groups
    }

    private fun titleKeys(result: ApiSearchResult): List<String> = buildList {
        result.title.normalizeForSearch().takeIf { it.isNotEmpty() }?.let { add(it) }
        result.altTitle?.normalizeForSearch()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        if (isEmpty()) {
            result.externalId?.let { add("${result.source.lowercase()}:$it") }
        }
    }

    companion object {
        const val COOCCURRENCE_STEP = 0.25f
        const val COOCCURRENCE_CAP = 0.5f
        const val RATING_WEIGHT = 0.15f
        const val SEED_WEIGHT = 0.1f
    }
}
