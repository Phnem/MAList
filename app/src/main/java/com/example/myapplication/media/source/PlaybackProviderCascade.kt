package com.example.myapplication.media.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

internal data class PlaybackProviderCall(
    val label: String,
    val timeoutMs: Long,
    val resolve: suspend () -> List<VetroHoster>,
)

internal data class SourceAttempt(
    val label: String,
    val hosters: List<VetroHoster>,
    val failed: Boolean = false,
    val timedOut: Boolean = false,
    val elapsedMs: Long = 0,
)

/** Runs every applicable provider independently; one failure never cancels its siblings. */
internal suspend fun runPlaybackProviderCascade(
    calls: List<PlaybackProviderCall>,
): List<SourceAttempt> = supervisorScope {
    calls.map { call ->
        async {
            val startedAt = System.currentTimeMillis()
            val result = withTimeoutOrNull(call.timeoutMs) {
                try {
                    SourceAttempt(label = call.label, hosters = call.resolve())
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    SourceAttempt(label = call.label, hosters = emptyList(), failed = true)
                }
            }
            val elapsed = System.currentTimeMillis() - startedAt
            result?.copy(elapsedMs = elapsed)
                ?: SourceAttempt(
                    hosters = emptyList(),
                    label = call.label,
                    failed = true,
                    timedOut = true,
                    elapsedMs = elapsed,
                )
        }
    }.awaitAll()
}
