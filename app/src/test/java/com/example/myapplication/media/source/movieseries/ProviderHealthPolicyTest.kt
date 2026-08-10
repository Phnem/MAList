package com.example.myapplication.media.source.movieseries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHealthPolicyTest {

    private val now = 1_000_000L

    @Test
    fun `a success clears the failure streak and any parking`() {
        val shaky = ProviderHealth(consecutiveFailures = 4, temporarilyDisabledUntil = now + 60_000)

        val updated = record(shaky, ProviderResolution.Found(emptyList()))

        assertEquals(0, updated.consecutiveFailures)
        assertNull(updated.temporarilyDisabledUntil)
        assertEquals(now, updated.lastSuccessAt)
    }

    @Test
    fun `not found counts as a healthy answer`() {
        val updated = record(ProviderHealth(consecutiveFailures = 2), ProviderResolution.NotFound)

        // Punishing an honest "I do not carry this title" would disable a working provider for
        // nothing more than a niche request.
        assertEquals(0, updated.consecutiveFailures)
        assertNull(updated.lastFailureAt)
    }

    @Test
    fun `an unconfigured provider is no evidence in either direction`() {
        val before = ProviderHealth(consecutiveFailures = 2, lastSuccessAt = 5L)

        assertEquals(before, record(before, ProviderResolution.NotConfigured))
        assertEquals(before, record(before, ProviderResolution.Unsupported))
    }

    @Test
    fun `a single failure does not park the provider`() {
        val updated = record(ProviderHealth(), ProviderResolution.TemporaryError("HTTP 503"))

        assertEquals(1, updated.consecutiveFailures)
        assertNull(updated.temporarilyDisabledUntil)
        assertFalse(updated.isDisabledAt(now))
    }

    @Test
    fun `parking starts only after repeated failures`() {
        var health = ProviderHealth()
        repeat(ProviderHealthPolicy.FAILURES_BEFORE_BACKOFF) {
            health = record(health, ProviderResolution.TemporaryError("down"))
        }

        assertTrue(health.isDisabledAt(now))
    }

    @Test
    fun `backoff grows with continued failure but never exceeds the cap`() {
        var health = ProviderHealth()
        repeat(40) { health = record(health, ProviderResolution.TemporaryError("down")) }

        val parkedFor = health.temporarilyDisabledUntil!! - now
        assertTrue(parkedFor <= ProviderHealthPolicy.MAX_BACKOFF_MS)
        // A provider must always get another chance eventually; a permanent ban is not a policy.
        assertTrue(parkedFor > 0)
    }

    @Test
    fun `a stated retry-after is obeyed instead of being second-guessed`() {
        val updated = record(ProviderHealth(), ProviderResolution.RateLimited(5_000))

        assertEquals(now + 5_000, updated.temporarilyDisabledUntil)
    }

    @Test
    fun `an absurd retry-after is still capped`() {
        val updated = record(ProviderHealth(), ProviderResolution.RateLimited(Long.MAX_VALUE / 2))

        assertEquals(now + ProviderHealthPolicy.MAX_BACKOFF_MS, updated.temporarilyDisabledUntil)
    }

    @Test
    fun `parking expires on its own`() {
        var health = ProviderHealth()
        repeat(ProviderHealthPolicy.FAILURES_BEFORE_BACKOFF) {
            health = record(health, ProviderResolution.TemporaryError("down"))
        }

        assertTrue(health.isDisabledAt(now))
        assertFalse(health.isDisabledAt(now + ProviderHealthPolicy.MAX_BACKOFF_MS + 1))
    }

    @Test
    fun `one recovery after a long outage restores the provider immediately`() {
        var health = ProviderHealth()
        repeat(10) { health = record(health, ProviderResolution.Blocked("403")) }

        val recovered = record(health, ProviderResolution.Found(emptyList()))

        assertFalse(recovered.isDisabledAt(now))
        assertEquals(0, recovered.consecutiveFailures)
    }

    @Test
    fun `latency is smoothed rather than replaced by one slow call`() {
        val steady = ProviderHealth(averageLatencyMs = 200)

        val updated = record(steady, ProviderResolution.Found(emptyList()), elapsedMs = 4_000)

        assertTrue(updated.averageLatencyMs in 201..1_500)
    }

    @Test
    fun `the first measurement becomes the average`() {
        val updated = record(ProviderHealth(), ProviderResolution.Found(emptyList()), elapsedMs = 350)

        assertEquals(350, updated.averageLatencyMs)
    }

    @Test
    fun `penalty tracks the failure streak and is bounded`() {
        assertEquals(0, ProviderHealthPolicy.penalty(ProviderHealth()))
        assertEquals(3, ProviderHealthPolicy.penalty(ProviderHealth(consecutiveFailures = 3)))
        assertEquals(10, ProviderHealthPolicy.penalty(ProviderHealth(consecutiveFailures = 99)))
    }

    private fun record(
        current: ProviderHealth,
        outcome: ProviderResolution,
        elapsedMs: Long = 100,
    ): ProviderHealth = ProviderHealthPolicy.record(current, outcome, elapsedMs, now)
}
