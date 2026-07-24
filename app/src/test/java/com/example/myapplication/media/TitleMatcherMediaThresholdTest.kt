package com.example.myapplication.media

import com.example.myapplication.sync.TitleMatcher
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatcherMediaThresholdTest {
    @Test
    fun bleachAmbiguousDoesNotMatchRandom() {
        val score = TitleMatcher.bestScore("Bleach", listOf("Bleach: Thousand-Year Blood War", "Naruto"))
        // Same franchise should score high; we only assert TYBW is preferred over Naruto
        val tybw = TitleMatcher.bestScore("Bleach", listOf("Bleach: Thousand-Year Blood War"))
        val naruto = TitleMatcher.bestScore("Bleach", listOf("Naruto"))
        assertTrue(tybw > naruto)
        assertTrue(tybw >= 0.85)
    }

    @Test
    fun exactRuTitleMatches() {
        val score = TitleMatcher.bestScore("Магическая битва", listOf("Магическая битва", "Jujutsu Kaisen"))
        assertTrue(score >= 0.91)
    }
}
