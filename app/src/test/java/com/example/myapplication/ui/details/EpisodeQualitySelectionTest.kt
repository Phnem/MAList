package com.example.myapplication.ui.details

import com.example.myapplication.media.source.VetroVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeQualitySelectionTest {
    @Test
    fun options_contain_only_real_distinct_resolutions() {
        val videos = listOf(
            video("720p", 720),
            video("HD 720p", null),
            video("1080p", 1080),
            video("auto", null),
        )
        assertEquals(listOf(1080, 720), availableQualityOptions(videos).map { it.resolution })
    }

    @Test
    fun exact_resolution_wins() {
        val selected = chooseVideoForResolution(
            listOf(video("720p", 720), video("1080p", 1080)),
            preferredResolution = 720,
        )
        assertEquals(720, selected?.resolution)
    }

    @Test
    fun closest_lower_resolution_wins_on_equal_distance() {
        val selected = chooseVideoForResolution(
            listOf(video("480p", 480), video("960p", 960)),
            preferredResolution = 720,
        )
        assertEquals(480, selected?.resolution)
    }

    private fun video(label: String, resolution: Int?): VetroVideo =
        VetroVideo(
            url = "https://example.com/$label.m3u8",
            label = label,
            resolution = resolution,
        )
}
