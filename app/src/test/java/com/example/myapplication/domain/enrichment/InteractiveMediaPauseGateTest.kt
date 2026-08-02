package com.example.myapplication.domain.enrichment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveMediaPauseGateTest {

    @Test
    fun `nested tokens pause once and resume after the final close`() {
        var pauses = 0
        var resumes = 0
        val gate = InteractiveMediaPauseGate(
            onFirstAcquire = { pauses++ },
            onLastRelease = { resumes++ },
        )

        val player = gate.acquire()
        val reader = gate.acquire()

        assertTrue(gate.hasActiveTokens)
        assertEquals(1, pauses)
        assertEquals(0, resumes)

        player.close()
        assertTrue(gate.hasActiveTokens)
        assertEquals(0, resumes)

        reader.close()
        assertFalse(gate.hasActiveTokens)
        assertEquals(1, resumes)
    }

    @Test
    fun `closing a token twice is idempotent`() {
        var resumes = 0
        val gate = InteractiveMediaPauseGate(
            onFirstAcquire = {},
            onLastRelease = { resumes++ },
        )

        val token = gate.acquire()
        token.close()
        token.close()

        assertFalse(gate.hasActiveTokens)
        assertEquals(1, resumes)
    }
}
