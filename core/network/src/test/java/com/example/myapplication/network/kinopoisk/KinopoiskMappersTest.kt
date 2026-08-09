package com.example.myapplication.network.kinopoisk

import com.example.myapplication.network.dto.KinopoiskExternalIdDto
import com.example.myapplication.network.dto.KinopoiskGenreDto
import com.example.myapplication.network.dto.KinopoiskMovieDto
import com.example.myapplication.network.dto.KinopoiskPosterDto
import com.example.myapplication.network.dto.KinopoiskRatingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KinopoiskMappersTest {

    @Test
    fun `maps tmdb bridge id into ExternalIds alongside kinopoisk id`() {
        val dto = KinopoiskMovieDto(
            id = 123,
            name = "Дюна",
            externalId = KinopoiskExternalIdDto(tmdb = 438631, imdb = "tt1160419"),
        )
        val result = dto.toApiSearchResult(categoryType = "MOVIE")
        assertEquals(123, result.externalIds.kinopoisk)
        assertEquals(438631, result.externalIds.tmdb)
    }

    @Test
    fun `missing externalId leaves tmdb bridge null without crashing`() {
        val dto = KinopoiskMovieDto(id = 5, name = "Тест")
        val result = dto.toApiSearchResult(categoryType = "SERIES")
        assertEquals(5, result.externalIds.kinopoisk)
        assertNull(result.externalIds.tmdb)
    }

    @Test
    fun `kp rating 0 to 10 scales to the app's 0 to 100 rating`() {
        val dto = KinopoiskMovieDto(id = 1, name = "X", rating = KinopoiskRatingDto(kp = 8.3))
        assertEquals(83, dto.toApiSearchResult("MOVIE").rating)
    }

    @Test
    fun `falls back to alternativeName when name is absent`() {
        val dto = KinopoiskMovieDto(id = 1, name = null, alternativeName = "Dune")
        assertEquals("Dune", dto.toApiSearchResult("MOVIE").title)
    }

    @Test
    fun `movie episodes is always 1, series defaults to 0`() {
        val movie = KinopoiskMovieDto(id = 1, name = "M")
        val series = KinopoiskMovieDto(id = 2, name = "S")
        assertEquals(1, movie.toApiSearchResult("MOVIE").episodes)
        assertEquals(0, series.toApiSearchResult("SERIES").episodes)
    }

    @Test
    fun `poster url comes from poster field, blank is treated as absent`() {
        val withPoster = KinopoiskMovieDto(id = 1, name = "X", poster = KinopoiskPosterDto(url = "https://example/x.jpg"))
        val blankPoster = KinopoiskMovieDto(id = 2, name = "Y", poster = KinopoiskPosterDto(url = ""))
        assertEquals("https://example/x.jpg", withPoster.toApiSearchResult("MOVIE").posterUrl)
        assertNull(blankPoster.toApiSearchResult("MOVIE").posterUrl)
    }

    @Test
    fun `genres map to plain names, nulls filtered out`() {
        val dto = KinopoiskMovieDto(id = 1, name = "X", genres = listOf(KinopoiskGenreDto("драма"), KinopoiskGenreDto(null)))
        assertEquals(listOf("драма"), dto.toApiSearchResult("MOVIE").genres)
    }

    @Test
    fun `release year is carried through for dedup`() {
        val dto = KinopoiskMovieDto(id = 1, name = "X", year = 2021)
        assertEquals(2021, dto.toApiSearchResult("MOVIE").seasonYear)
    }

    @Test
    fun `english locale uses enName only while alternative stays original title`() {
        val withoutEnglish = KinopoiskMovieDto(
            id = 1,
            name = "1+1",
            enName = null,
            alternativeName = "Intouchables",
        ).toApiSearchResult("MOVIE")
        assertNull(withoutEnglish.titleEn)
        assertEquals("Intouchables", withoutEnglish.originalTitle)

        val withEnglish = KinopoiskMovieDto(
            id = 2,
            name = "1+1",
            enName = "The Intouchables",
            alternativeName = "Intouchables",
        ).toApiSearchResult("MOVIE")
        assertEquals("The Intouchables", withEnglish.titleEn)
    }

    @Test
    fun `toDetails preserves kp rating unscaled for repair-level consumers`() {
        val dto = KinopoiskMovieDto(id = 1, name = "X", rating = KinopoiskRatingDto(kp = 7.5))
        assertEquals(7.5, dto.toDetails().ratingKp!!, 0.0001)
    }
}
