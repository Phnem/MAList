package com.example.myapplication.media.source

import android.util.Log
import com.example.myapplication.data.local.WebLinksStore
import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.WebLinkSites
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bounded, native-first media resolution.
 *
 * Page URLs are never sent to embedded Python. Native sources run with a hard timeout, and the
 * direct URL fallback is attempted only after every applicable native source returned no video.
 */
class SourceEngine(
    private val aniLibriaSource: AniLibriaSource,
    private val animeGoSource: AnimeGoSource,
    private val jutSuSource: JutSuSource,
    private val kodikSource: KodikSource,
    private val consumetSource: ConsumetSource,
    private val urlSource: UrlSource,
    private val webLinksStore: WebLinksStore,
) {
    suspend fun resolveHosters(
        anime: Anime,
        episodeNumber: Int,
        seasonInfo: SeasonInfo? = null,
        language: AppLanguage = AppLanguage.RU,
    ): List<VetroHoster> {
        if (episodeNumber <= 0) return emptyList()
        val resolved = when (language) {
            AppLanguage.RU -> resolveRu(anime, episodeNumber, seasonInfo)
            AppLanguage.EN -> resolveEn(anime, episodeNumber)
        }
        val normalized = resolved.normalizeHosters()
        Log.i(
            TAG,
            "Resolved ${normalized.size} hosters / " +
                "${normalized.sumOf { it.videos.orEmpty().size }} videos for " +
                "${anime.title} ep $episodeNumber [$language]",
        )
        return normalized
    }

    private suspend fun resolveRu(
        anime: Anime,
        episodeNumber: Int,
        seasonInfo: SeasonInfo?,
    ): List<VetroHoster> {
        webLinksStore.ensureLoaded()
        val links = webLinksStore.flow.value[anime.id]?.ruLinks.orEmpty()
        val aniKnown = links.firstOrNull {
            it.siteKey == WebLinkSites.ANILIBRIA_TOP ||
                it.siteKey == WebLinkSites.ANILIBRIA_TV
        }?.url
        val seasonSpecificAniUrl = aniKnown.takeIf {
            (seasonInfo?.seasonNumber?.coerceAtLeast(1) ?: 1) == 1
        }
        val jutKnown = links.firstOrNull { it.siteKey == WebLinkSites.JUTSU }?.url

        // Exact stored links are the most reliable title disambiguation and avoid a search roundtrip.
        val exact = supervisorScope {
            buildList<suspend () -> List<VetroHoster>> {
                if (seasonSpecificAniUrl != null) {
                    add { aniLibriaSource.resolveEpisode(anime, episodeNumber, seasonSpecificAniUrl) }
                }
                if (jutKnown != null) {
                    add { jutSuSource.resolveEpisode(anime, episodeNumber, seasonInfo, jutKnown) }
                }
            }.map { block ->
                async { safeResolve("known source", EXACT_SOURCE_TIMEOUT_MS, block) }
            }.awaitAll().flatten()
        }
        // Keep resolving: exact links are fast, but they must not hide other studios.

        val native = supervisorScope {
            buildList<Pair<String, suspend () -> List<VetroHoster>>> {
                if (seasonSpecificAniUrl == null) {
                    add("AniLiberty" to {
                        aniLibriaSource.resolveEpisode(anime, episodeNumber)
                    })
                }
                add("AnimeGo" to { animeGoSource.resolveEpisode(anime, episodeNumber) })
                add("Kodik" to { kodikSource.resolveEpisode(anime, episodeNumber) })
                if (jutKnown == null) {
                    add("jut.su" to {
                        jutSuSource.resolveEpisode(anime, episodeNumber, seasonInfo)
                    })
                }
            }.map { (label, block) ->
                async {
                    val timeout = if (label == "Kodik") KODIK_SOURCE_TIMEOUT_MS else SOURCE_TIMEOUT_MS
                    safeResolve(label, timeout, block)
                }
            }.awaitAll().flatten()
        }
        if (exact.isNotEmpty() || native.isNotEmpty()) return exact + native

        return resolveDirectFallback(links.map { it.url })
    }

    private suspend fun resolveEn(
        anime: Anime,
        episodeNumber: Int,
    ): List<VetroHoster> {
        val native = safeResolve("Consumet", SOURCE_TIMEOUT_MS) {
            consumetSource.resolveEpisode(anime, episodeNumber)
        }
        if (native.isNotEmpty()) return native

        webLinksStore.ensureLoaded()
        val links = webLinksStore.flow.value[anime.id]?.enLinks.orEmpty()
        return resolveDirectFallback(links.map { it.url })
    }

    private suspend fun resolveDirectFallback(urls: List<String>): List<VetroHoster> {
        val direct = urls.firstOrNull(urlSource::canResolveDirect) ?: return emptyList()
        return safeResolve("direct URL", DIRECT_TIMEOUT_MS) {
            urlSource.resolveFromWebUrl(direct)
        }
    }

    private suspend fun safeResolve(
        label: String,
        timeoutMs: Long,
        block: suspend () -> List<VetroHoster>,
    ): List<VetroHoster> {
        return withTimeoutOrNull(timeoutMs) {
            runCatching { block() }
                .onFailure { Log.w(TAG, "$label failed: ${it.message}") }
                .getOrElse { emptyList() }
        } ?: run {
            Log.w(TAG, "$label timed out after ${timeoutMs}ms")
            emptyList()
        }
    }

    suspend fun resolveBestVideo(hosters: List<VetroHoster>): VetroVideo? {
        val flat = hosters.flatMap { it.videos.orEmpty() }
        flat.firstOrNull { it.isPreferred }?.let { return it }
        return flat.maxByOrNull { it.resolution ?: 0 }
    }

    private fun List<VetroHoster>.normalizeHosters(): List<VetroHoster> =
        mapNotNull { hoster ->
            val videos = hoster.videos.orEmpty()
                .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
                .distinctBy { it.url }
            hoster.copy(videos = videos).takeIf { videos.isNotEmpty() }
        }.distinctBy { hoster ->
            hoster.videos.orEmpty().joinToString("|") { it.url }
        }

    companion object {
        private const val TAG = "SourceEngine"
        private const val EXACT_SOURCE_TIMEOUT_MS = 8_000L
        private const val SOURCE_TIMEOUT_MS = 12_000L
        private const val KODIK_SOURCE_TIMEOUT_MS = 24_000L
        private const val DIRECT_TIMEOUT_MS = 5_000L
    }
}
