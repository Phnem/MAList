package com.example.myapplication.sync.supabase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeSyncLogTest {

    @Test
    fun `redacts bearer jwt and supabase credential assignments`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLTEifQ.signature-part"
        val text = """
            request failed Authorization=[Bearer $jwt]
            apikey=public-anon-key-value&access_token=$jwt
            refresh_token=refresh-secret-value
        """.trimIndent()

        val safe = sanitizeSensitiveLogText(text)

        for (secret in listOf(jwt, "public-anon-key-value", "refresh-secret-value")) {
            assertFalse(safe.contains(secret))
        }
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test
    fun `exception summary keeps type but never raw credentials or multiple lines`() {
        val error = IllegalStateException(
            "HTTP 401\nBearer opaque-secret-token apikey=[another-secret]",
        )

        val safe = safeSyncError(error)

        assertTrue(safe.startsWith("IllegalStateException:"))
        assertFalse(safe.contains("opaque-secret-token"))
        assertFalse(safe.contains("another-secret"))
        assertFalse(safe.contains('\n'))
    }

    @Test
    fun `redacts ordinary authorization headers and quoted json credentials`() {
        val text = """
            Authorization: Bearer opaque-secret-token
            {"access_token":"opaque-access","refresh_token":"opaque-refresh","apikey":"opaque-key"}
        """.trimIndent()

        val safe = sanitizeSensitiveLogText(text)

        for (
            secret in listOf(
                "opaque-secret-token",
                "opaque-access",
                "opaque-refresh",
                "opaque-key",
            )
        ) {
            assertFalse(safe.contains(secret))
        }
    }
}
