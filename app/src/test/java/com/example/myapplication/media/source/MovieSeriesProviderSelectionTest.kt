package com.example.myapplication.media.source

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.media.source.movieseries.MovieSeriesStreamingProvider
import com.example.myapplication.media.source.movieseries.ProviderCapability
import com.example.myapplication.media.source.movieseries.ProviderId
import com.example.myapplication.media.source.movieseries.ProviderResolution
import com.example.myapplication.network.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/** RU and EN are separate cascades; this asserts the line-ups without running any provider. */
class MovieSeriesProviderSelectionTest {

    private val ruAutomatic = provider(
        "ru-automatic",
        ProviderCapability.MOVIE,
        ProviderCapability.SERIES,
        ProviderCapability.RU,
    )
    private val enAutomatic = provider(
        "en-automatic",
        ProviderCapability.MOVIE,
        ProviderCapability.SERIES,
        ProviderCapability.EN,
    )
    private val personal = provider(
        "jellyfin",
        ProviderCapability.MOVIE,
        ProviderCapability.SERIES,
    )
    private val movieOnly = provider("movies-only", ProviderCapability.MOVIE)

    @Test
    fun `the russian cascade excludes english-only providers`() {
        val selected = selectMovieSeriesProviders(
            listOf(ruAutomatic, enAutomatic, personal),
            MediaType.SERIES,
            AppLanguage.RU,
        )

        assertEquals(listOf("ru-automatic", "jellyfin"), selected.map { it.id.value })
    }

    @Test
    fun `the english cascade excludes russian-only providers`() {
        val selected = selectMovieSeriesProviders(
            listOf(ruAutomatic, enAutomatic, personal),
            MediaType.SERIES,
            AppLanguage.EN,
        )

        assertEquals(listOf("en-automatic", "jellyfin"), selected.map { it.id.value })
    }

    @Test
    fun `personal sources answer for both languages`() {
        val ru = selectMovieSeriesProviders(listOf(personal), MediaType.MOVIE, AppLanguage.RU)
        val en = selectMovieSeriesProviders(listOf(personal), MediaType.MOVIE, AppLanguage.EN)

        assertEquals(1, ru.size)
        assertEquals(1, en.size)
    }

    @Test
    fun `a movie-only provider is absent from a series cascade`() {
        val selected = selectMovieSeriesProviders(
            listOf(movieOnly, personal),
            MediaType.SERIES,
            AppLanguage.RU,
        )

        assertEquals(listOf("jellyfin"), selected.map { it.id.value })
    }

    @Test
    fun `anime never selects a movie series provider`() {
        val selected = selectMovieSeriesProviders(
            listOf(ruAutomatic, enAutomatic, personal),
            MediaType.ANIME,
            AppLanguage.RU,
        )

        assertEquals(emptyList<String>(), selected.map { it.id.value })
    }

    @Test
    fun `selection preserves the declared order`() {
        val selected = selectMovieSeriesProviders(
            listOf(personal, ruAutomatic),
            MediaType.MOVIE,
            AppLanguage.RU,
        )

        assertEquals(listOf("jellyfin", "ru-automatic"), selected.map { it.id.value })
    }

    private fun provider(
        name: String,
        vararg capabilities: ProviderCapability,
    ) = object : MovieSeriesStreamingProvider {
        override val id: ProviderId = ProviderId(name)
        override val displayName: String = name
        override val capabilities: Set<ProviderCapability> = capabilities.toSet()
        override suspend fun resolve(request: PlaybackRequest): ProviderResolution =
            ProviderResolution.NotFound
    }
}
