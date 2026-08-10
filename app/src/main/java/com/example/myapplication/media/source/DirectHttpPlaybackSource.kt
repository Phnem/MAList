package com.example.myapplication.media.source

import com.example.myapplication.data.local.WebLinksStore
import com.example.myapplication.network.AppLanguage

/** Explicit direct HTTPS media already attached by the user to this library entry. */
class DirectHttpPlaybackSource(
    private val urlSource: UrlSource,
    private val webLinksStore: WebLinksStore,
) : MovieSeriesPlaybackSource {
    override val sourceName: String = "Direct HTTPS"

    override suspend fun resolve(request: PlaybackRequest): MovieSeriesSourceResult {
        val direct = directUrl(request) ?: return MovieSeriesSourceResult.NotConfigured
        // Stored web links prove technical reachability, not offline-copy permission.
        val hosters = urlSource.resolveFromWebUrl(direct, downloadAllowed = false)
        return if (hosters.isEmpty()) {
            MovieSeriesSourceResult.NoMatch
        } else {
            MovieSeriesSourceResult.Found(hosters)
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
