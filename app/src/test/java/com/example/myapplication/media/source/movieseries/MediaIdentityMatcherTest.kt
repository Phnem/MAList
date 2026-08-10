package com.example.myapplication.media.source.movieseries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdentityMatcherTest {

    private val house = MediaIdentity(
        tmdbId = 1408,
        imdbId = "tt0412142",
        kinopoiskId = 178710,
        title = "Doctor House",
        year = 2004,
    )

    @Test
    fun `tmdb id is the strongest evidence`() {
        val candidate = MediaIdentity(tmdbId = 1408, title = "Completely different name")

        assertEquals(
            IdentityMatch.Matched(MatchAccuracy.TMDB_ID),
            MediaIdentityMatcher.match(house, candidate),
        )
    }

    @Test
    fun `imdb id matches when tmdb is absent on one side`() {
        val candidate = MediaIdentity(imdbId = "tt0412142")

        assertEquals(
            IdentityMatch.Matched(MatchAccuracy.IMDB_ID),
            MediaIdentityMatcher.match(house, candidate),
        )
    }

    @Test
    fun `imdb ids compare case insensitively`() {
        val candidate = MediaIdentity(imdbId = "  TT0412142 ")

        assertEquals(
            IdentityMatch.Matched(MatchAccuracy.IMDB_ID),
            MediaIdentityMatcher.match(house, candidate),
        )
    }

    @Test
    fun `kinopoisk id matches when the stronger ids are absent`() {
        val candidate = MediaIdentity(kinopoiskId = 178710)

        assertEquals(
            IdentityMatch.Matched(MatchAccuracy.KINOPOISK_ID),
            MediaIdentityMatcher.match(house, candidate),
        )
    }

    @Test
    fun `title and year match when no ids are shared`() {
        val candidate = MediaIdentity(title = "doctor  house", year = 2004)

        assertEquals(
            IdentityMatch.Matched(MatchAccuracy.TITLE_AND_YEAR),
            MediaIdentityMatcher.match(house, candidate),
        )
    }

    @Test
    fun `title alone is the weakest usable evidence`() {
        val candidate = MediaIdentity(title = "Doctor-House")

        assertEquals(
            IdentityMatch.Matched(MatchAccuracy.TITLE_ONLY),
            MediaIdentityMatcher.match(house, candidate),
        )
    }

    @Test
    fun `the same title in a different year is rejected`() {
        val remake = MediaIdentity(title = "Doctor House", year = 2019)

        // Remakes share a name; accepting this is how the wrong title gets played.
        assertEquals(IdentityMatch.NoEvidence, MediaIdentityMatcher.match(house, remake))
    }

    @Test
    fun `a conflicting id disqualifies the candidate even when another id agrees`() {
        val candidate = MediaIdentity(tmdbId = 1408, imdbId = "tt9999999")

        assertEquals(IdentityMatch.Conflict, MediaIdentityMatcher.match(house, candidate))
    }

    @Test
    fun `a conflicting id disqualifies the candidate even when the title is identical`() {
        val candidate = MediaIdentity(tmdbId = 9999, title = "Doctor House", year = 2004)

        assertEquals(IdentityMatch.Conflict, MediaIdentityMatcher.match(house, candidate))
    }

    @Test
    fun `unrelated candidates report no evidence rather than conflict`() {
        val candidate = MediaIdentity(title = "Severance", year = 2022)

        assertEquals(IdentityMatch.NoEvidence, MediaIdentityMatcher.match(house, candidate))
    }

    @Test
    fun `an absent id on one side is not evidence either way`() {
        val candidate = MediaIdentity(tmdbId = null, title = "Doctor House", year = 2004)

        assertEquals(
            IdentityMatch.Matched(MatchAccuracy.TITLE_AND_YEAR),
            MediaIdentityMatcher.match(house, candidate),
        )
    }

    @Test
    fun `a blank imdb id counts as absent, not as an empty id`() {
        val wanted = MediaIdentity(imdbId = "  ", title = "Doctor House", year = 2004)
        val candidate = MediaIdentity(imdbId = "tt0412142", title = "Doctor House", year = 2004)

        assertEquals(
            IdentityMatch.Matched(MatchAccuracy.TITLE_AND_YEAR),
            MediaIdentityMatcher.match(wanted, candidate),
        )
    }

    @Test
    fun `selection returns the single id match`() {
        val candidates = listOf(
            MediaIdentity(tmdbId = 1408, title = "House MD"),
            MediaIdentity(title = "Severance", year = 2022),
        )

        assertEquals(
            candidates.first(),
            MediaIdentityMatcher.selectUnique(house, candidates) { it },
        )
    }

    @Test
    fun `selection refuses two candidates sharing the strongest accuracy`() {
        val candidates = listOf(
            MediaIdentity(tmdbId = 1408, title = "House copy A"),
            MediaIdentity(tmdbId = 1408, title = "House copy B"),
        )

        assertNull(MediaIdentityMatcher.selectUnique(house, candidates) { it })
    }

    @Test
    fun `a strong match wins over a weak one instead of being treated as ambiguous`() {
        val candidates = listOf(
            MediaIdentity(title = "Doctor House"),
            MediaIdentity(tmdbId = 1408, title = "Unrelated name"),
        )

        assertEquals(
            candidates[1],
            MediaIdentityMatcher.selectUnique(house, candidates) { it },
        )
    }

    @Test
    fun `two ambiguous title matches are refused even though a weaker tier is unique`() {
        val candidates = listOf(
            MediaIdentity(title = "Doctor House"),
            MediaIdentity(title = "doctor house"),
        )

        assertNull(MediaIdentityMatcher.selectUnique(house, candidates) { it })
    }

    @Test
    fun `conflicting candidates are never selected`() {
        val candidates = listOf(MediaIdentity(tmdbId = 9999, title = "Doctor House", year = 2004))

        assertNull(MediaIdentityMatcher.selectUnique(house, candidates) { it })
    }

    @Test
    fun `an empty candidate list selects nothing`() {
        assertNull(MediaIdentityMatcher.selectUnique(house, emptyList<MediaIdentity>()) { it })
    }

    @Test
    fun `episode coordinates must both line up`() {
        assertTrue(episodeMatches(2, 3, wantedSeason = 2, wantedEpisode = 3))
        assertFalse(episodeMatches(2, 4, wantedSeason = 2, wantedEpisode = 3))
        assertFalse(episodeMatches(1, 3, wantedSeason = 2, wantedEpisode = 3))
        assertFalse(episodeMatches(null, 3, wantedSeason = 2, wantedEpisode = 3))
    }

    @Test
    fun `accuracy ordering is strongest first`() {
        assertTrue(MatchAccuracy.TMDB_ID < MatchAccuracy.IMDB_ID)
        assertTrue(MatchAccuracy.IMDB_ID < MatchAccuracy.KINOPOISK_ID)
        assertTrue(MatchAccuracy.KINOPOISK_ID < MatchAccuracy.TITLE_AND_YEAR)
        assertTrue(MatchAccuracy.TITLE_AND_YEAR < MatchAccuracy.TITLE_ONLY)
    }
}
