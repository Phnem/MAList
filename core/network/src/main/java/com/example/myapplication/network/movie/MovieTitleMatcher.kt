package com.example.myapplication.network.movie

import kotlin.math.max

/** Единая консервативная policy для слабой title-based ступени дедупа и id-резолва. */
internal object MovieTitleMatcher {
    const val MATCH_THRESHOLD = 0.85

    fun isMatch(title: String, candidates: List<String>): Boolean =
        bestScore(title, candidates) >= MATCH_THRESHOLD

    fun bestScore(title: String, candidates: List<String>): Double {
        val normalized = normalize(title)
        if (normalized.isBlank()) return 0.0
        return candidates.asSequence()
            .map(::normalize)
            .filter(String::isNotBlank)
            .maxOfOrNull { score(normalized, it) }
            ?: 0.0
    }

    /** Компактная форма нужна только строгой original-title ступени. */
    fun exactKey(title: String): String = normalize(title).replace(" ", "")

    private fun score(a: String, b: String): Double {
        if (a == b) return 1.0
        val aTokens = a.split(' ').filter(String::isNotBlank).toSet()
        val bTokens = b.split(' ').filter(String::isNotBlank).toSet()
        if (aTokens.isNotEmpty() && bTokens.isNotEmpty()) {
            val (smaller, larger) = if (aTokens.size <= bTokens.size) aTokens to bTokens else bTokens to aTokens
            val allowSubset = smaller.size >= 2 || smaller.sumOf(String::length) >= 5
            if (allowSubset && smaller.all { it in larger }) return 0.92
        }

        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        if (short.length >= 4 && long.contains(short)) return 0.87

        val intersection = aTokens.intersect(bTokens).size.toDouble()
        val union = aTokens.union(bTokens).size.toDouble()
        val jaccard = if (union == 0.0) 0.0 else intersection / union
        return max(jaccard, levenshteinSimilarity(a, b))
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun levenshteinSimilarity(a: String, b: String): Double {
        val maxLength = max(a.length, b.length)
        if (maxLength == 0) return 1.0
        val previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in b.indices) {
                val above = previous[j + 1]
                previous[j + 1] = if (a[i] == b[j]) diagonal else 1 + minOf(diagonal, above, previous[j])
                diagonal = above
            }
        }
        return 1.0 - previous[b.length].toDouble() / maxLength
    }
}
