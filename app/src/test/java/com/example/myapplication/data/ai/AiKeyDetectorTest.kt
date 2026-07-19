package com.example.myapplication.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiKeyDetectorTest {

    @Test
    fun anthropic_prefix_is_unique_and_beats_generic_sk() {
        assertEquals(
            listOf(AiProvider.ANTHROPIC),
            AiKeyDetector.detectCandidates("sk-ant-api03-abc123"),
        )
    }

    @Test
    fun openrouter_prefix_is_unique_and_beats_generic_sk() {
        assertEquals(
            listOf(AiProvider.OPENROUTER),
            AiKeyDetector.detectCandidates("sk-or-v1-abcdef"),
        )
    }

    @Test
    fun groq_prefix_is_unique() {
        assertEquals(
            listOf(AiProvider.GROQ),
            AiKeyDetector.detectCandidates("gsk_ABCdef123456"),
        )
    }

    @Test
    fun gemini_prefix_is_unique() {
        assertEquals(
            listOf(AiProvider.GEMINI),
            AiKeyDetector.detectCandidates("AIzaSyAbc-123_def456"),
        )
    }

    @Test
    fun generic_sk_is_ambiguous_openai_and_deepseek() {
        val candidates = AiKeyDetector.detectCandidates("sk-proj-abc123")
        assertTrue(AiProvider.OPENAI in candidates)
        assertTrue(AiProvider.DEEPSEEK in candidates)
        assertEquals(2, candidates.size)
    }

    @Test
    fun no_recognizable_prefix_falls_back_to_prefixless_providers() {
        // Cohere-подобный ключ без узнаваемого префикса.
        assertEquals(
            listOf(AiProvider.COHERE),
            AiKeyDetector.detectCandidates("Xy12aB34cD56eF78gH90ij"),
        )
    }

    @Test
    fun leading_and_trailing_whitespace_is_ignored() {
        assertEquals(
            listOf(AiProvider.ANTHROPIC),
            AiKeyDetector.detectCandidates("  sk-ant-key  "),
        )
    }

    @Test
    fun blank_key_yields_no_candidates() {
        assertTrue(AiKeyDetector.detectCandidates("   ").isEmpty())
    }

    @Test
    fun detectSingle_returns_unique_provider() {
        assertEquals(AiProvider.GROQ, AiKeyDetector.detectSingle("gsk_key"))
    }

    @Test
    fun detectSingle_null_when_ambiguous() {
        assertNull(AiKeyDetector.detectSingle("sk-plainkey"))
    }

    @Test
    fun fromId_roundtrips_all_providers() {
        for (provider in AiProvider.entries) {
            assertEquals(provider, AiProvider.fromId(provider.id))
        }
        assertNull(AiProvider.fromId("nonexistent"))
    }
}
