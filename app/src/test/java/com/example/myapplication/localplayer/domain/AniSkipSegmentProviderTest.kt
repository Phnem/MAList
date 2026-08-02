package com.example.myapplication.localplayer.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniSkipSegmentProviderTest {

    @Test
    fun `one episodeLength zero request is cached across duration changes`() =
        kotlinx.coroutines.runBlocking {
            var requests = 0
            var requestedUrl = ""
            val provider = AniSkipSegmentProvider(
                transport = AniSkipTransport { url ->
                    requests++
                    requestedUrl = url
                    AniSkipHttpResponse(200, successfulBody())
                },
                episodeMapper = { _, _, _ -> null },
            )

            val first = provider.fetch(null, 1535, 1, 1_377_000L)
            val second = provider.fetch(null, 1535, 1, 1_378_000L)

            assertEquals(1, requests)
            assertTrue(requestedUrl.endsWith("episodeLength=0"))
            assertEquals(2, first.segments.size)
            assertEquals(2, second.segments.size)
        }

    @Test
    fun `network error is not cached`() = kotlinx.coroutines.runBlocking {
        var requests = 0
        val provider = AniSkipSegmentProvider(
            transport = AniSkipTransport {
                requests++
                if (requests == 1) throw java.io.IOException("offline")
                AniSkipHttpResponse(200, successfulBody())
            },
            episodeMapper = { _, _, _ -> null },
        )

        assertTrue(provider.fetch(null, 1535, 1, 1_377_000L).segments.isEmpty())
        assertEquals(2, provider.fetch(null, 1535, 1, 1_377_000L).segments.size)
        assertEquals(2, requests)
    }

    @Test
    fun `http miss is not cached`() = kotlinx.coroutines.runBlocking {
        var requests = 0
        val provider = AniSkipSegmentProvider(
            transport = AniSkipTransport {
                requests++
                AniSkipHttpResponse(404, """{"found":false,"results":[],"statusCode":404}""")
            },
            episodeMapper = { _, _, _ -> null },
        )

        provider.fetch(null, 999_999, 1, 1_000_000L)
        provider.fetch(null, 999_999, 1, 1_000_000L)

        assertEquals(2, requests)
    }

    @Test
    fun `malformed successful response is not cached`() = kotlinx.coroutines.runBlocking {
        var requests = 0
        val provider = AniSkipSegmentProvider(
            transport = AniSkipTransport {
                requests++
                if (requests == 1) {
                    AniSkipHttpResponse(200, """{"found":true,"statusCode":200,"results":[{}]}""")
                } else {
                    AniSkipHttpResponse(200, successfulBody())
                }
            },
            episodeMapper = { _, _, _ -> null },
        )

        assertTrue(provider.fetch(null, 1535, 1, 1_377_000L).segments.isEmpty())
        assertEquals(2, provider.fetch(null, 1535, 1, 1_377_000L).segments.size)
        assertEquals(2, requests)
    }

    @Test
    fun `incompatible top voted records are rejected`() = kotlinx.coroutines.runBlocking {
        val provider = AniSkipSegmentProvider(
            transport = AniSkipTransport { AniSkipHttpResponse(200, successfulBody()) },
            episodeMapper = { _, _, _ -> null },
        )

        val selected = provider.fetch(null, 1535, 1, 1_000_000L)

        assertTrue(selected.segments.isEmpty())
    }

    @Test
    fun `cancelling fetch owner releases waiter and permits retry`() =
        kotlinx.coroutines.runBlocking {
            var requests = 0
            val provider = AniSkipSegmentProvider(
                transport = AniSkipTransport {
                    requests++
                    if (requests == 1) awaitCancellation()
                    AniSkipHttpResponse(200, successfulBody())
                },
                episodeMapper = { _, _, _ -> null },
            )

            val owner = async(start = CoroutineStart.UNDISPATCHED) {
                provider.fetch(null, 1535, 1, 1_377_000L)
            }
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                provider.fetch(null, 1535, 1, 1_377_000L)
            }

            owner.cancel()
            owner.join()
            val waiterResult = withTimeout(1_000L) { waiter.await() }
            val retryResult = withTimeout(1_000L) {
                provider.fetch(null, 1535, 1, 1_377_000L)
            }

            assertTrue(waiterResult.segments.isEmpty())
            assertEquals(2, retryResult.segments.size)
            assertEquals(2, requests)
        }

    @Test
    fun `episode mapper cancellation is propagated without transport request`() =
        kotlinx.coroutines.runBlocking {
            var requests = 0
            val provider = AniSkipSegmentProvider(
                transport = AniSkipTransport {
                    requests++
                    AniSkipHttpResponse(200, successfulBody())
                },
                episodeMapper = { _, _, _ -> throw CancellationException() },
            )

            var cancelled = false
            try {
                provider.fetch(null, 1535, 1, 1_377_000L)
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
            assertEquals(0, requests)
        }

    private fun successfulBody(): String = """
        {
          "found": true,
          "statusCode": 200,
          "results": [
            {
              "interval": {"startTime": 1.039, "endTime": 91.039},
              "skipType": "op",
              "episodeLength": 1377.312
            },
            {
              "interval": {"startTime": 1286.051, "endTime": 1356.451},
              "skipType": "ed",
              "episodeLength": 1377.080
            }
          ]
        }
    """.trimIndent()
}
