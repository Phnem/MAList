package com.example.myapplication.localplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Перенос выбранной дорожки между сериями (см. matchPreferredAudioTrack): без него ExoPlayer сам
 * подбирает дорожку под каждую новую серию, и выбор пользователя приходилось бы повторять.
 */
class PlayerScreenTest {

    private val russian = AudioTrackOption(
        id = "0:0",
        label = "Russian",
        isSelected = false,
        groupIndex = 0,
        trackIndex = 0,
    )
    private val japanese = AudioTrackOption(
        id = "0:1",
        label = "Japanese",
        isSelected = true,
        groupIndex = 0,
        trackIndex = 1,
    )

    @Test
    fun `matches the previously picked track by label in the new episode`() {
        assertEquals(
            russian,
            matchPreferredAudioTrack("Russian", listOf(japanese, russian)),
        )
    }

    @Test
    fun `does nothing when the preferred track is already selected`() {
        val alreadySelected = russian.copy(isSelected = true)
        assertNull(matchPreferredAudioTrack("Russian", listOf(japanese, alreadySelected)))
    }

    @Test
    fun `falls back to the player default when nothing was picked yet or the track is gone`() {
        assertNull(matchPreferredAudioTrack(null, listOf(japanese, russian)))
        assertNull(matchPreferredAudioTrack("English", listOf(japanese, russian)))
    }
}
