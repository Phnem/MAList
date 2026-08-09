package com.example.myapplication.network.kinopoisk

import com.phnem.vetro.network.BuildConfig
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.LookupResult
import com.example.myapplication.network.dto.KinopoiskMovieDto
import com.example.myapplication.network.dto.KinopoiskSearchResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments

/**
 * RU-источник (по образцу `ShikimoriRemoteDataSource`) — сторонний `api.kinopoisk.dev`.
 * Kinopoisk.dev ищет вперемешку movie/tv-series/cartoon/anime/animated-series в одном
 * эндпоинте — поэтому методы типизированы по контенту на уровне сигнатуры ([searchMovie]/
 * [searchSeries]), не единым `search(query, type)`, где перепутать type было бы легко.
 */
class KinopoiskRemoteDataSource(private val client: HttpClient) {

    private fun apiKey(): String = BuildConfig.KINOPOISK_API_KEY

    suspend fun searchMovie(query: String, year: Int? = null): LookupResult<List<ApiSearchResult>> =
        search(query, type = "movie", year) { it.toApiSearchResult(categoryType = "MOVIE") }

    suspend fun searchSeries(query: String, year: Int? = null): LookupResult<List<ApiSearchResult>> =
        search(query, type = "tv-series", year) { it.toApiSearchResult(categoryType = "SERIES") }

    private suspend fun search(
        query: String,
        type: String,
        year: Int?,
        map: (KinopoiskMovieDto) -> ApiSearchResult,
    ): LookupResult<List<ApiSearchResult>> = runRequest {
        client.get {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.kinopoisk.dev"
                appendPathSegments("v1.4", "movie", "search")
                parameter("query", query)
                parameter("type", type)
                parameter("limit", "20")
                if (year != null) parameter("year", year)
            }
            header("X-API-KEY", apiKey())
        }
    }.map { response ->
        val results = response.body<KinopoiskSearchResponseDto>().docs.map(map)
        if (results.isEmpty()) LookupResult.NoMatch else LookupResult.Found(results)
    }

    suspend fun details(id: Int): LookupResult<KinopoiskDetails> = runRequest {
        client.get {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.kinopoisk.dev"
                appendPathSegments("v1.4", "movie", id.toString())
            }
            header("X-API-KEY", apiKey())
        }
    }.map { response -> LookupResult.Found(response.body<KinopoiskMovieDto>().toDetails()) }

    // ---- HTTP → LookupResult -------------------------------------------------------------

    private suspend fun runRequest(block: suspend () -> HttpResponse): LookupResult<HttpResponse> = try {
        val response = block()
        when {
            response.status.value in 200..299 -> LookupResult.Found(response)
            response.status == HttpStatusCode.NotFound -> LookupResult.NotFoundById
            else -> LookupResult.Failure(
                cause = IllegalStateException("Kinopoisk HTTP ${response.status.value}"),
                retryable = response.status.value in 500..599 || response.status == HttpStatusCode.TooManyRequests,
            )
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        LookupResult.Failure(cause = e, retryable = true)
    }

    private suspend fun <T, R> LookupResult<T>.map(transform: suspend (T) -> LookupResult<R>): LookupResult<R> = when (this) {
        is LookupResult.Found -> transform(value)
        is LookupResult.NoMatch -> LookupResult.NoMatch
        is LookupResult.NotFoundById -> LookupResult.NotFoundById
        is LookupResult.Failure -> this
    }
}
