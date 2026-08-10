package com.example.myapplication.media.source.movieseries.custom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSourceInstallerTest {

    private val installer = CustomSourceInstaller()

    @Test
    fun `a valid vetro manifest installs`() {
        val result = installer.fromManifestJson(VETRO_MANIFEST)

        val installed = (result as SourceInstallResult.Installed).source
        assertEquals("manifest:my-catalog", installed.key)
        assertEquals("My catalog", installed.displayName)
        assertTrue(installed.enabled)
        assertTrue(installed.definition is InstalledSourceDefinition.Manifest)
    }

    @Test
    fun `an invalid manifest is refused with the validator's reason`() {
        val insecure = VETRO_MANIFEST.replace("https://media.example.org", "http://public.example")

        val result = installer.fromManifestJson(insecure)

        assertTrue((result as SourceInstallResult.Rejected).reason.contains("https"))
    }

    @Test
    fun `text that is not json at all is refused clearly`() {
        val result = installer.fromManifestJson("<html>nope</html>")

        assertEquals("Not a valid Vetro manifest", (result as SourceInstallResult.Rejected).reason)
    }

    @Test
    fun `a stremio manifest installs from its addon url`() {
        val result = installer.fromStremioManifest("https://addon.example", STREMIO_MANIFEST)

        val installed = (result as SourceInstallResult.Installed).source
        assertEquals("stremio:com.example.addon", installed.key)
        assertEquals("https://addon.example/manifest.json", installed.sourceUrl)
    }

    @Test
    fun `a p2p stremio manifest is refused at install time`() {
        val p2p = STREMIO_MANIFEST.replace(
            """"types":["movie","series"]""",
            """"types":["movie","series"],"behaviorHints":{"p2p":true}""",
        )

        val result = installer.fromStremioManifest("https://addon.example", p2p)

        assertTrue((result as SourceInstallResult.Rejected).reason.contains("P2P"))
    }

    @Test
    fun `the format is detected from the payload rather than declared`() {
        val vetro = installer.fromUnknownJson(VETRO_MANIFEST)
        assertTrue(vetro is SourceInstallResult.Installed)

        val stremio = installer.fromUnknownJson(
            STREMIO_MANIFEST,
            sourceUrl = "https://addon.example/manifest.json",
        )
        assertTrue(stremio is SourceInstallResult.Installed)
        assertEquals(
            "stremio:com.example.addon",
            (stremio as SourceInstallResult.Installed).source.key,
        )
    }

    private companion object {
        const val VETRO_MANIFEST = """
            {
              "manifestVersion": 1,
              "id": "my-catalog",
              "name": "My catalog",
              "baseUrl": "https://media.example.org",
              "capabilities": ["MOVIE", "SERIES", "TMDB_ID"],
              "movie": { "path": "/api/movie/{tmdbId}" },
              "series": { "path": "/api/series/{tmdbId}/{season}/{episode}" },
              "response": { "streams": "/streams", "url": "/src" }
            }
        """

        const val STREMIO_MANIFEST = """
            {
              "id":"com.example.addon",
              "name":"Example addon",
              "version":"1.0.0",
              "description":"test",
              "resources":["stream"],
              "types":["movie","series"]
            }
        """
    }
}
