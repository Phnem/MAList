package com.example.myapplication.localplayer.domain

import com.example.myapplication.media.source.VetroSkipReference
import com.example.myapplication.media.source.VetroTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentResolverTest {

    @Test
    fun `exact video timestamps win over reference and aniskip`() = kotlinx.coroutines.runBlocking {
        var aniSkipCalls = 0
        val resolver = SkipSegmentResolver(
            aniSkip = AniSkipLookup { _, _, _, _ ->
                aniSkipCalls++
                AniSkipSelection(
                    listOf(SkipSegment(30_000L, 40_000L, SkipKind.OPENING)),
                    100_000L,
                )
            },
        )

        val result = resolver.resolve(
            SkipSegmentRequest(
                anilistId = 1,
                malId = 2,
                episodeNumber = 1,
                durationMs = 100_000L,
                exactTimestamps = listOf(VetroTimestamp(1_000L, 2_000L, SkipKind.RECAP)),
                exactOrigin = "AniLiberty",
                reference = VetroSkipReference(
                    listOf(VetroTimestamp(10_000L, 20_000L, SkipKind.OPENING)),
                    100_000L,
                    "jut.su",
                ),
            ),
        )

        assertEquals("AniLiberty", result.origin)
        assertEquals(listOf(SkipSegment(1_000L, 2_000L, SkipKind.RECAP)), result.segments)
        assertEquals(0, aniSkipCalls)
    }

    @Test
    fun `compatible reference is not scaled and is clipped to current duration`() =
        kotlinx.coroutines.runBlocking {
            val resolver = SkipSegmentResolver(
                aniSkip = AniSkipLookup { _, _, _, _ -> error("AniSkip must not be called") },
            )

            val result = resolver.resolve(
                SkipSegmentRequest(
                    anilistId = null,
                    malId = null,
                    episodeNumber = 1,
                    durationMs = 100_000L,
                    reference = VetroSkipReference(
                        segments = listOf(VetroTimestamp(10_000L, 110_000L, SkipKind.OPENING)),
                        referenceDurationMs = 100_500L,
                        origin = "jut.su",
                    ),
                ),
            )

            assertEquals("jut.su", result.origin)
            assertEquals(100_500L, result.referenceDurationMs)
            assertEquals(
                listOf(SkipSegment(10_000L, 100_000L, SkipKind.OPENING)),
                result.segments,
            )
        }

    @Test
    fun `reference must satisfy percentage and absolute duration limits`() {
        assertEquals(true, areSkipDurationsCompatible(1_000_000L, 1_010_000L))
        assertEquals(false, areSkipDurationsCompatible(1_000_000L, 1_010_001L))
        assertEquals(false, areSkipDurationsCompatible(2_000_000L, 2_015_001L))
    }

    @Test
    fun `opening reference uses wider limits without scaling but ending stays strict`() =
        kotlinx.coroutines.runBlocking {
            val resolver = SkipSegmentResolver(
                aniSkip = AniSkipLookup { _, _, _, _ -> error("AniSkip must not be called") },
            )

            val result = resolver.resolve(
                SkipSegmentRequest(
                    anilistId = null,
                    malId = null,
                    episodeNumber = 6,
                    durationMs = 1_470_123L,
                    reference = VetroSkipReference(
                        segments = listOf(
                            VetroTimestamp(75_000L, 165_000L, SkipKind.OPENING),
                            VetroTimestamp(1_301_000L, 1_452_000L, SkipKind.ENDING),
                        ),
                        referenceDurationMs = 1_452_000L,
                        origin = "jut.su",
                    ),
                ),
            )

            assertEquals("jut.su", result.origin)
            assertEquals(
                listOf(SkipSegment(75_000L, 165_000L, SkipKind.OPENING)),
                result.segments,
            )
        }

    @Test
    fun `opening reference still requires both two percent and thirty seconds`() {
        assertEquals(
            true,
            areSkipDurationsCompatible(1_470_123L, 1_452_000L, SkipKind.OPENING, "jut.su"),
        )
        assertEquals(
            false,
            areSkipDurationsCompatible(1_000_000L, 1_020_001L, SkipKind.OPENING, "jut.su"),
        )
        assertEquals(
            false,
            areSkipDurationsCompatible(2_000_000L, 2_030_001L, SkipKind.OPENING, "jut.su"),
        )
        assertEquals(
            false,
            areSkipDurationsCompatible(1_470_123L, 1_452_000L, SkipKind.ENDING),
        )
    }

    @Test
    fun `wider opening compatibility is exclusive to jut su references`() =
        kotlinx.coroutines.runBlocking {
            val resolver = SkipSegmentResolver(
                aniSkip = AniSkipLookup { _, _, _, _ -> AniSkipSelection(emptyList(), null) },
            )

            val result = resolver.resolve(
                SkipSegmentRequest(
                    anilistId = null,
                    malId = null,
                    episodeNumber = 1,
                    durationMs = 1_470_123L,
                    reference = VetroSkipReference(
                        segments = listOf(VetroTimestamp(75_000L, 165_000L, SkipKind.OPENING)),
                        referenceDurationMs = 1_452_000L,
                        origin = "other-source",
                    ),
                ),
            )

            assertTrue(result.segments.isEmpty())
            assertNull(result.origin)
        }

    @Test
    fun `incompatible reference falls back to aniskip`() = kotlinx.coroutines.runBlocking {
        val resolver = SkipSegmentResolver(
            aniSkip = AniSkipLookup { _, _, _, _ ->
                AniSkipSelection(
                    segments = listOf(SkipSegment(30_000L, 40_000L, SkipKind.OPENING)),
                    referenceDurationMs = 100_000L,
                )
            },
        )

        val result = resolver.resolve(
            SkipSegmentRequest(
                anilistId = null,
                malId = 2,
                episodeNumber = 1,
                durationMs = 100_000L,
                reference = VetroSkipReference(
                    listOf(VetroTimestamp(10_000L, 20_000L, SkipKind.OPENING)),
                    103_000L,
                    "jut.su",
                ),
            ),
        )

        assertEquals("AniSkip", result.origin)
        assertEquals(listOf(SkipSegment(30_000L, 40_000L, SkipKind.OPENING)), result.segments)
        assertEquals(100_000L, result.referenceDurationMs)
    }

    @Test
    fun `empty fallback reports no origin`() = kotlinx.coroutines.runBlocking {
        val resolver = SkipSegmentResolver(
            aniSkip = AniSkipLookup { _, _, _, _ -> AniSkipSelection(emptyList(), null) },
        )

        val result = resolver.resolve(
            SkipSegmentRequest(null, null, 1, 100_000L),
        )

        assertEquals(emptyList<SkipSegment>(), result.segments)
        assertNull(result.origin)
    }

    @Test
    fun `negative exact and reference starts are rejected`() = kotlinx.coroutines.runBlocking {
        val resolver = SkipSegmentResolver(
            aniSkip = AniSkipLookup { _, _, _, _ -> AniSkipSelection(emptyList(), null) },
        )

        val exact = resolver.resolve(
            SkipSegmentRequest(
                null,
                null,
                1,
                100_000L,
                exactTimestamps = listOf(VetroTimestamp(-1L, 10_000L, SkipKind.OPENING)),
            ),
        )
        val reference = resolver.resolve(
            SkipSegmentRequest(
                null,
                null,
                1,
                100_000L,
                reference = VetroSkipReference(
                    listOf(VetroTimestamp(-1L, 10_000L, SkipKind.OPENING)),
                    100_000L,
                    "jut.su",
                ),
            ),
        )

        assertTrue(exact.segments.isEmpty())
        assertTrue(reference.segments.isEmpty())
    }
}
