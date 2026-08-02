package com.example.myapplication.domain.enrichment

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reference-counted pause gate for Activities that compete with automatic network enrichment.
 * Each returned token is idempotent so lifecycle cleanup can safely run more than once.
 */
internal class InteractiveMediaPauseGate(
    private val onFirstAcquire: () -> Unit,
    private val onLastRelease: () -> Unit,
) {
    private val lock = Any()
    private var activeTokens = 0

    val hasActiveTokens: Boolean
        get() = synchronized(lock) { activeTokens > 0 }

    fun acquire(): AutoCloseable {
        val isFirst = synchronized(lock) {
            activeTokens += 1
            activeTokens == 1
        }
        if (isFirst) onFirstAcquire()

        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (!closed.compareAndSet(false, true)) return@AutoCloseable
            val isLast = synchronized(lock) {
                check(activeTokens > 0) { "Interactive media pause token underflow" }
                activeTokens -= 1
                activeTokens == 0
            }
            if (isLast) onLastRelease()
        }
    }
}
