package com.example.myapplication.media.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorPlaybackSourceConnectionTesterTest {

    @Test
    fun `WebDAV probe uses depth zero and authorization header`() = runBlocking {
        val tester = KtorPlaybackSourceConnectionTester(
            webDavClient = HttpClient(
                MockEngine { request ->
                    assertEquals("0", request.headers["Depth"])
                    assertTrue(request.headers["Authorization"].orEmpty().startsWith("Basic "))
                    respond("", HttpStatusCode.MultiStatus)
                }
            ),
            personalServerClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
        )

        assertTrue(
            tester.testWebDav(
                WebDavConfig("https://dav.example", "Movies", "owner", "app-password")
            )
        )
    }

    @Test
    fun `personal probe keeps token in header and treats unauthorized as failure`() = runBlocking {
        val tester = KtorPlaybackSourceConnectionTester(
            webDavClient = HttpClient(MockEngine { respond("", HttpStatusCode.MultiStatus) }),
            personalServerClient = HttpClient(
                MockEngine { request ->
                    assertTrue(request.url.encodedPath.endsWith("/Users/user-1"))
                    assertEquals("personal-token", request.headers["X-Emby-Token"])
                    assertFalse(request.url.toString().contains("personal-token"))
                    respond("", HttpStatusCode.Unauthorized)
                }
            ),
        )

        assertFalse(
            tester.testPersonalServer(
                PersonalMediaServerProvider.JELLYFIN,
                PersonalMediaServerConfig(
                    "https://media.example/jellyfin",
                    "user-1",
                    "personal-token",
                ),
            )
        )
    }
}
