package com.example.myapplication.localplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSkipCoordinatorTest {

    private val resolution = SkipSegmentResolution(
        segments = listOf(SkipSegment(0L, 85_000L, SkipKind.OPENING)),
        origin = "jut.su",
        referenceDurationMs = 1_377_000L,
    )

    @Test
    fun `first episode and resume inside opening produce automatic seek`() {
        val coordinator = MediaSkipCoordinator()
        val key = SkipMediaKey(1, "https://video/episode-1.mp4", 1)
        coordinator.install(key, resolution)

        val decision = coordinator.automaticSeek(key, 40_000L, enabled = true)

        assertEquals(85_000L, decision?.targetMs)
        assertEquals(SkipSeekReason.AUTOMATIC, decision?.reason)
    }

    @Test
    fun `restored position inside same opening is rechecked after discontinuity`() {
        val coordinator = MediaSkipCoordinator()
        val key = SkipMediaKey(1, "episode-1", 1)
        coordinator.install(key, resolution)
        assertEquals(85_000L, coordinator.automaticSeek(key, 0L, enabled = true)?.targetMs)

        coordinator.onPositionDiscontinuity(key, 40_000L)
        val afterRestore = coordinator.automaticSeek(key, 40_000L, enabled = true)

        assertEquals(85_000L, afterRestore?.targetMs)
    }

    @Test
    fun `automatic seek discontinuity does not rearm the same segment`() {
        val coordinator = MediaSkipCoordinator()
        val key = SkipMediaKey(1, "episode-1", 1)
        coordinator.install(key, resolution)
        assertEquals(85_000L, coordinator.automaticSeek(key, 10_000L, true)?.targetMs)

        coordinator.onPositionDiscontinuity(key, 84_750L)

        assertNull(coordinator.automaticSeek(key, 84_750L, true))
    }

    @Test
    fun `duration refinement for same media preserves automatic seek deduplication`() {
        val coordinator = MediaSkipCoordinator()
        val key = SkipMediaKey(1, "episode-1", 1)
        coordinator.install(key, resolution)
        assertEquals(85_000L, coordinator.automaticSeek(key, 10_000L, true)?.targetMs)

        coordinator.install(key, resolution.copy(referenceDurationMs = 1_378_000L))

        assertNull(coordinator.automaticSeek(key, 20_000L, true))
    }

    @Test
    fun `same segment is not repeatedly sought until playback leaves it`() {
        val coordinator = MediaSkipCoordinator()
        val key = SkipMediaKey(1, "episode-1", 1)
        coordinator.install(key, resolution)

        assertEquals(85_000L, coordinator.automaticSeek(key, 10_000L, true)?.targetMs)
        assertNull(coordinator.automaticSeek(key, 20_000L, true))
        assertNull(coordinator.automaticSeek(key, 90_000L, true))
        assertEquals(85_000L, coordinator.automaticSeek(key, 10_000L, true)?.targetMs)
    }

    @Test
    fun `episode url and player instance each reset state`() {
        val coordinator = MediaSkipCoordinator()
        val first = SkipMediaKey(1, "episode-1-audio-a", 1)
        val nextEpisode = SkipMediaKey(1, "episode-2", 2)
        val nextAudio = SkipMediaKey(1, "episode-2-audio-b", 2)
        val replacementPlayer = SkipMediaKey(2, "episode-2-audio-b", 2)

        coordinator.install(first, resolution)
        assertEquals(85_000L, coordinator.automaticSeek(first, 1_000L, true)?.targetMs)
        coordinator.install(nextEpisode, resolution)
        assertEquals(85_000L, coordinator.automaticSeek(nextEpisode, 1_000L, true)?.targetMs)
        coordinator.install(nextAudio, resolution)
        assertEquals(85_000L, coordinator.automaticSeek(nextAudio, 1_000L, true)?.targetMs)
        coordinator.install(replacementPlayer, resolution)
        assertEquals(85_000L, coordinator.automaticSeek(replacementPlayer, 1_000L, true)?.targetMs)
    }

    @Test
    fun `manual skip works while autoskip is disabled`() {
        val coordinator = MediaSkipCoordinator()
        val key = SkipMediaKey(1, "local-episode-1", 1)
        coordinator.install(key, resolution)

        assertNull(coordinator.automaticSeek(key, 20_000L, enabled = false))
        val manual = coordinator.manualSeek(key, 20_000L)

        assertEquals(85_000L, manual?.targetMs)
        assertEquals(SkipSeekReason.MANUAL, manual?.reason)
    }

    @Test
    fun `stale media key cannot trigger seek`() {
        val coordinator = MediaSkipCoordinator()
        val old = SkipMediaKey(1, "old", 1)
        val current = SkipMediaKey(1, "current", 2)
        coordinator.install(old, resolution)
        coordinator.install(current, resolution)

        assertNull(coordinator.automaticSeek(old, 20_000L, enabled = true))
        assertNull(coordinator.manualSeek(old, 20_000L))
    }
}
