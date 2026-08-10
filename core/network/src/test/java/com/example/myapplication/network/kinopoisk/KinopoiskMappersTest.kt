package com.example.myapplication.network.kinopoisk

import com.example.myapplication.network.dto.KinopoiskFilmDto
import com.example.myapplication.network.dto.KinopoiskGenreDto
import com.example.myapplication.network.dto.KinopoiskSearchFilmDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KinopoiskMappersTest {

    @Test
    fun `search carries kinopoisk identity without inventing a tmdb bridge`() {
        val result = searchFilm(filmId = 123, nameRu = "Дюна").toApiSearchResult("MOVIE")

        assertEquals(123, result.externalIds.kinopoisk)
        assertNull(result.externalIds.tmdb)
    }

    @Test
    fun `string rating scales from 0 to 10 into app 0 to 100`() {
        val result = searchFilm(rating = "8.3").toApiSearchResult("MOVIE")

        assertEquals(83, result.rating)
    }

    @Test
    fun `russian title leads while english title remains localized alias`() {
        val result = searchFilm(nameRu = "1+1", nameEn = "The Intouchables")
            .toApiSearchResult("MOVIE")

        assertEquals("1+1", result.title)
        assertEquals("The Intouchables", result.titleEn)
        assertEquals("1+1", result.titleRu)
    }

    @Test
    fun `english title is a safe display fallback when russian is absent`() {
        val result = searchFilm(nameRu = null, nameEn = "Dune").toApiSearchResult("MOVIE")

        assertEquals("Dune", result.title)
    }

    @Test
    fun `movie episodes is one while series remains unknown`() {
        assertEquals(1, searchFilm().toApiSearchResult("MOVIE").episodes)
        assertEquals(0, searchFilm().toApiSearchResult("SERIES").episodes)
    }

    @Test
    fun `blank poster is absent and genre names are flattened`() {
        val result = searchFilm(
            posterUrl = "",
            genres = listOf(KinopoiskGenreDto("драма"), KinopoiskGenreDto(null)),
        ).toApiSearchResult("MOVIE")

        assertNull(result.posterUrl)
        assertEquals(listOf("драма"), result.genres)
    }

    @Test
    fun `year string is parsed for dedup`() {
        assertEquals(2021, searchFilm(year = "2021").toApiSearchResult("MOVIE").seasonYear)
        assertNull(searchFilm(year = "unknown").toApiSearchResult("MOVIE").seasonYear)
    }

    @Test
    fun `details preserve imdb bridge and native kp rating`() {
        val details = KinopoiskFilmDto(
            kinopoiskId = 178710,
            imdbId = "tt0412142",
            nameRu = "Доктор Хаус",
            nameEn = "House, M.D.",
            nameOriginal = "House",
            year = 2004,
            ratingKinopoisk = 8.8,
            genres = listOf(KinopoiskGenreDto("драма")),
        ).toDetails()

        assertEquals("tt0412142", details.externalImdbId)
        assertEquals("Доктор Хаус", details.nameRu)
        assertEquals("House, M.D.", details.nameEn)
        assertEquals("House", details.originalName)
        assertEquals(2004, details.year)
        assertEquals(8.8, details.ratingKp!!, 0.0001)
        assertEquals(listOf("драма"), details.genres)
    }

    private fun searchFilm(
        filmId: Int = 1,
        nameRu: String? = "X",
        nameEn: String? = null,
        year: String? = null,
        rating: String? = null,
        posterUrl: String? = null,
        genres: List<KinopoiskGenreDto> = emptyList(),
    ) = KinopoiskSearchFilmDto(
        filmId = filmId,
        nameRu = nameRu,
        nameEn = nameEn,
        type = "FILM",
        year = year,
        rating = rating,
        posterUrl = posterUrl,
        genres = genres,
    )
}
