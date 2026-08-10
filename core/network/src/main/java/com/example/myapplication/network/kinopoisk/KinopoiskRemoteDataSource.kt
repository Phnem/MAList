package com.example.myapplication.network.kinopoisk

import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.LookupResult
import com.example.myapplication.network.executeHttpLookup
import com.example.myapplication.network.flatMap
import com.example.myapplication.network.dto.KinopoiskFilmDto
import com.example.myapplication.network.dto.KinopoiskSearchResponseDto
import com.example.myapplication.network.dto.KinopoiskSeasonResponseDto
import com.phnem.vetro.network.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments

/** Typed adapter for the user's documented `kinopoiskapiunofficial.tech` catalog. */
class KinopoiskRemoteDataSource(
    private val client: HttpClient,
    private val apiKeyProvider: () -> String = { BuildConfig.KINOPOISK_API_KEY },
) {

    suspend fun searchMovie(query: String, year: Int? = null): LookupResult<List<ApiSearchResult>> =
        search(query, year, MOVIE_TYPES, categoryType = "MOVIE")

    suspend fun searchSeries(query: String, year: Int? = null): LookupResult<List<ApiSearchResult>> =
        search(query, year, SERIES_TYPES, categoryType = "SERIES")

    private suspend fun search(
        query: String,
        year: Int?,
        acceptedTypes: Set<String>,
        categoryType: String,
    ): LookupResult<List<ApiSearchResult>> = runRequest(notFoundById = false) { apiKey ->
        client.get {
            url {
                protocol = URLProtocol.HTTPS
                host = API_HOST
                appendPathSegments("api", "v2.1", "films", "search-by-keyword")
                parameter("keyword", query)
                parameter("page", "1")
            }
            header(API_KEY_HEADER, apiKey)
        }
    }.flatMap { response ->
        val results = response.body<KinopoiskSearchResponseDto>().films
            .asSequence()
            .filter { it.type?.uppercase() in acceptedTypes }
            .filter { year == null || it.year?.toIntOrNull() == year }
            .map { it.toApiSearchResult(categoryType) }
            .toList()
        if (results.isEmpty()) LookupResult.NoMatch else LookupResult.Found(results)
    }

    suspend fun details(id: Int): LookupResult<KinopoiskDetails> = runRequest(notFoundById = true) { apiKey ->
        client.get {
            url {
                protocol = URLProtocol.HTTPS
                host = API_HOST
                appendPathSegments("api", "v2.2", "films", id.toString())
            }
            header(API_KEY_HEADER, apiKey)
        }
    }.flatMap { response -> LookupResult.Found(response.body<KinopoiskFilmDto>().toDetails()) }

    /** Known catalogue layout only; callers must not write it into released-episode storage. */
    suspend fun seasons(id: Int): LookupResult<List<KinopoiskSeason>> = runRequest(notFoundById = true) { apiKey ->
        client.get {
            url {
                protocol = URLProtocol.HTTPS
                host = API_HOST
                appendPathSegments("api", "v2.2", "films", id.toString(), "seasons")
            }
            header(API_KEY_HEADER, apiKey)
        }
    }.flatMap { response ->
        LookupResult.Found(response.body<KinopoiskSeasonResponseDto>().items.map { it.toDomain() })
    }

    private suspend fun runRequest(
        notFoundById: Boolean,
        block: suspend (apiKey: String) -> HttpResponse,
    ): LookupResult<HttpResponse> {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            return LookupResult.Failure(
                cause = IllegalStateException("Kinopoisk API key is not configured"),
                retryable = false,
            )
        }
        return executeHttpLookup(
            providerName = "Kinopoisk",
            notFoundById = notFoundById,
        ) { block(apiKey) }
    }

    private companion object {
        const val API_HOST = "kinopoiskapiunofficial.tech"
        const val API_KEY_HEADER = "X-API-KEY"
        val MOVIE_TYPES = setOf("FILM")
        val SERIES_TYPES = setOf("TV_SERIES", "MINI_SERIES", "TV_SHOW")
    }
}
