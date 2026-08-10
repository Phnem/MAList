package com.example.myapplication.media.source

import com.example.myapplication.data.local.WebLinksStore
import com.example.myapplication.media.source.movieseries.MovieSeriesStreamingProvider
import com.example.myapplication.media.source.movieseries.ProviderCapability
import com.example.myapplication.media.source.movieseries.ProviderId
import com.example.myapplication.media.source.movieseries.ProviderResolution
import com.example.myapplication.network.AppLanguage

/** Explicit direct HTTPS media already attached by the user to this library entry. */
class DirectHttpPlaybackSource(
    private val urlSource: UrlSource,
    private val webLinksStore: WebLinksStore,
) : MovieSeriesStreamingProvider {
    override val id: ProviderId = ProviderId("direct-https")
    override val displayName: String = "Direct HTTPS"

    /**
     * Language-agnostic: the stored link set is chosen per request language, but the provider itself
     * serves both. No DOWNLOAD — a stored link proves reachability, not offline-copy permission.
     */
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.MOVIE,
        ProviderCapability.SERIES,
        ProviderCapability.DIRECT,
        ProviderCapability.HLS,
    )

    override suspend fun resolve(request: PlaybackRequest): ProviderResolution {
        val direct = directUrl(request) ?: return ProviderResolution.NotConfigured
        val hosters = urlSource.resolveFromWebUrl(direct, downloadAllowed = false)
        return if (hosters.isEmpty()) {
            ProviderResolution.NotFound
        } else {
            ProviderResolution.Found(hosters)
        }
    }

    private suspend fun directUrl(request: PlaybackRequest): String? {
        webLinksStore.ensureLoaded()
        val stored = webLinksStore.flow.value[request.anime.id]
        val urls = when (request.language) {
            AppLanguage.RU -> stored?.ruLinks
            AppLanguage.EN -> stored?.enLinks
        }.orEmpty().map { it.url }
        return urls.firstOrNull(urlSource::canResolveDirect)
    }
}
