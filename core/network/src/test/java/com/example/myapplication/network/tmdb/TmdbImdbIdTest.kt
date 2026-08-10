package com.example.myapplication.network.tmdb

import com.example.myapplication.network.AppLanguage
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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * IMDb id — единственный адресный ключ Stremio-транспорта, поэтому его извлечение проверяется
 * отдельно от остального маппинга.
 */
class TmdbImdbIdTest {

    @Test
    fun `movie details expose the native imdb_id field`() = runBlocking {
        val engine = MockEngine {
            respond(MOVIE_WITH_IMDB, HttpStatusCode.OK, jsonHeaders)
        }

        val result = TmdbRemoteDataSource(client(engine)).movieDetails(1, AppLanguage.EN)

        assertEquals("tt1375666", (result as LookupResult.Found).value.imdbId)
    }

    @Test
    fun `tv details request external_ids because the tv payload has no imdb_id of its own`() =
        runBlocking {
            var appended: String? = null
            val engine = MockEngine { request ->
                appended = request.url.parameters["append_to_response"]
                respond(TV_WITH_EXTERNAL_IDS, HttpStatusCode.OK, jsonHeaders)
            }

            val result = TmdbRemoteDataSource(client(engine)).tvDetails(2, AppLanguage.EN)

            assertEquals("external_ids", appended)
            assertEquals("tt0412142", (result as LookupResult.Found).value.imdbId)
        }

    @Test
    fun `a missing imdb id stays null rather than becoming an empty string`() = runBlocking {
        val engine = MockEngine {
            respond("""{"id":3,"title":"TBA"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val result = TmdbRemoteDataSource(client(engine)).movieDetails(3, AppLanguage.EN)

        // An empty id would read as "already resolved" and stop enrichment from retrying.
        assertNull((result as LookupResult.Found).value.imdbId)
    }

    @Test
    fun `a blank imdb id is normalised to null`() = runBlocking {
        val engine = MockEngine {
            respond("""{"id":4,"title":"TBA","imdb_id":"  "}""", HttpStatusCode.OK, jsonHeaders)
        }

        val result = TmdbRemoteDataSource(client(engine)).movieDetails(4, AppLanguage.EN)

        assertNull((result as LookupResult.Found).value.imdbId)
    }

    @Test
    fun `tv details without external_ids do not fail the whole lookup`() = runBlocking {
        val engine = MockEngine {
            respond("""{"id":5,"name":"Severance"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val result = TmdbRemoteDataSource(client(engine)).tvDetails(5, AppLanguage.EN)

        assertNull((result as LookupResult.Found).value.imdbId)
        assertEquals("Severance", result.value.name)
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private companion object {
        const val MOVIE_WITH_IMDB = """
            {"id":1,"title":"Inception","imdb_id":"tt1375666","vote_average":8.4}
        """

        const val TV_WITH_EXTERNAL_IDS = """
            {
              "id":2,
              "name":"House",
              "in_production":false,
              "external_ids":{"imdb_id":"tt0412142","tvdb_id":73255}
            }
        """
    }
}
