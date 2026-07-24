package com.example.myapplication.localplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeFilenameParserTest {

    private val parser = EpisodeFilenameParser()

    private fun ep(name: String) = parser.parse(name).episodeNumber

    @Test
    fun sxxExx_marker() {
        assertEquals(5, ep("Jujutsu Kaisen S01E05 [1080p].mkv"))
    }

    @Test
    fun ep_marker_variants() {
        assertEquals(23, ep("[SubsPlease] Jujutsu Kaisen - EP23 (1080p).mkv"))
        assertEquals(7, ep("Bleach E07.mp4"))
        assertEquals(12, ep("show ep.12.mp4"))
    }

    @Test
    fun episode_word_ru_and_en() {
        assertEquals(3, ep("Naruto episode 3.avi"))
        assertEquals(9, ep("Наруто эпизод 9.mkv"))
        assertEquals(4, ep("Ван-Пис 4 серия.mp4"))
    }

    @Test
    fun bracket_number_is_episode_even_with_noise() {
        // Из ТЗ: "1155ieegd[23]jutsu" → 23, а не 1155.
        assertEquals(23, ep("1155ieegd[23]jutsu.mp4"))
        assertEquals(5, ep("Title (05).mkv"))
    }

    @Test
    fun dash_number() {
        assertEquals(8, ep("Attack on Titan - 08.mkv"))
    }

    @Test
    fun technical_numbers_are_not_episodes() {
        // Только тех.теги в скобках, самого номера эпизода нет → null (уйдёт на ИИ).
        assertNull(ep("Movie [1080p][x265][10bit].mkv"))
    }

    @Test
    fun unparseable_returns_null() {
        assertNull(ep("random_clip.mp4"))
    }

    @Test
    fun sxxExx_beats_resolution() {
        assertEquals(2, ep("Frieren S1E02 1080p WEB-DL.mkv"))
    }

    @Test
    fun season_is_extracted() {
        val p = parser.parse("Jujutsu Kaisen S02E05 [1080p].mkv")
        assertEquals(2, p.season)
        assertEquals(5, p.episodeNumber)
        assertEquals(3, parser.parse("[Group] Show Season 3 - 07.mkv").season)
    }

    @Test
    fun season_absent_when_not_stated() {
        assertNull(parser.parse("Bleach E07.mp4").season)
    }
}
