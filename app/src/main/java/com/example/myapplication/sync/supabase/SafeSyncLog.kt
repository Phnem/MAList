package com.example.myapplication.sync.supabase

private const val REDACTED = "[REDACTED]"
private const val MAX_SAFE_ERROR_LENGTH = 600

private val credentialAssignment = Regex(
    """(?i)["']?\b(authorization|apikey|access_token|refresh_token)\b["']?\s*(?:=|:)\s*(?:"[^"]*"|'[^']*'|\[[^\]]*]|Bearer\s+[^\s,;}\]]+|[^&\s,;}\]]+)""",
)
private val bearerValue = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
private val jwtValue = Regex(
    """(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])""",
)

internal fun sanitizeSensitiveLogText(text: String): String {
    return text
        .replace(bearerValue, "Bearer $REDACTED")
        .replace(credentialAssignment) { match ->
            "${match.groupValues[1]}=$REDACTED"
        }
        .replace(jwtValue, REDACTED)
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(MAX_SAFE_ERROR_LENGTH)
}

internal fun safeSyncError(error: Throwable): String {
    val type = error.javaClass.simpleName.ifBlank { "Exception" }
    val message = sanitizeSensitiveLogText(error.message.orEmpty())
    return if (message.isBlank()) type else "$type: $message"
}

internal fun safeSyncFailure(error: Throwable): Exception =
    IllegalStateException(safeSyncError(error))
