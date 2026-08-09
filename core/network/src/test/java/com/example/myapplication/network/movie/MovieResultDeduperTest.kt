package com.example.myapplication.network.movie

import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.ExternalIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieResultDeduperTest {

    private fun result(
        title: String,
        year: Int? = null,
        tmdb: Int? = null,
        kinopoisk: Int? = null,
        source: String = "TMDB",
        categoryType: String = "MOVIE",
        originalTitle: String? = null,
    ) = ApiSearchResult(
        title = title,
        altTitle = null,
        posterUrl = null,
        episodes = 1,
        description = "",
        type = "Movie",
        genres = emptyList(),
        rating = null,
        source = source,
        categoryType = categoryType,
        externalId = (tmdb ?: kinopoisk)?.toString(),
        seasonYear = year,
        originalTitle = originalTitle,
        externalIds = ExternalIds(tmdb = tmdb, kinopoisk = kinopoisk),
    )

    @Test
    fun `same tmdb id merges into one entry`() {
        val kinopoiskHit = result("Дюна", year = 2021, kinopoisk = 55, tmdb = 438631, source = "Kinopoisk")
        val tmdbHit = result("Dune", year = 2021, tmdb = 438631, source = "TMDB")
        val merged = MovieResultDeduper.merge(listOf(kinopoiskHit, tmdbHit))
        assertEquals(1, merged.size)
        // Первый источник в списке побеждает по отображаемым полям, второй только доливает id.
        assertEquals("Дюна", merged.single().title)
        assertEquals(438631, merged.single().externalIds.tmdb)
        assertEquals(55, merged.single().externalIds.kinopoisk)
    }

    @Test
    fun `same kinopoisk id without a tmdb bridge still merges`() {
        val a = result("Слово пацана", year = 2023, kinopoisk = 999)
        val b = result("Слово пацана", year = 2023, kinopoisk = 999)
        assertEquals(1, MovieResultDeduper.merge(listOf(a, b)).size)
    }

    @Test
    fun `exact normalized title plus year merges without any shared id`() {
        val a = result("The Office", year = 2005, tmdb = 2316)
        val b = result("the office", year = 2005, kinopoisk = 4058)
        val merged = MovieResultDeduper.merge(listOf(a, b))
        assertEquals(1, merged.size)
    }

    @Test
    fun `same title different year does not merge (remake case)`() {
        val office2001 = result("The Office", year = 2001, tmdb = 2996)
        val office2005 = result("The Office", year = 2005, tmdb = 2316)
        val merged = MovieResultDeduper.merge(listOf(office2001, office2005))
        assertEquals(2, merged.size)
    }

    @Test
    fun `high confidence title typo with matching year merges on the fuzzy fallback step`() {
        val a = result("Oppenheimer", year = 2023, tmdb = 100)
        val b = result("Oppenhaimer", year = 2023, kinopoisk = 200)
        assertEquals(1, MovieResultDeduper.merge(listOf(a, b)).size)
    }

    @Test
    fun `normalized original title plus year merges localized cards`() {
        val ru = result("Сёгун", year = 2024, kinopoisk = 200, originalTitle = "Shōgun")
        val en = result("Shogun", year = 2024, tmdb = 100, originalTitle = "Shōgun!")
        assertEquals(1, MovieResultDeduper.merge(listOf(ru, en)).size)
    }

    @Test
    fun `same kinopoisk id cannot override conflicting canonical tmdb ids`() {
        val first = result("Example", year = 2024, tmdb = 100, kinopoisk = 200)
        val second = result("Example", year = 2024, tmdb = 101, kinopoisk = 200)
        assertEquals(2, MovieResultDeduper.merge(listOf(first, second)).size)
    }

    @Test
    fun `near boundary fuzzy match stays separate`() {
        val first = result("abcdefghij", year = 2024, tmdb = 100)
        val second = result("abcdefxxij", year = 2024, kinopoisk = 200)
        assertEquals(2, MovieResultDeduper.merge(listOf(first, second)).size)
    }

    @Test
    fun `same title and year with different media type stays separate`() {
        val movie = result("Fargo", year = 2014, tmdb = 100, categoryType = "MOVIE")
        val series = result("Fargo", year = 2014, kinopoisk = 200, categoryType = "SERIES")
        assertEquals(2, MovieResultDeduper.merge(listOf(movie, series)).size)
    }

    @Test
    fun `weak fuzzy match without a year in common does not merge`() {
        // Ни общего id, ни года -- ступень 3 и 4 обе требуют совпадающий год.
        val a = result("Dune", year = null, tmdb = 1)
        val b = result("Dune Part Two", year = null, tmdb = 2)
        assertEquals(2, MovieResultDeduper.merge(listOf(a, b)).size)
    }

    @Test
    fun `unrelated titles with the same year never merge`() {
        val a = result("Oppenheimer", year = 2023, tmdb = 1)
        val b = result("Barbie", year = 2023, tmdb = 2)
        assertEquals(2, MovieResultDeduper.merge(listOf(a, b)).size)
    }

    @Test
    fun `three way merge across all sources collapses to one entry`() {
        val kp = result("Опенгеймер", year = 2023, kinopoisk = 1, tmdb = 872585, source = "Kinopoisk")
        val tmdbRu = result("Оппенгеймер", year = 2023, tmdb = 872585, source = "TMDB")
        val merged = MovieResultDeduper.merge(listOf(kp, tmdbRu))
        assertTrue(merged.size == 1)
    }
}
