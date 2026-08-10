package com.example.myapplication.media.source.movieseries

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderApplicabilityTest {

    @Test
    fun `media type must be declared`() {
        val movieOnly = setOf(ProviderCapability.MOVIE)

        assertTrue(ProviderApplicability.isApplicable(movieOnly, MediaType.MOVIE, AppLanguage.RU))
        assertFalse(ProviderApplicability.isApplicable(movieOnly, MediaType.SERIES, AppLanguage.RU))
    }

    @Test
    fun `anime and manga never reach movie series providers`() {
        val everything = ProviderCapability.entries.toSet()

        assertFalse(ProviderApplicability.isApplicable(everything, MediaType.ANIME, AppLanguage.RU))
        assertFalse(ProviderApplicability.isApplicable(everything, MediaType.MANGA, AppLanguage.RU))
    }

    @Test
    fun `declaring no language means the provider answers for both`() {
        val personal = setOf(ProviderCapability.MOVIE, ProviderCapability.SERIES)

        assertTrue(ProviderApplicability.isApplicable(personal, MediaType.MOVIE, AppLanguage.RU))
        assertTrue(ProviderApplicability.isApplicable(personal, MediaType.MOVIE, AppLanguage.EN))
    }

    @Test
    fun `declaring one language excludes the other`() {
        val ruOnly = setOf(ProviderCapability.MOVIE, ProviderCapability.RU)

        assertTrue(ProviderApplicability.isApplicable(ruOnly, MediaType.MOVIE, AppLanguage.RU))
        assertFalse(ProviderApplicability.isApplicable(ruOnly, MediaType.MOVIE, AppLanguage.EN))
    }

    @Test
    fun `declaring both languages serves both`() {
        val both = setOf(ProviderCapability.SERIES, ProviderCapability.RU, ProviderCapability.EN)

        assertTrue(ProviderApplicability.isApplicable(both, MediaType.SERIES, AppLanguage.RU))
        assertTrue(ProviderApplicability.isApplicable(both, MediaType.SERIES, AppLanguage.EN))
    }
}

class ProviderResolutionStatusTest {

    @Test
    fun `success returns null so the caller keeps parsing`() {
        assertEquals(null, providerResolutionForStatus(200))
        assertEquals(null, providerResolutionForStatus(206))
    }

    @Test
    fun `missing content is not found rather than an error`() {
        assertEquals(ProviderResolution.NotFound, providerResolutionForStatus(404))
        assertEquals(ProviderResolution.NotFound, providerResolutionForStatus(410))
    }

    @Test
    fun `auth refusals are blocked`() {
        assertEquals(ProviderResolution.Blocked("HTTP 401"), providerResolutionForStatus(401))
        assertEquals(ProviderResolution.Blocked("HTTP 403"), providerResolutionForStatus(403))
    }

    @Test
    fun `throttling carries the retry hint`() {
        assertEquals(ProviderResolution.RateLimited(5_000), providerResolutionForStatus(429, 5_000))
        assertEquals(ProviderResolution.RateLimited(null), providerResolutionForStatus(429))
    }

    @Test
    fun `server faults and timeouts are temporary`() {
        assertEquals(ProviderResolution.TemporaryError("HTTP 500"), providerResolutionForStatus(500))
        assertEquals(ProviderResolution.TemporaryError("HTTP 503"), providerResolutionForStatus(503))
        assertEquals(ProviderResolution.TemporaryError("HTTP 408"), providerResolutionForStatus(408))
    }

    @Test
    fun `unexpected client statuses are invalid responses`() {
        assertEquals(ProviderResolution.InvalidResponse("HTTP 418"), providerResolutionForStatus(418))
        assertEquals(ProviderResolution.InvalidResponse("HTTP 400"), providerResolutionForStatus(400))
    }
}

class ProviderResolutionClassificationTest {

    @Test
    fun `only real faults count against provider health`() {
        assertTrue(ProviderResolution.TemporaryError().isFailure)
        assertTrue(ProviderResolution.Blocked().isFailure)
        assertTrue(ProviderResolution.RateLimited().isFailure)
        assertTrue(ProviderResolution.InvalidResponse().isFailure)

        // An honest "I do not carry this title" must never disable a healthy provider.
        assertFalse(ProviderResolution.NotFound.isFailure)
        assertFalse(ProviderResolution.NotConfigured.isFailure)
        assertFalse(ProviderResolution.Unsupported.isFailure)
        assertFalse(ProviderResolution.Found(emptyList()).isFailure)
    }

    @Test
    fun `unconfigured and unsupported providers are not counted as configured`() {
        assertFalse(ProviderResolution.NotConfigured.isConfigured)
        assertFalse(ProviderResolution.Unsupported.isConfigured)

        assertTrue(ProviderResolution.NotFound.isConfigured)
        assertTrue(ProviderResolution.Found(emptyList()).isConfigured)
        assertTrue(ProviderResolution.TemporaryError().isConfigured)
    }
}
