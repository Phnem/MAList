package com.example.myapplication.media.download

import com.example.myapplication.media.source.VetroVideo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HlsSegmentDownloaderTest {

    @Test
    fun `selects requested variant and keeps repeated segments`() {
        val requestedPaths = mutableListOf<String>()
        val packet = ByteArray(188 * 400).also { bytes ->
            bytes.indices.step(188).forEach { bytes[it] = 0x47 }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                requestedPaths += path
                val (contentType, body) = when (path) {
                    "/master.m3u8" -> "application/vnd.apple.mpegurl" to """
                        #EXTM3U
                        #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720
                        720.m3u8
                        #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080
                        1080.m3u8
                    """.trimIndent().toByteArray()
                    "/720.m3u8" -> "application/vnd.apple.mpegurl" to """
                        #EXTM3U
                        #EXTINF:4,
                        segment.ts
                        #EXTINF:4,
                        segment.ts
                        #EXT-X-ENDLIST
                    """.trimIndent().toByteArray()
                    "/segment.ts" -> "video/mp2t" to packet
                    else -> error("Unexpected request: $path")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody(contentType.toMediaType()))
                    .build()
            }
            .build()
        val destination = File.createTempFile("native-hls", ".mp4").also { it.delete() }

        try {
            HlsSegmentDownloader(client).download(
                video = VetroVideo(
                    url = "https://media.test/master.m3u8",
                    label = "720p",
                    resolution = 720,
                ),
                destination = destination,
            )

            assertTrue(MediaFileValidator.isPlayableVideo(destination))
            assertEquals(
                listOf("/master.m3u8", "/720.m3u8", "/segment.ts", "/segment.ts"),
                requestedPaths,
            )
        } finally {
            destination.delete()
        }
    }
}
