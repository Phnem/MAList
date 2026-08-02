package com.example.myapplication.media.source

import com.example.myapplication.localplayer.domain.SkipKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SkipReferencePropagationTest {

    @Test
    fun `reference only jut hoster enriches every playable video then is removed`() {
        val reference = VetroSkipReference(
            segments = listOf(VetroTimestamp(0L, 85_000L, SkipKind.OPENING)),
            referenceDurationMs = 1_377_000L,
            origin = "jut.su",
        )
        val hosters = listOf(
            VetroHoster(name = "jut.su", videos = emptyList(), skipReference = reference),
            VetroHoster(
                name = "Kodik",
                videos = listOf(
                    VetroVideo("https://cdn.example/720.mp4", "720p"),
                    VetroVideo("https://cdn.example/1080.mp4", "1080p"),
                ),
            ),
        )

        val result = hosters.withPropagatedSkipReference().playableHosters()

        assertEquals(listOf("Kodik"), result.map { it.name })
        assertEquals(
            listOf(reference, reference),
            result.single().videos.orEmpty().map { it.skipReference },
        )
    }

    @Test
    fun `video keeps its own reference`() {
        val episodeReference = VetroSkipReference(
            listOf(VetroTimestamp(0L, 85_000L, SkipKind.OPENING)),
            1_377_000L,
            "jut.su",
        )
        val ownReference = VetroSkipReference(
            listOf(VetroTimestamp(10_000L, 20_000L, SkipKind.RECAP)),
            1_377_000L,
            "own",
        )
        val result = listOf(
            VetroHoster("jut.su", skipReference = episodeReference),
            VetroHoster(
                "Source",
                videos = listOf(
                    VetroVideo(
                        "https://cdn.example/video.mp4",
                        "auto",
                        skipReference = ownReference,
                    ),
                ),
            ),
        ).withPropagatedSkipReference()

        assertEquals(ownReference, result[1].videos.orEmpty().single().skipReference)
        assertEquals(episodeReference, result[1].skipReference)
    }

    @Test
    fun `jut reference wins regardless of hoster order`() {
        val otherReference = VetroSkipReference(
            listOf(VetroTimestamp(10_000L, 20_000L, SkipKind.RECAP)),
            1_377_000L,
            "other",
        )
        val jutReference = VetroSkipReference(
            listOf(VetroTimestamp(0L, 85_000L, SkipKind.OPENING)),
            1_377_000L,
            "jut.su",
        )
        val result = listOf(
            VetroHoster(
                "Other",
                videos = listOf(
                    VetroVideo(
                        "https://cdn.example/video.mp4",
                        "auto",
                        skipReference = otherReference,
                    ),
                ),
            ),
            VetroHoster("jut.su", skipReference = jutReference),
        ).withPropagatedSkipReference()

        assertEquals(jutReference, result.first().skipReference)
        assertEquals(otherReference, result.first().videos.orEmpty().single().skipReference)
    }

    @Test
    fun `invalid video urls neither escape normalization nor suppress fallback`() {
        val hosters = listOf(
            VetroHoster(
                name = "invalid",
                url = "https://source.example",
                videos = listOf(
                    VetroVideo(url = "https://cdn.example/pixel.png", label = "placeholder"),
                    VetroVideo(url = "https://cdn.example/poster.jpg", label = "image"),
                ),
            ),
        )

        assertEquals(emptyList<VetroHoster>(), hosters.playableHosters())
        assertEquals(false, hosters.hasPlayableVideo())
    }
}
