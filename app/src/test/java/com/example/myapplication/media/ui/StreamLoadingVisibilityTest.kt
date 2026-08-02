package com.example.myapplication.media.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLoadingVisibilityTest {

    @Test
    fun `loading remains visible between resolve completion and player ready`() {
        assertTrue(
            shouldShowStreamLoading(
                requestPending = false,
                playerBuffering = true,
            )
        )
    }

    @Test
    fun `pending episode resolve shows loading even while current player is ready`() {
        assertTrue(
            shouldShowStreamLoading(
                requestPending = true,
                playerBuffering = false,
            )
        )
    }

    @Test
    fun `loading hides only after request and buffering both finish`() {
        assertFalse(
            shouldShowStreamLoading(
                requestPending = false,
                playerBuffering = false,
            )
        )
    }
}
