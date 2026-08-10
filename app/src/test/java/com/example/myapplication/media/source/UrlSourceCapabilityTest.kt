package com.example.myapplication.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSourceCapabilityTest {

    @Test
    fun `direct HTTPS media is playable while download permission is explicit`() = runBlocking {
        val source = UrlSource()

        assertTrue(source.canResolveDirect("https://media.example/Doctor.House.S01E01.m3u8"))
        assertFalse(source.canResolveDirect("https://media.example/watch/doctor-house"))
        val video = source.resolveFromWebUrl(
            "https://media.example/Doctor.House.S01E01.m3u8",
            downloadAllowed = false,
        ).single().videos.orEmpty().single()
        assertFalse(video.downloadAllowed)
    }
}
