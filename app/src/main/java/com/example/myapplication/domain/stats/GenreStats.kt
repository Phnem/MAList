package com.example.myapplication.domain.stats

import com.example.myapplication.data.models.Anime

data class TagFrequencySlice(
    val tagId: String,
    val count: Int,
    /** Доля внутри топ-5: count / top5Sum. Круг на 100% = сумма пяти жанров. */
    val share: Float
)

data class DonutChartData(
    /** Сумма вхождений у топ-5; число в центре кольца. */
    val top5CountSum: Int,
    val slices: List<TagFrequencySlice>
)

data class BarChartEntry(
    val tagId: String,
    val averageRating: Double,
    val titleCount: Int
)

/**
 * Топ-5 тегов по частоте. Доли: count / top5Sum (без сегмента «Прочее»).
 */
fun buildDonutChartData(animeList: List<Anime>): DonutChartData? {
    val allTags = animeList.flatMap { a -> a.tags }
    if (allTags.isEmpty()) return null
    val byTag = allTags.groupingBy { it }.eachCount()
    val top5 = byTag.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(5)
    if (top5.isEmpty()) return null
    val top5Sum = top5.sumOf { it.value }
    if (top5Sum == 0) return null
    val slices = top5.map { (tag, c) ->
        TagFrequencySlice(tagId = tag, count = c, share = c.toFloat() / top5Sum)
    }
    return DonutChartData(top5CountSum = top5Sum, slices = slices)
}

/**
 * Средний рейтинг по тегу; только жанры с достаточной выборкой; топ-5 по max среднему, tie-break по tagId.
 */
fun buildBarChartData(animeList: List<Anime>): List<BarChartEntry> {
    val map = linkedMapOf<String, MutableList<Float>>()
    for (a in animeList) {
        for (t in a.tags) {
            map.getOrPut(t) { mutableListOf() }.add(a.rating)
        }
    }
    if (map.isEmpty()) return emptyList()
    val totalTitles = animeList.size
    val minThreshold = if (totalTitles <= 50) 5 else 10
    return map
        .map { (tag, ratings) ->
            BarChartEntry(
                tagId = tag,
                averageRating = ratings.map { it.toDouble() }.average(),
                titleCount = ratings.size
            )
        }
        .filter { it.titleCount >= minThreshold }
        .sortedWith(
            compareByDescending<BarChartEntry> { it.averageRating }
                .thenBy { it.tagId }
        )
        .take(5)
}
