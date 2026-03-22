package com.example.myapplication.network

import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Non-blocking-friendly rate limit: callers wait only until tokens refill,
 * instead of each paying a fixed delay under a single mutex (which stacks to K×delay).
 */
class TokenBucketRateLimiter(
    private val maxTokens: Double,
    private val refillTokensPerSecond: Double
) {
    private val mutex = Mutex()
    private var available = maxTokens
    private var lastNano = System.nanoTime()

    private fun refillLocked() {
        val now = System.nanoTime()
        val elapsedSec = (now - lastNano) / 1_000_000_000.0
        if (elapsedSec > 0) {
            available = min(maxTokens, available + elapsedSec * refillTokensPerSecond)
            lastNano = now
        }
    }

    suspend fun acquire(cost: Double = 1.0) {
        require(cost > 0 && cost <= maxTokens) { "cost must be in (0, maxTokens]" }
        while (true) {
            val waitMs = mutex.withLock {
                refillLocked()
                if (available >= cost) {
                    available -= cost
                    -1L
                } else {
                    val needed = cost - available
                    ((needed / refillTokensPerSecond) * 1000).toLong().coerceIn(1L, 60_000L)
                }
            }
            if (waitMs < 0) return
            delay(waitMs)
        }
    }
}
