package com.example.myapplication.domain.recommendations

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualSimilarityTest {

    @Test
    fun `best match wins over a weaker one among several reference covers`() {
        val candidate = setOf("pastel", "slice of life", "watercolor")
        val closeMatch = setOf("pastel", "slice of life", "chibi")
        val farMatch = setOf("dark fantasy", "mecha")

        val score = visualSimilarityScore(candidate, listOf(farMatch, closeMatch))

        // intersection=2 (pastel, slice of life), union=4 → 0.5
        assertEquals(0.5f, score, 0.0001f)
    }

    @Test
    fun `no overlap scores zero`() {
        assertEquals(
            0f,
            visualSimilarityScore(setOf("mecha"), listOf(setOf("pastel", "slice of life"))),
        )
    }

    @Test
    fun `empty candidate or empty reference list scores zero, never crashes`() {
        assertEquals(0f, visualSimilarityScore(emptySet(), listOf(setOf("pastel"))))
        assertEquals(0f, visualSimilarityScore(setOf("pastel"), emptyList()))
        assertEquals(0f, visualSimilarityScore(setOf("pastel"), listOf(emptySet())))
    }
}
