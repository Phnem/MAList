package com.example.myapplication.network

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

/** Shared HTTP outcome policy for typed catalog lookups. */
internal suspend fun executeHttpLookup(
    providerName: String,
    notFoundById: Boolean,
    block: suspend () -> HttpResponse,
): LookupResult<HttpResponse> = try {
    val response = block()
    when {
        response.status.value in 200..299 -> LookupResult.Found(response)
        response.status == HttpStatusCode.NotFound && notFoundById -> LookupResult.NotFoundById
        else -> LookupResult.Failure(
            cause = IllegalStateException("$providerName HTTP ${response.status.value}"),
            retryable = response.status == HttpStatusCode.TooManyRequests ||
                response.status.value in 500..599,
        )
    }
} catch (error: Exception) {
    if (error is CancellationException) throw error
    LookupResult.Failure(cause = error, retryable = true)
}

internal suspend fun <T, R> LookupResult<T>.flatMap(
    transform: suspend (T) -> LookupResult<R>,
): LookupResult<R> = when (this) {
    is LookupResult.Found -> transform(value)
    is LookupResult.NoMatch -> LookupResult.NoMatch
    is LookupResult.NotFoundById -> LookupResult.NotFoundById
    is LookupResult.Failure -> this
}
