package com.example.myapplication.domain.stats

import com.example.myapplication.network.AppLanguage

internal data class StatsPhraseGroupKey(
    val language: AppLanguage,
    val bucketTag: String
)

/**
 * Парсинг строк каталога; вынесено для unit-тестов без Android assets.
 */
internal object StatsPhraseLineParser {
    private val LINE_REGEX = Regex("""^\((RU|EN)\)\((02|24|45)\)\((S|R)\)\s+(.+)$""")

    fun parseLines(rawLines: Sequence<String>): Map<StatsPhraseGroupKey, List<StatsPhraseLine>> {
        val map = linkedMapOf<StatsPhraseGroupKey, MutableList<StatsPhraseLine>>()
        for (raw in rawLines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val match = LINE_REGEX.matchEntire(line) ?: continue
            val langToken = match.groupValues[1]
            val bucket = match.groupValues[2]
            val subToken = match.groupValues[3]
            val template = match.groupValues[4]
            val language = when (langToken) {
                "RU" -> AppLanguage.RU
                "EN" -> AppLanguage.EN
                else -> continue
            }
            val kind = when (subToken) {
                "R" -> StatsSubstitutionKind.Rating
                "S" -> StatsSubstitutionKind.Series
                else -> continue
            }
            val key = StatsPhraseGroupKey(language, bucket)
            map.getOrPut(key) { mutableListOf() }.add(StatsPhraseLine(template = template, substitutionKind = kind))
        }
        return map.mapValues { (_, v) -> v.toList() }
    }
}
