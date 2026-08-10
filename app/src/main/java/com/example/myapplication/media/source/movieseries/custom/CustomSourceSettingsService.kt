package com.example.myapplication.media.source.movieseries.custom

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One installed source as the settings screen sees it. */
data class CustomSourceSummary(
    val key: String,
    val displayName: String,
    val kindLabel: String,
    val enabled: Boolean,
    val sourceUrl: String?,
)

/** What happened when the user tried to add or refresh a source. */
sealed interface CustomSourceOutcome {
    data class Installed(val summary: CustomSourceSummary) : CustomSourceOutcome
    data class Rejected(val reason: String) : CustomSourceOutcome
}

/**
 * Add, refresh, enable, disable and remove user-installed sources.
 *
 * Definitions are always validated before they are stored, so the settings screen can show a real
 * reason instead of the source failing silently during playback much later.
 */
class CustomSourceSettingsService(
    private val store: InstalledSourceStore,
    private val installer: CustomSourceInstaller,
    private val client: HttpClient,
) {
    suspend fun summaries(): List<CustomSourceSummary> =
        store.all().map(InstalledSource::toSummary)

    /** Installs from a definition the user pasted or picked as a file. */
    suspend fun installFromText(text: String, sourceUrl: String? = null): CustomSourceOutcome =
        when (val parsed = installer.fromUnknownJson(text, sourceUrl)) {
            is SourceInstallResult.Rejected -> CustomSourceOutcome.Rejected(parsed.reason)
            is SourceInstallResult.Installed ->
                CustomSourceOutcome.Installed(store.install(parsed.source).toSummary())
        }

    /** Installs from a link to the definition. */
    suspend fun installFromUrl(url: String): CustomSourceOutcome {
        val normalized = url.trim()
        val parsed = normalized.toHttpUrlOrNull()
            ?: return CustomSourceOutcome.Rejected("Not a valid link")
        // The definition decides what Vetro will talk to, so it must not arrive over plain HTTP
        // where anyone on the path could rewrite it.
        if (!parsed.isHttps) return CustomSourceOutcome.Rejected("The link must use https")

        val body = try {
            val response = client.get(normalized)
            if (!response.status.isSuccess()) {
                return CustomSourceOutcome.Rejected("Source replied HTTP ${response.status.value}")
            }
            response.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return CustomSourceOutcome.Rejected("Could not reach the link")
        }
        return installFromText(body, sourceUrl = normalized)
    }

    /** Re-fetches a source's definition from where it came from. */
    suspend fun refresh(key: String): CustomSourceOutcome {
        val existing = store.all().firstOrNull { it.key == key }
            ?: return CustomSourceOutcome.Rejected("Source is no longer installed")
        val url = existing.sourceUrl
            ?: return CustomSourceOutcome.Rejected("This source was added manually and has no link")
        return installFromUrl(url)
    }

    suspend fun setEnabled(key: String, enabled: Boolean) = store.setEnabled(key, enabled)

    suspend fun remove(key: String) = store.remove(key)
}

private fun InstalledSource.toSummary(): CustomSourceSummary = CustomSourceSummary(
    key = key,
    displayName = displayName,
    kindLabel = when (definition) {
        is InstalledSourceDefinition.Manifest -> "Vetro"
        is InstalledSourceDefinition.Stremio -> "Stremio"
    },
    enabled = enabled,
    sourceUrl = sourceUrl,
)
