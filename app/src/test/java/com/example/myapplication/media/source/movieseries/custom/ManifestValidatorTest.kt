package com.example.myapplication.media.source.movieseries.custom

import com.example.myapplication.media.source.movieseries.ProviderCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestValidatorTest {

    @Test
    fun `a well formed manifest is accepted`() {
        assertTrue(validate(base()) is ManifestValidation.Valid)
    }

    @Test
    fun `an unknown schema version is rejected rather than guessed at`() {
        assertInvalid(base().copy(manifestVersion = 99), "manifestVersion")
    }

    @Test
    fun `a plain http base url is rejected for a public host`() {
        assertInvalid(base().copy(baseUrl = "http://public.example"), "https")
    }

    @Test
    fun `plain http is allowed for a local address when opted in`() {
        val local = base().copy(baseUrl = "http://192.168.1.10:8080", allowInsecureHttp = true)

        assertTrue(validate(local) is ManifestValidation.Valid)
    }

    @Test
    fun `opting in does not unlock plain http for a public host`() {
        val public = base().copy(baseUrl = "http://public.example", allowInsecureHttp = true)

        assertInvalid(public, "local address")
    }

    @Test
    fun `a base url embedding credentials is rejected`() {
        assertInvalid(base().copy(baseUrl = "https://user:pass@host.example"), "credentials")
    }

    @Test
    fun `a path template containing a scheme cannot redirect the request elsewhere`() {
        assertInvalid(
            base().copy(movie = ManifestRequest(path = "/x?u=https://evil.example")),
            "scheme",
        )
    }

    @Test
    fun `a protocol relative template is rejected`() {
        assertInvalid(base().copy(movie = ManifestRequest(path = "//evil.example/x")), "relative")
    }

    @Test
    fun `a traversal template is rejected`() {
        assertInvalid(base().copy(movie = ManifestRequest(path = "/../../admin")), "..")
    }

    @Test
    fun `a template not starting with a slash is rejected`() {
        assertInvalid(base().copy(movie = ManifestRequest(path = "api/movie")), "start with")
    }

    @Test
    fun `an unknown placeholder is rejected`() {
        assertInvalid(base().copy(movie = ManifestRequest(path = "/m/{whatever}")), "whatever")
    }

    @Test
    fun `a placeholder for an undeclared id capability is rejected`() {
        val manifest = base().copy(
            capabilities = setOf(ProviderCapability.MOVIE),
            movie = ManifestRequest(path = "/movie/{tmdbId}"),
        )

        // Substituting an id the source never claimed would produce a nonsense request.
        assertInvalid(manifest, "TMDB_ID")
    }

    @Test
    fun `a series request must address both season and episode`() {
        val manifest = base().copy(
            capabilities = setOf(
                ProviderCapability.SERIES,
                ProviderCapability.TMDB_ID,
            ),
            movie = null,
            series = ManifestRequest(path = "/series/{tmdbId}"),
        )

        assertInvalid(manifest, "season")
    }

    @Test
    fun `declaring a capability without a matching request is rejected`() {
        val manifest = base().copy(
            capabilities = setOf(
                ProviderCapability.MOVIE,
                ProviderCapability.SERIES,
                ProviderCapability.TMDB_ID,
            ),
            series = null,
        )

        assertInvalid(manifest, "SERIES is declared")
    }

    @Test
    fun `query auth is refused for a public host because urls leak into logs`() {
        val manifest = base().copy(auth = ManifestAuth(AuthKind.QUERY, "api_key"))

        assertInvalid(manifest, "Query-parameter auth")
    }

    @Test
    fun `header auth is accepted`() {
        val manifest = base().copy(auth = ManifestAuth(AuthKind.HEADER, "Authorization", "Bearer "))

        assertTrue(validate(manifest) is ManifestValidation.Valid)
    }

    @Test
    fun `a lookup placeholder without a chain is rejected`() {
        val manifest = base().copy(movie = ManifestRequest(path = "/movie/{lookupId}"))

        assertInvalid(manifest, "requires a resolveVia")
    }

    @Test
    fun `a chain that never uses its lookup result is rejected`() {
        val manifest = base().copy(
            resolveVia = ManifestChain(
                lookup = ManifestLookupStep(path = "/search/{tmdbId}", extract = "/results/0/id"),
                movie = ManifestRequest(path = "/movie/{tmdbId}"),
            )
        )

        assertInvalid(manifest, "never used")
    }

    @Test
    fun `a two step chain is accepted`() {
        val manifest = base().copy(
            movie = null,
            series = null,
            capabilities = setOf(ProviderCapability.MOVIE, ProviderCapability.TMDB_ID),
            resolveVia = ManifestChain(
                lookup = ManifestLookupStep(path = "/search/{tmdbId}", extract = "/results/0/id"),
                movie = ManifestRequest(path = "/stream/{lookupId}"),
            ),
        )

        assertTrue(validate(manifest) is ManifestValidation.Valid)
    }

    @Test
    fun `response pointers must be json pointers`() {
        assertInvalid(
            base().copy(response = base().response.copy(streams = "streams")),
            "streams must be a pointer",
        )
    }

    @Test
    fun `retry and pagination bounds are enforced`() {
        assertInvalid(base().copy(retry = ManifestRetry(maxAttempts = 50)), "maxAttempts")
        assertInvalid(
            base().copy(pagination = ManifestPagination(maxPages = 99)),
            "maxPages",
        )
    }

    @Test
    fun `a manifest with no capabilities is rejected`() {
        assertInvalid(base().copy(capabilities = emptySet()), "no capabilities")
    }

    private fun validate(manifest: VetroSourceManifest) = ManifestValidator.validate(manifest)

    private fun assertInvalid(manifest: VetroSourceManifest, contains: String) {
        val result = validate(manifest)
        assertTrue("Expected rejection for: $contains, got $result", result is ManifestValidation.Invalid)
        val reason = (result as ManifestValidation.Invalid).reason
        assertTrue("Reason '$reason' should mention '$contains'", reason.contains(contains))
    }

    private fun base() = VetroSourceManifest(
        manifestVersion = SUPPORTED_MANIFEST_VERSION,
        id = "my-catalog",
        name = "My catalog",
        baseUrl = "https://media.example.org",
        capabilities = setOf(
            ProviderCapability.MOVIE,
            ProviderCapability.SERIES,
            ProviderCapability.TMDB_ID,
        ),
        movie = ManifestRequest(path = "/api/movie/{tmdbId}"),
        series = ManifestRequest(path = "/api/series/{tmdbId}/{season}/{episode}"),
        response = ManifestResponseMapping(streams = "/streams", url = "/src"),
    )

    @Test
    fun `every supported placeholder is documented in one place`() {
        assertEquals(
            setOf("tmdbId", "imdbId", "kinopoiskId", "season", "episode", "title", "lookupId"),
            SUPPORTED_PLACEHOLDERS,
        )
    }
}
