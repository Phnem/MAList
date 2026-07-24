package com.example.myapplication.media.source

import com.example.myapplication.localplayer.domain.SkipKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniLibriaV1ParserTest {

    @Test
    fun extracts_alias_from_current_and_legacy_release_urls() {
        assertEquals(
            "bleach",
            extractAniLibriaAlias("https://anilibria.top/anime/releases/release/bleach"),
        )
        assertEquals(
            "bleach",
            extractAniLibriaAlias("https://www.anilibria.tv/release/bleach.html")
                ?.removeSuffix(".html"),
        )
        assertNull(extractAniLibriaAlias("https://example.com/bleach"))
    }

    @Test
    fun parses_only_requested_episode_with_real_qualities_and_timestamps() {
        val release = Json.parseToJsonElement(
            """
            {
              "episodes": [
                {
                  "ordinal": 1,
                  "hls_480": "https://cache.example/1/480.m3u8"
                },
                {
                  "ordinal": 2,
                  "hls_480": "https://cache.example/2/480.m3u8",
                  "hls_720": "https://cache.example/2/720.m3u8",
                  "hls_1080": "https://cache.example/2/1080.m3u8",
                  "opening": {"start": 5, "stop": 95},
                  "ending": {"start": 1200, "stop": 1320}
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val videos = parseAniLibriaV1Episode(release, 2)

        assertEquals(listOf(1080, 720, 480), videos.map { it.resolution })
        assertEquals("https://anilibria.top/", videos.first().headers["Referer"])
        assertEquals(2, videos.first().timestamps.size)
        assertEquals(SkipKind.OPENING, videos.first().timestamps.first().kind)
        assertEquals(5_000L, videos.first().timestamps.first().startMs)
        assertEquals(95_000L, videos.first().timestamps.first().endMs)
    }
}
