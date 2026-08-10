package com.example.myapplication.media.source.movieseries

import kotlinx.serialization.Serializable
import kotlin.math.min

/**
 * What we have learned about one provider's reliability.
 *
 * Persisted, so a provider that was dead a minute ago is not retried from scratch on every episode.
 */
@Serializable
data class ProviderHealth(
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val consecutiveFailures: Int = 0,
    val averageLatencyMs: Long = 0,
    /** Wall-clock instant before which the provider is skipped without a network call. */
    val temporarilyDisabledUntil: Long? = null,
) {
    fun isDisabledAt(now: Long): Boolean = (temporarilyDisabledUntil ?: 0) > now
}

/**
 * Turns one attempt into an updated health record.
 *
 * Deliberately pure with an injected clock: a backoff policy that can only be tested by waiting is a
 * policy nobody re-tests after changing it.
 */
object ProviderHealthPolicy {

    /** Failures tolerated before a provider is parked at all. */
    const val FAILURES_BEFORE_BACKOFF = 3

    private const val BASE_BACKOFF_MS = 30_000L

    /** Upper bound on the pause, so a provider always gets another chance eventually. */
    const val MAX_BACKOFF_MS = 30 * 60_000L

    fun record(
        current: ProviderHealth,
        outcome: ProviderResolution,
        elapsedMs: Long,
        now: Long,
    ): ProviderHealth {
        // NotFound is an honest answer, not a fault: punishing it would disable a healthy provider
        // simply for not carrying a niche title.
        if (!outcome.isFailure) return current.recordSuccess(elapsedMs, now, outcome)
        return current.recordFailure(elapsedMs, now, outcome)
    }

    private fun ProviderHealth.recordSuccess(
        elapsedMs: Long,
        now: Long,
        outcome: ProviderResolution,
    ): ProviderHealth {
        // An unconfigured or inapplicable provider never reached the network, so it is no evidence
        // about reliability in either direction.
        if (!outcome.isConfigured) return this
        return copy(
            lastSuccessAt = now,
            consecutiveFailures = 0,
            averageLatencyMs = blendLatency(elapsedMs),
            temporarilyDisabledUntil = null,
        )
    }

    private fun ProviderHealth.recordFailure(
        elapsedMs: Long,
        now: Long,
        outcome: ProviderResolution,
    ): ProviderHealth {
        val failures = consecutiveFailures + 1
        val explicitRetry = (outcome as? ProviderResolution.RateLimited)?.retryAfterMs
        val disabledUntil = when {
            // A provider that told us when to come back is obeyed rather than second-guessed.
            explicitRetry != null -> now + min(explicitRetry, MAX_BACKOFF_MS)
            failures >= FAILURES_BEFORE_BACKOFF -> now + backoffFor(failures)
            else -> temporarilyDisabledUntil
        }
        return copy(
            lastFailureAt = now,
            consecutiveFailures = failures,
            averageLatencyMs = blendLatency(elapsedMs),
            temporarilyDisabledUntil = disabledUntil,
        )
    }

    /** Exponential, but capped: one bad afternoon must not be a permanent ban. */
    private fun backoffFor(consecutiveFailures: Int): Long {
        val steps = (consecutiveFailures - FAILURES_BEFORE_BACKOFF).coerceIn(0, 10)
        val scaled = BASE_BACKOFF_MS shl steps
        return min(scaled, MAX_BACKOFF_MS)
    }

    /** Exponential moving average; a single slow call should not redefine a provider. */
    private fun ProviderHealth.blendLatency(elapsedMs: Long): Long =
        if (averageLatencyMs == 0L) elapsedMs else (averageLatencyMs * 3 + elapsedMs) / 4

    /**
     * Ranking penalty for a provider's current state.
     *
     * Only breaks ties between otherwise equal candidates: a shaky provider that actually returned a
     * stream still beats a healthy one that returned nothing.
     */
    fun penalty(health: ProviderHealth): Int = health.consecutiveFailures.coerceAtMost(10)
}
