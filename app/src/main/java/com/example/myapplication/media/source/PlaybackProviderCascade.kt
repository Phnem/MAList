package com.example.myapplication.media.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

internal data class PlaybackProviderCall<T>(
    val label: String,
    val timeoutMs: Long,
    val resolve: suspend () -> T,
)

internal data class SourceAttempt<T>(
    val label: String,
    val value: T?,
    val failed: Boolean = false,
    val timedOut: Boolean = false,
    val elapsedMs: Long = 0,
)

/** Runs every applicable provider independently; one failure never cancels its siblings. */
internal suspend fun <T> runPlaybackProviderCascade(
    calls: List<PlaybackProviderCall<T>>,
): List<SourceAttempt<T>> = supervisorScope {
    calls.map { call ->
        async {
            val startedAt = System.currentTimeMillis()
            val result = withTimeoutOrNull(call.timeoutMs) {
                try {
                    SourceAttempt<T>(label = call.label, value = call.resolve())
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    SourceAttempt<T>(label = call.label, value = null, failed = true)
                }
            }
            val elapsed = System.currentTimeMillis() - startedAt
            result?.copy(elapsedMs = elapsed)
                ?: SourceAttempt<T>(
                    value = null,
                    label = call.label,
                    failed = true,
                    timedOut = true,
                    elapsedMs = elapsed,
                )
        }
    }.awaitAll()
}
