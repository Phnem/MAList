package com.example.myapplication.media.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JutSuTitleSearchTest {

    @Test
    fun `aliases are tried russian then main then english without duplicates`() {
        assertEquals(
            listOf("Тетрадь смерти", "Death Note"),
            orderedJutSuAliases(
                titleRu = "Тетрадь смерти",
                title = "Death Note",
                titleEn = "Death Note",
            ),
        )
    }

    @Test
    fun `search queries retry punctuation heavy alias as words`() {
        assertEquals(
            listOf(
                "Повар-боец Сома",
                "Повар боец Сома",
                "Food Wars!",
                "Food Wars",
            ),
            jutSuSearchQueries(
                listOf("Повар-боец Сома", "Food Wars!"),
            ),
        )
    }

    @Test
    fun `best candidate at threshold is accepted`() {
        val selected = selectJutSuTitleCandidate(
            localTitles = listOf("Тетрадь смерти", "Death Note"),
            candidates = listOf(
                JutSuTitleCandidate("Наруто", "https://jut.su/naruuto/"),
                JutSuTitleCandidate("Death Note", "https://jut.su/bookofd/"),
            ),
        )

        assertEquals("https://jut.su/bookofd/", selected?.url)
    }

    @Test
    fun `candidate below point ninety one is rejected`() {
        val selected = selectJutSuTitleCandidate(
            localTitles = listOf("Death Note"),
            candidates = listOf(
                // Levenshtein similarity is 10/11 = 0.909..., deliberately above the
                // repository-wide 0.85 threshold but below the media threshold 0.91.
                JutSuTitleCandidate("Death Notes", "https://jut.su/death-notes/"),
            ),
        )

        assertNull(selected)
    }

    @Test
    fun `search result html yields only local title pages`() {
        val candidates = parseJutSuTitleCandidates(
            html = """
                <div id="dle-content">
                  <a href="/bookofd/" title="Death Note">Тетрадь смерти</a>
                  <a href="/search/">Search</a>
                  <a href="https://foreign.example/naruto/">Naruto</a>
                  <a href="/bookofd/episode-1.html">Episode</a>
                </div>
            """.trimIndent(),
            baseUrl = "https://jut.su",
        )

        assertEquals(
            listOf(JutSuTitleCandidate("Тетрадь смерти", "https://jut.su/bookofd/", listOf("Death Note"))),
            candidates,
        )
    }

    @Test
    fun `javascript landing falls through to normalized direct redirect`() {
        val selected = selectJutSuSearchResponse(
            localTitles = listOf("Food Wars!", "Shokugeki no Souma"),
            baseUrl = "https://jut.su",
            responses = listOf(
                JutSuSearchResponse(
                    finalUrl = "https://jut.su/search/",
                    html = "<script>window.searchResult = loadResults()</script>",
                ),
                JutSuSearchResponse(
                    finalUrl = "https://jut.su/shokugeki-no-souma/",
                    html = """
                        <html>
                          <head><meta itemprop="name" content="Shokugeki no Souma"></head>
                          <body><h1>Food Wars!</h1></body>
                        </html>
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals("https://jut.su/shokugeki-no-souma/", selected?.url)
    }
}
