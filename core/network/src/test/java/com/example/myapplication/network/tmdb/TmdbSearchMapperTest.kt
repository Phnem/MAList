package com.example.myapplication.network.tmdb

import com.example.myapplication.network.dto.TmdbSearchResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbSearchMapperTest {

    @Test
    fun `movie result reads title and release_date year`() {
        val dto = TmdbSearchResultDto(
            id = 1,
            title = "Дюна",
            originalTitle = "Dune",
            releaseDate = "2021-09-15",
        )
        val result = dto.toApiSearchResult("MOVIE")
        assertEquals("Дюна", result.title)
        assertEquals("Dune", result.originalTitle)
        assertEquals(2021, result.seasonYear)
        assertEquals(1, result.episodes)
        assertEquals(1, result.externalIds.tmdb)
    }

    @Test
    fun `tv result falls back to name and first_air_date year`() {
        val dto = TmdbSearchResultDto(id = 2, name = "Severance", firstAirDate = "2022-02-18")
        val result = dto.toApiSearchResult("SERIES")
        assertEquals("Severance", result.title)
        assertEquals(2022, result.seasonYear)
        assertEquals(0, result.episodes)
    }

    @Test
    fun `missing date leaves year null instead of crashing`() {
        val dto = TmdbSearchResultDto(id = 3, title = "TBA")
        assertNull(dto.toApiSearchResult("MOVIE").seasonYear)
    }
}
