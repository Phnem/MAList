package com.example.myapplication.network.kinopoisk

import com.example.myapplication.network.LookupResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KinopoiskRemoteDataSourceTest {

    @Test
    fun `series search uses supplied api host header and maps documented response`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("kinopoiskapiunofficial.tech", request.url.host)
            assertEquals("/api/v2.1/films/search-by-keyword", request.url.encodedPath)
            assertEquals("Доктор Хаус", request.url.parameters["keyword"])
            assertEquals("test-key", request.headers["X-API-KEY"])
            respond(
                content = SEARCH_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val source = KinopoiskRemoteDataSource(client(engine), apiKeyProvider = { "test-key" })

        val result = source.searchSeries("Доктор Хаус")

        assertTrue(result is LookupResult.Found)
        val item = (result as LookupResult.Found).value.single()
        assertEquals("Доктор Хаус", item.title)
        assertEquals("House, M.D.", item.titleEn)
        assertEquals(178710, item.externalIds.kinopoisk)
        assertEquals(2004, item.seasonYear)
        assertEquals(88, item.rating)
    }

    @Test
    fun `movie and series search filter mixed documented result types locally`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = MIXED_SEARCH_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val source = KinopoiskRemoteDataSource(client(engine), apiKeyProvider = { "test-key" })

        val movie = source.searchMovie("Дюна") as LookupResult.Found
        val series = source.searchSeries("Дюна") as LookupResult.Found

        assertEquals(listOf("FILM"), movie.value.map { it.type })
        assertEquals(listOf("TV"), series.value.map { it.type })
    }

    @Test
    fun `details and seasons use documented endpoints without conflating known episodes`() = runBlocking {
        val engine = MockEngine { request ->
            val content = when (request.url.encodedPath) {
                "/api/v2.2/films/178710" -> DETAILS_RESPONSE
                "/api/v2.2/films/178710/seasons" -> SEASONS_RESPONSE
                else -> error("Unexpected ${request.url}")
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val source = KinopoiskRemoteDataSource(client(engine), apiKeyProvider = { "test-key" })

        val details = (source.details(178710) as LookupResult.Found).value
        val seasons = (source.seasons(178710) as LookupResult.Found).value

        assertEquals("tt0412142", details.externalImdbId)
        assertEquals(8.8, details.ratingKp!!, 0.0001)
        assertEquals("Доктор Хаус", details.nameRu)
        assertEquals("House, M.D.", details.nameEn)
        assertEquals("House", details.originalName)
        assertEquals(2004, details.year)
        assertEquals(8, seasons.size)
        assertEquals(22, seasons.first().episodes.size)
        assertEquals("2024-01-01", seasons.first().episodes.first().releaseDate)
    }

    @Test
    fun `blank key fails before network request and never leaks a credential`() = runBlocking {
        var requested = false
        val engine = MockEngine {
            requested = true
            error("must not request")
        }
        val source = KinopoiskRemoteDataSource(client(engine), apiKeyProvider = { "" })

        val result = source.searchMovie("Dune")

        assertTrue(result is LookupResult.Failure)
        assertFalse((result as LookupResult.Failure).retryable)
        assertFalse(requested)
        assertFalse(result.cause.message.orEmpty().contains("test-key"))
    }

    @Test
    fun `429 and 5xx are retryable while 401 is configuration failure`() = runBlocking {
        suspend fun resultFor(status: HttpStatusCode): LookupResult<*> {
            val source = KinopoiskRemoteDataSource(
                client(MockEngine { respond("{}", status) }),
                apiKeyProvider = { "test-key" },
            )
            return source.searchMovie("Dune")
        }

        assertTrue((resultFor(HttpStatusCode.TooManyRequests) as LookupResult.Failure).retryable)
        assertTrue((resultFor(HttpStatusCode.ServiceUnavailable) as LookupResult.Failure).retryable)
        assertFalse((resultFor(HttpStatusCode.Unauthorized) as LookupResult.Failure).retryable)
    }

    @Test
    fun `search 404 is protocol failure while details 404 means stale saved id`() = runBlocking {
        val engine = MockEngine { respond("{}", HttpStatusCode.NotFound) }
        val source = KinopoiskRemoteDataSource(client(engine), apiKeyProvider = { "test-key" })

        val search = source.searchMovie("Dune")
        val details = source.details(1)

        assertTrue(search is LookupResult.Failure)
        assertFalse((search as LookupResult.Failure).retryable)
        assertTrue(details is LookupResult.NotFoundById)
    }

    private fun client(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private companion object {
        const val SEARCH_RESPONSE = """
            {
              "keyword": "Доктор Хаус",
              "pagesCount": 1,
              "searchFilmsCountResult": 1,
              "films": [{
                "filmId": 178710,
                "nameRu": "Доктор Хаус",
                "nameEn": "House, M.D.",
                "type": "TV_SERIES",
                "year": "2004",
                "description": "Medical drama",
                "countries": [{"country": "США"}],
                "genres": [{"genre": "драма"}],
                "rating": "8.8",
                "posterUrl": "https://example/house.jpg",
                "posterUrlPreview": "https://example/house-small.jpg"
              }]
            }
        """

        const val MIXED_SEARCH_RESPONSE = """
            {
              "films": [
                {"filmId": 1, "nameRu": "Фильм", "type": "FILM", "year": "2021"},
                {"filmId": 2, "nameRu": "Сериал", "type": "TV_SERIES", "year": "2021"}
              ]
            }
        """

        const val DETAILS_RESPONSE = """
            {
              "kinopoiskId": 178710,
              "imdbId": "tt0412142",
              "nameRu": "Доктор Хаус",
              "nameEn": "House, M.D.",
              "nameOriginal": "House",
              "posterUrl": "https://example/house.jpg",
              "posterUrlPreview": "https://example/house-small.jpg",
              "ratingKinopoisk": 8.8,
              "description": "Medical drama",
              "type": "TV_SERIES",
              "year": 2004,
              "countries": [{"country": "США"}],
              "genres": [{"genre": "драма"}]
            }
        """

        val SEASONS_RESPONSE = """
            {
              "total": 8,
              "items": [
                {"number": 1, "episodes": [
                  ${episode(1, 1)},
                  ${episode(1, 2)}
                  ${",${(3..22).joinToString(",") { episode(1, it) }}"}
                ]},
                ${
                    (2..8).joinToString(",") { season ->
                        "{\"number\":$season,\"episodes\":[${episode(season, 1)}]}"
                    }
                }
              ]
            }
        """

        private fun episode(season: Int, episode: Int): String =
            "{\"seasonNumber\":$season,\"episodeNumber\":$episode," +
                "\"nameRu\":\"Серия $episode\",\"releaseDate\":\"2024-01-01\"}"
    }
}
