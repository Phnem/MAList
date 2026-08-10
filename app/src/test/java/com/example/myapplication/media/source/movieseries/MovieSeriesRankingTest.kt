package com.example.myapplication.media.source.movieseries

import com.example.myapplication.media.source.VetroHoster
import com.example.myapplication.media.source.VetroVideo
import com.example.myapplication.network.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieSeriesRankingTest {

    @Test
    fun `identification strength outranks quality`() {
        val ranked = rank(
            candidate("weak-id", accuracy = MatchAccuracy.TITLE_ONLY, resolution = 2160),
            candidate("strong-id", accuracy = MatchAccuracy.TMDB_ID, resolution = 720),
        )

        // A 4K stream of the wrong title is worthless.
        assertEquals(listOf("strong-id", "weak-id"), ranked.map { it.providerName })
    }

    @Test
    fun `a missing accuracy is treated as the weakest evidence`() {
        val ranked = rank(
            candidate("unknown", accuracy = null, resolution = 1080),
            candidate("title-only", accuracy = MatchAccuracy.TITLE_ONLY, resolution = 1080),
        )

        assertEquals(listOf("title-only", "unknown"), ranked.map { it.providerName })
    }

    @Test
    fun `the requested language wins at equal accuracy`() {
        val ranked = rank(
            candidate("en", accuracy = MatchAccuracy.TMDB_ID, language = AppLanguage.EN),
            candidate("ru", accuracy = MatchAccuracy.TMDB_ID, language = AppLanguage.RU),
            requested = AppLanguage.RU,
        )

        assertEquals(listOf("ru", "en"), ranked.map { it.providerName })
    }

    @Test
    fun `a language-agnostic candidate is not demoted`() {
        val ranked = rank(
            candidate("wrong-language", accuracy = MatchAccuracy.TMDB_ID, language = AppLanguage.EN),
            candidate("personal", accuracy = MatchAccuracy.TMDB_ID, language = null),
            requested = AppLanguage.RU,
        )

        assertEquals(listOf("personal", "wrong-language"), ranked.map { it.providerName })
    }

    @Test
    fun `an unhealthy provider sinks below a healthy one offering the same thing`() {
        val ranked = rank(
            candidate("failing", accuracy = MatchAccuracy.TMDB_ID, healthPenalty = 5),
            candidate("healthy", accuracy = MatchAccuracy.TMDB_ID, healthPenalty = 0),
        )

        assertEquals(listOf("healthy", "failing"), ranked.map { it.providerName })
    }

    @Test
    fun `the closest quality to the request wins`() {
        val ranked = rank(
            candidate("low", accuracy = MatchAccuracy.TMDB_ID, resolution = 480),
            candidate("target", accuracy = MatchAccuracy.TMDB_ID, resolution = 1080),
            candidate("high", accuracy = MatchAccuracy.TMDB_ID, resolution = 2160),
            preferred = 1080,
        )

        assertEquals("target", ranked.first().providerName)
    }

    @Test
    fun `latency breaks a tie that nothing else settles`() {
        val ranked = rank(
            candidate("slow", accuracy = MatchAccuracy.TMDB_ID, elapsedMs = 4_000),
            candidate("fast", accuracy = MatchAccuracy.TMDB_ID, elapsedMs = 200),
        )

        assertEquals(listOf("fast", "slow"), ranked.map { it.providerName })
    }

    @Test
    fun `ranking is deterministic for otherwise identical candidates`() {
        val a = candidate("b-provider", accuracy = MatchAccuracy.TMDB_ID)
        val b = candidate("a-provider", accuracy = MatchAccuracy.TMDB_ID)

        assertEquals(
            MovieSeriesRanking.rank(listOf(a, b), 1080, AppLanguage.RU).map { it.providerName },
            MovieSeriesRanking.rank(listOf(b, a), 1080, AppLanguage.RU).map { it.providerName },
        )
    }

    @Test
    fun `duplicate stream urls collapse to one candidate`() {
        val ranked = rank(
            candidate("first", url = "https://cdn.example/x.m3u8"),
            candidate("second", url = "https://cdn.example/x.m3u8"),
        )

        assertEquals(1, ranked.size)
    }

    @Test
    fun `resolution is read from the label when the field is absent`() {
        val ranked = rank(
            candidate("labelled", accuracy = MatchAccuracy.TMDB_ID, resolution = null, label = "720p"),
            candidate("target", accuracy = MatchAccuracy.TMDB_ID, resolution = null, label = "1080p"),
            preferred = 1080,
        )

        assertEquals("target", ranked.first().providerName)
    }

    @Test
    fun `several providers for one episode all survive ranking`() {
        // The brief's example: one episode offered by three providers at different qualities.
        val ranked = rank(
            candidate("Kodik", accuracy = MatchAccuracy.TMDB_ID, resolution = 1080, translation = "LostFilm"),
            candidate("ProviderB", accuracy = MatchAccuracy.TMDB_ID, resolution = 1080, translation = "Novafilm"),
            candidate("ProviderC", accuracy = MatchAccuracy.IMDB_ID, resolution = 720),
        )

        assertEquals(3, ranked.size)
        assertEquals(MatchAccuracy.IMDB_ID, ranked.last().accuracy)
    }

    @Test
    fun `candidates keep the hoster name as the translation label`() {
        val candidates = buildCandidates(
            providerId = ProviderId("kodik"),
            providerName = "Kodik",
            hosters = listOf(
                VetroHoster(
                    name = "LostFilm",
                    videos = listOf(VetroVideo(url = "https://cdn.example/a.m3u8", label = "1080p")),
                )
            ),
        )

        assertEquals("LostFilm", candidates.single().translation)
        assertEquals("LostFilm", candidates.single().video.sourceName)
    }

    @Test
    fun `a hoster named after the provider is not treated as a translation`() {
        val candidates = buildCandidates(
            providerId = ProviderId("jellyfin"),
            providerName = "Jellyfin",
            hosters = listOf(
                VetroHoster(
                    name = "Jellyfin",
                    videos = listOf(VetroVideo(url = "https://home.example/a.mp4", label = "Auto")),
                )
            ),
        )

        assertEquals(null, candidates.single().translation)
    }

    @Test
    fun `ranked candidates regroup into hosters preserving order`() {
        val hosters = rank(
            candidate("A", accuracy = MatchAccuracy.TMDB_ID, translation = "LostFilm", url = "https://a.example/1.m3u8"),
            candidate("B", accuracy = MatchAccuracy.TITLE_ONLY, translation = "Novafilm", url = "https://b.example/1.m3u8"),
        ).toHosters()

        assertEquals(listOf("LostFilm", "Novafilm"), hosters.map { it.name })
    }

    private fun rank(
        vararg candidates: MovieSeriesCandidate,
        preferred: Int = 1080,
        requested: AppLanguage = AppLanguage.RU,
    ) = MovieSeriesRanking.rank(candidates.toList(), preferred, requested)

    private fun candidate(
        providerName: String,
        accuracy: MatchAccuracy? = MatchAccuracy.TMDB_ID,
        language: AppLanguage? = null,
        resolution: Int? = 1080,
        label: String = "${resolution ?: 0}p",
        elapsedMs: Long = 0,
        healthPenalty: Int = 0,
        translation: String? = null,
        url: String = "https://cdn.example/$providerName.m3u8",
    ) = MovieSeriesCandidate(
        providerId = ProviderId(providerName),
        providerName = providerName,
        video = VetroVideo(url = url, label = label, resolution = resolution),
        accuracy = accuracy,
        language = language,
        translation = translation,
        elapsedMs = elapsedMs,
        healthPenalty = healthPenalty,
    )
}
