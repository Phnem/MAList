package com.example.myapplication.media.source

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

/**
 * Thin HTTP helper patterned after Animetail AnimeHttpSource (Apache-2.0 inspired contract),
 * without Injekt — uses the shared Ktor client from core:network.
 */
abstract class VetroHttpSource(
    protected val client: HttpClient,
) {
    abstract val name: String
    abstract val baseUrl: String

    open fun headersBuilder(): Map<String, String> = mapOf(
        HttpHeaders.UserAgent to DEFAULT_UA,
        HttpHeaders.AcceptLanguage to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
    )

    protected suspend fun getText(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String {
        val merged = SanitizeHeaders.sanitize(headersBuilder() + extraHeaders)
        return client.get(url) {
            headers {
                merged.forEach { (k, v) -> header(k, v) }
            }
        }.bodyAsText()
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
        /** TitleMatcher threshold used by SourceEngine for search disambiguation. */
        const val TITLE_MATCH_THRESHOLD = 0.91
    }
}
