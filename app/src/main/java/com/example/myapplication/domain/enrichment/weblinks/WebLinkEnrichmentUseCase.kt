package com.example.myapplication.domain.enrichment.weblinks

import android.util.Log
import com.example.myapplication.data.local.WebLinksStore
import com.example.myapplication.data.models.Anime
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.WebLinkResolver
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Резолвит прямые ссылки на страницы тайтла по одобренному списку сайтов ([WebLinkResolver])
 * и складывает их в [WebLinksStore]. Запускается независимым воркером
 * (WebLinkEnrichmentWorker) — НЕ фазой длинного FullEnrichment, чтобы не умирать вместе с ним.
 *
 * Язык приложения определяет набор сайтов: RU → RU-сайты по русскому названию, EN → EN-сайты по
 * английскому. Пустой результат тоже сохраняется как «проверено» (TTL), чтобы не долбить сайты.
 * Запись в [WebLinksStore] идёт в [NonCancellable] — если воркер остановят, уже посчитанное сохранится.
 */
class WebLinkEnrichmentUseCase(
    private val resolver: WebLinkResolver,
    private val store: WebLinksStore,
) {

    suspend fun enrichOne(anime: Anime, language: AppLanguage): Boolean {
        val queries = when (language) {
            AppLanguage.RU -> listOfNotNull(anime.titleRu, anime.title, anime.titleEn)
            AppLanguage.EN -> listOfNotNull(anime.titleEn, anime.title, anime.titleRu)
        }.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        Log.i(TAG, "enrichOne id=${anime.id} '${anime.title}' lang=$language queries=$queries")
        val linksBySite = linkedMapOf<String, ResolvedWebLink>()
        for (query in queries.take(MAX_TITLE_ALIASES)) {
            val resolutions = runCatching {
                when (language) {
                    AppLanguage.RU -> resolver.resolveRu(query)
                    AppLanguage.EN -> resolver.resolveEn(query)
                }
            }.onFailure {
                Log.w(TAG, "resolve failed id=${anime.id} query='$query': ${it.message}")
            }.getOrDefault(emptyList())
            resolutions.forEach { resolution ->
                linksBySite.putIfAbsent(
                    resolution.siteKey,
                    ResolvedWebLink(resolution.siteKey, resolution.url),
                )
            }
            if (linksBySite.size >= DESIRED_SITE_COUNT) break
        }
        val links = linksBySite.values.toList()
        // Сохраняем ВНЕ отмены — иначе остановка воркера теряет уже найденное.
        withContext(NonCancellable) {
            store.putLinks(anime.id, language, links)
        }
        Log.i(TAG, "enrichOne STORED id=${anime.id} links=${links.size} sites=${links.map { it.siteKey }}")
        return links.isNotEmpty()
    }

    /**
     * Обогащает тайтлы без свежих ссылок для [language]. [limit] == null → все. Между тайтлами —
     * пауза. Возвращает число обработанных.
     */
    suspend fun enrichBatch(
        allAnime: List<Anime>,
        language: AppLanguage,
        limit: Int?,
        shouldStop: () -> Boolean = { false },
    ): Int {
        store.ensureLoaded()
        withContext(NonCancellable) { store.retainOnly(allAnime.map { it.id }.toSet()) }
        val stale = allAnime.filterNot { store.isFresh(it.id, language) }
        val target = if (limit != null) stale.take(limit) else stale
        Log.i(TAG, "enrichBatch START total=${allAnime.size} stale=${stale.size} target=${target.size} lang=$language limit=$limit")
        var done = 0
        for (anime in target) {
            if (shouldStop()) {
                Log.i(TAG, "enrichBatch STOP requested after $done")
                break
            }
            runCatching { enrichOne(anime, language) }
                .onFailure { Log.w(TAG, "enrichOne threw for '${anime.title}': ${it.message}") }
            done++
            delay(ITEM_DELAY_MS)
        }
        Log.i(TAG, "enrichBatch DONE processed=$done remainingStale=${countStale(allAnime, language)}")
        return done
    }

    /** Сколько тайтлов ещё без свежих ссылок (для решения о self-reschedule). */
    fun countStale(allAnime: List<Anime>, language: AppLanguage): Int {
        return allAnime.count { !store.isFresh(it.id, language) }
    }

    companion object {
        private const val TAG = "WebLinkEnrichment"
        private const val ITEM_DELAY_MS = 300L
        private const val MAX_TITLE_ALIASES = 3
        private const val DESIRED_SITE_COUNT = 3
    }
}
