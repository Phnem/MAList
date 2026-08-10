package com.example.myapplication.media.source.movieseries

import com.example.myapplication.media.source.VetroHoster
import com.example.myapplication.media.source.VetroVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieSeriesSourceOptionsTest {

    @Test
    fun `each hoster becomes one selectable source`() {
        val options = MovieSeriesSourceOptions.from(
            listOf(
                hoster("LostFilm", video("a", 1080)),
                hoster("Novafilm", video("b", 720)),
            )
        )

        assertEquals(listOf("LostFilm", "Novafilm"), options.map { it.name })
    }

    @Test
    fun `the ranked order of sources is preserved`() {
        val options = MovieSeriesSourceOptions.from(
            listOf(hoster("Second", video("b", 720)), hoster("First", video("a", 1080)))
        )

        // The cascade already ranked these; re-sorting here would discard that work.
        assertEquals(listOf("Second", "First"), options.map { it.name })
    }

    @Test
    fun `qualities within a source are highest first`() {
        val options = MovieSeriesSourceOptions.from(
            listOf(hoster("LostFilm", video("a", 480), video("b", 1080), video("c", 720)))
        )

        assertEquals(listOf("1080p", "720p", "480p"), options.single().qualities.map { it.label })
    }

    @Test
    fun `the preferred stream of a source is its best quality`() {
        val options = MovieSeriesSourceOptions.from(
            listOf(hoster("LostFilm", video("low", 480), video("high", 1080)))
        )

        assertEquals("https://cdn.example/high.m3u8", options.single().preferredVideo.url)
    }

    @Test
    fun `resolution is read from the label when the field is missing`() {
        val options = MovieSeriesSourceOptions.from(
            listOf(hoster("LostFilm", VetroVideo(url = "https://cdn.example/a.m3u8", label = "720p")))
        )

        assertEquals(720, options.single().qualities.single().resolution)
    }

    @Test
    fun `an unlabelled stream keeps the source's own wording instead of an invented number`() {
        val options = MovieSeriesSourceOptions.from(
            listOf(hoster("Jellyfin", VetroVideo(url = "https://home.example/a.mp4", label = "Auto")))
        )

        val quality = options.single().qualities.single()
        assertEquals("Auto", quality.label)
        assertEquals(null, quality.resolution)
    }

    @Test
    fun `a source with no playable stream is not offered`() {
        val options = MovieSeriesSourceOptions.from(
            listOf(
                VetroHoster(name = "Empty", videos = emptyList()),
                hoster("Working", video("a", 1080)),
            )
        )

        // Offering a source that cannot play anything is a dead end for the user.
        assertEquals(listOf("Working"), options.map { it.name })
    }

    @Test
    fun `duplicate streams inside one source collapse`() {
        val duplicate = video("same", 1080)
        val options = MovieSeriesSourceOptions.from(listOf(hoster("LostFilm", duplicate, duplicate)))

        assertEquals(1, options.single().qualities.size)
    }

    @Test
    fun `no hosters means nothing to choose from`() {
        assertTrue(MovieSeriesSourceOptions.from(emptyList()).isEmpty())
    }

    private fun hoster(name: String, vararg videos: VetroVideo) =
        VetroHoster(name = name, videos = videos.toList())

    private fun video(id: String, resolution: Int) = VetroVideo(
        url = "https://cdn.example/$id.m3u8",
        label = "${resolution}p",
        resolution = resolution,
    )
}
