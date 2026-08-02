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
    private val animeHeavenSource: AnimeHeavenSource,
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
            AppLanguage.EN -> resolveEn(anime, episodeNumber, seasonInfo)
        }
        val normalized = resolved.withPropagatedSkipReference().playableHosters()
        Log.i(
            TAG,
            "Resolved ${normalized.size} hosters / " +
                "${normalized.sumOf { it.videos.orEmpty().size }} videos for " +
                "${anime.title} S${seasonInfo?.seasonNumber ?: 1}E$episodeNumber [$language]",
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
        val seasonQuery = anime.seasonSourceQuery(seasonInfo)
        val aniKnown = links.firstOrNull {
            it.siteKey == WebLinkSites.ANILIBRIA_TOP ||
                it.siteKey == WebLinkSites.ANILIBRIA_TV
        }?.url
        val seasonSpecificAniUrl = aniKnown.takeIf {
            seasonInfo == null || seasonInfo.seasonNumber <= 1
        }
        val jutKnown = links.firstOrNull { it.siteKey == WebLinkSites.JUTSU }?.url

        // Exact stored links are the most reliable title disambiguation and avoid a search roundtrip.
        val exact = supervisorScope {
            buildList<suspend () -> List<VetroHoster>> {
                if (seasonSpecificAniUrl != null) {
                    add {
                        aniLibriaSource.resolveEpisode(
                            seasonQuery.anime,
                            episodeNumber,
                            seasonSpecificAniUrl,
                        )
                    }
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
                        aniLibriaSource.resolveEpisode(
                            anime = anime,
                            episodeNumber = episodeNumber,
                            seasonInfo = seasonInfo,
                        )
                    })
                }
                // AnimeGo больше не выпадает из гонки на сезонах без собственного названия:
                // он получает франшизные алиасы и ищет ими.
                add("AnimeGo" to {
                    animeGoSource.resolveEpisode(seasonQuery, episodeNumber)
                })
                add("Kodik" to { kodikSource.resolveEpisode(anime, episodeNumber, seasonInfo) })
                // jut.su — источник ТАЙМСКИПОВ, не видео: сайт перешёл на новый плеер и отдаёт
                // в разметке заглушки вместо адресов (§ .scratch/season-source-resolution,
                // TICKET-02). Конфиг с таймингами по-прежнему приезжает, поэтому ветку держим,
                // но видео из неё отбрасываем — как это давно делает EN-путь.
                add("jut.su reference" to {
                    jutSuSource.resolveEpisode(anime, episodeNumber, seasonInfo, jutKnown)
                        .map { it.copy(videos = emptyList()) }
                })
            }.map { (label, block) ->
                async {
                    val timeout = if (label == "Kodik") KODIK_SOURCE_TIMEOUT_MS else SOURCE_TIMEOUT_MS
                    safeResolve(label, timeout, block)
                }
            }.awaitAll().flatten()
        }
        val resolved = exact + native
        if (resolved.hasPlayableVideo()) return resolved

        return resolved + resolveDirectFallback(links.map { it.url })
    }

    private suspend fun resolveEn(
        anime: Anime,
        episodeNumber: Int,
        seasonInfo: SeasonInfo?,
    ): List<VetroHoster> {
        val (native, jutReference) = supervisorScope {
            // AnimeHeaven needs three sequential page loads (search → title → gate), hence its own budget.
            val video = async {
                safeResolve("AnimeHeaven", EN_SOURCE_TIMEOUT_MS) {
                    animeHeavenSource.resolveEpisode(anime, episodeNumber, seasonInfo)
                }
            }
            val reference = async {
                safeResolve("jut.su reference", SOURCE_TIMEOUT_MS) {
                    jutSuSource.resolveEpisode(anime, episodeNumber, seasonInfo)
                        .map { it.copy(videos = emptyList()) }
                }
            }
            video.await() to reference.await()
        }
        if (native.hasPlayableVideo()) return native + jutReference

        webLinksStore.ensureLoaded()
        val links = webLinksStore.flow.value[anime.id]?.enLinks.orEmpty()
        return jutReference + resolveDirectFallback(links.map { it.url })
    }

    private suspend fun resolveDirectFallback(urls: List<String>): List<VetroHoster> {
        val direct = urls.firstOrNull(urlSource::canResolveDirect) ?: return emptyList()
        return safeResolve("direct URL", DIRECT_TIMEOUT_MS) {
            urlSource.resolveFromWebUrl(direct)
        }
    }

    /**
     * Исход КАЖДОЙ ветки попадает в лог, включая пустую. Раньше молчание источника было
     * неотличимо от того, что его вообще не запускали, и отказ разбирался по сырым строкам Ktor.
     */
    private suspend fun safeResolve(
        label: String,
        timeoutMs: Long,
        block: suspend () -> List<VetroHoster>,
    ): List<VetroHoster> {
        val startedAt = System.currentTimeMillis()
        val resolved = withTimeoutOrNull(timeoutMs) {
            runCatching { block() }
                .onFailure { Log.w(TAG, "$label failed: ${it.message}") }
                .getOrElse { emptyList() }
        }
        val elapsedMs = System.currentTimeMillis() - startedAt
        if (resolved == null) {
            Log.w(TAG, "$label timed out after ${timeoutMs}ms")
            return emptyList()
        }
        val playable = resolved.sumOf { hoster ->
            hoster.videos.orEmpty().count { isPlayableVetroVideoUrl(it.url) }
        }
        Log.i(TAG, "$label → ${resolved.size} hoster(s), $playable playable video(s), ${elapsedMs}ms")
        return resolved
    }

    suspend fun resolveBestVideo(hosters: List<VetroHoster>): VetroVideo? {
        val flat = hosters.flatMap { it.videos.orEmpty() }
        flat.firstOrNull { it.isPreferred }?.let { return it }
        return flat.maxByOrNull { it.resolution ?: 0 }
    }

    companion object {
        private const val TAG = "SourceEngine"
        private const val EXACT_SOURCE_TIMEOUT_MS = 8_000L
        private const val SOURCE_TIMEOUT_MS = 12_000L
        private const val KODIK_SOURCE_TIMEOUT_MS = 24_000L
        private const val EN_SOURCE_TIMEOUT_MS = 20_000L
        private const val DIRECT_TIMEOUT_MS = 5_000L
    }
}
