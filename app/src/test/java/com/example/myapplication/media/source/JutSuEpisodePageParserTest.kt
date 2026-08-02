package com.example.myapplication.media.source

import com.example.myapplication.localplayer.domain.SkipKind
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JutSuEpisodePageParserTest {

    @Test
    fun `death note episode one parses intro outro and duration`() {
        val parsed = JutSuEpisodePageParser.parse(
            html = episodeHtml(
                config = """
                    video_intro_start = 0;
                    video_intro_end = 85;
                    video_outro_start = 1283;
                    this_video_duration = 1377;
                """.trimIndent(),
                sources = """
                    <source src="https://video.jut.su/death-note-1.mp4" label="720p" res="720"/>
                """.trimIndent(),
            ),
            pageUrl = "https://jut.su/bookofd/episode-1.html",
        )

        assertEquals(1_377_000L, parsed.durationMs)
        assertEquals(
            listOf(
                VetroTimestamp(0L, 85_000L, SkipKind.OPENING),
                VetroTimestamp(1_283_000L, 1_377_000L, SkipKind.ENDING),
            ),
            parsed.timestamps,
        )
        assertEquals(listOf("https://video.jut.su/death-note-1.mp4"), parsed.sources.map { it.url })
    }

    @Test
    fun `intro start without end gets exactly 89 seconds`() {
        val parsed = JutSuEpisodePageParser.parse(
            episodeHtml(
                config = "video_intro_start = 20; this_video_duration = 200;",
            ),
            "https://jut.su/example/episode-1.html",
        )

        assertEquals(
            listOf(VetroTimestamp(20_000L, 109_000L, SkipKind.OPENING)),
            parsed.timestamps,
        )
    }

    @Test
    fun `intro end without start infers 89 seconds backwards`() {
        val parsed = JutSuEpisodePageParser.parse(
            episodeHtml(
                config = "video_intro_end = 100; this_video_duration = 120;",
            ),
            "https://jut.su/example/episode-1.html",
        )

        assertEquals(
            listOf(VetroTimestamp(11_000L, 100_000L, SkipKind.OPENING)),
            parsed.timestamps,
        )
    }

    @Test
    fun `damaged base64 is ignored without breaking source parsing`() {
        val parsed = JutSuEpisodePageParser.parse(
            """
                <script>eval(Base64.decode("not-valid-%%%"));</script>
                <video><source src="https://cdn.example/episode.mp4" label="480p"/></video>
            """.trimIndent(),
            "https://jut.su/example/episode-1.html",
        )

        assertEquals(0L, parsed.durationMs)
        assertTrue(parsed.timestamps.isEmpty())
        assertEquals(listOf("https://cdn.example/episode.mp4"), parsed.sources.map { it.url })
    }

    @Test
    fun `pixel and image placeholders are not playable videos`() {
        val parsed = JutSuEpisodePageParser.parse(
            episodeHtml(
                config = "this_video_duration = 100;",
                sources = """
                    <source src="https://gen.jut.su/templates/school/images/pixel.png?720" label="720p"/>
                    <source src="https://cdn.example/placeholder.gif" label="480p"/>
                    <source src="https://cdn.example/stub/video.mp4" label="480p"/>
                    <source src="https://cdn.example/audio.mp3" label="audio"/>
                    <source src="https://cdn.example/episode.mp4" label="360p"/>
                """.trimIndent(),
            ),
            "https://jut.su/example/episode-1.html",
        )

        assertEquals(listOf("https://cdn.example/episode.mp4"), parsed.sources.map { it.url })
    }

    /**
     * Снимок настоящей страницы (см. `resources/jutsu/`, снят 2026-08-01 теми же заголовками,
     * что шлёт `VetroHttpSource`).
     *
     * jut.su перешёл на новый плеер (`jutsu_new_player = "yes"`): в HTML приезжают четыре
     * `<source>`, у которых `src` — заглушка `pixel.png?<res>` с `type="video/mp4"`, а настоящие
     * адреса подставляются на клиенте и в получаемую нами разметку не попадают. Поэтому ноль
     * видео здесь — ПРАВИЛЬНЫЙ разбор, а не дефект парсера. Тайминги при этом полноценные,
     * и как источник скипов jut.su остаётся рабочим.
     */
    @Test
    fun `real new-player capture yields skip reference but no playable video`() {
        val html = requireNotNull(
            javaClass.getResourceAsStream("/jutsu/season2-episode2-new-player.html"),
        ).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }

        val parsed = JutSuEpisodePageParser.parse(
            html,
            "https://jut.su/shokugeki-no-souma/season-2/episode-2.html",
        )

        assertEquals(emptyList<String>(), parsed.sources.map { it.url })
        assertEquals(1_512_000L, parsed.durationMs)
        assertEquals(
            listOf(
                VetroTimestamp(174_000L, 264_000L, SkipKind.OPENING),
                VetroTimestamp(1_340_000L, 1_512_000L, SkipKind.ENDING),
            ),
            parsed.timestamps,
        )
        assertTrue(parsed.toSkipReference("jut.su") != null)
    }

    @Test
    fun `parsed metadata creates serializable reference`() {
        val parsed = JutSuEpisodePageParser.parse(
            episodeHtml(
                config = "video_intro_start = 0; video_intro_end = 85; this_video_duration = 100;",
            ),
            "https://jut.su/example/episode-1.html",
        )

        assertEquals(
            VetroSkipReference(
                segments = listOf(VetroTimestamp(0L, 85_000L, SkipKind.OPENING)),
                referenceDurationMs = 100_000L,
                origin = "jut.su",
            ),
            parsed.toSkipReference("jut.su"),
        )
    }

    private fun episodeHtml(
        config: String,
        sources: String = "",
    ): String {
        val encoded = Base64.getEncoder().encodeToString(config.toByteArray())
        return """
            <html><body>
            <video>$sources</video>
            <script>eval( Base64.decode( "$encoded" ) );</script>
            </body></html>
        """.trimIndent()
    }
}
