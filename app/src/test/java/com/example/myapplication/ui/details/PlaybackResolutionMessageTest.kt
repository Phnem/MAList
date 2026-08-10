package com.example.myapplication.ui.details

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.media.source.PlaybackResolution
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResolutionMessageTest {

    @Test
    fun `unconfigured no-match and failure have distinct user messages`() {
        val unconfigured = playbackResolutionMessage(PlaybackResolution.NotConfigured(MediaType.SERIES))
        val noMatch = playbackResolutionMessage(PlaybackResolution.NoMatch)
        val failure = playbackResolutionMessage(PlaybackResolution.Failure)

        assertNotEquals(unconfigured, noMatch)
        assertNotEquals(noMatch, failure)
        assertNotEquals(unconfigured, failure)
        assertTrue(unconfigured.contains("not configured"))
        assertTrue(noMatch.contains("No matching"))
        assertTrue(failure.contains("temporarily unavailable"))
    }
}
