package com.example.myapplication.data.local

import android.content.Context
import android.util.Log
import com.example.myapplication.domain.enrichment.weblinks.ResolvedWebLink
import com.example.myapplication.domain.enrichment.weblinks.WebLinksEntry
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Файловый кэш прямых ссылок на страницы тайтла по одобренным сайтам (filesDir, atomic rename),
 * по образцу [RecommendationCacheStore] — без миграции схемы SQLDelight. Ключ — animeId.
 * В памяти держим Map в [flow], чтобы UI реактивно показывал найденные иконки.
 */
class WebLinksStore(context: Context) {

    private val file = File(context.filesDir, CACHE_FILE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var loaded = false

    private val _flow = MutableStateFlow<Map<String, WebLinksEntry>>(emptyMap())
    val flow: StateFlow<Map<String, WebLinksEntry>> = _flow.asStateFlow()

    private val serializer = MapSerializer(String.serializer(), WebLinksEntry.serializer())

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            val map = withContext(Dispatchers.IO) {
                // Чистим кэши прежних версий формата ссылок (иначе старые/неверные ссылки живут TTL).
                runCatching { OLD_CACHE_FILES.forEach { File(file.parentFile, it).delete() } }
                runCatching {
                    if (!file.exists()) emptyMap()
                    else json.decodeFromString(serializer, file.readText())
                }.getOrElse {
                    Log.w(TAG, "Failed to read web-links cache", it)
                    emptyMap()
                }
            }
            _flow.value = map
            loaded = true
        }
    }

    /** Ссылки для выбранного языка (то, что показывает UI). */
    fun linksFor(animeId: String, language: AppLanguage): List<ResolvedWebLink> {
        val e = _flow.value[animeId] ?: return emptyList()
        return if (language == AppLanguage.RU) e.ruLinks else e.enLinks
    }

    /** Свежесть по языку: резолвили и не истёк TTL. */
    fun isFresh(animeId: String, language: AppLanguage, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val e = _flow.value[animeId] ?: return false
        val at = if (language == AppLanguage.RU) e.ruResolvedAt else e.enResolvedAt
        val links = if (language == AppLanguage.RU) e.ruLinks else e.enLinks
        val ttl = when {
            links.isEmpty() -> EMPTY_RESULT_TTL_MILLIS
            links.size < HEALTHY_RESULT_SIZE -> PARTIAL_RESULT_TTL_MILLIS
            else -> TTL_MILLIS
        }
        return at > 0 && nowMillis - at <= ttl
    }

    /** Записать результат резолва для одного языка (пустой список тоже сохраняем — метка «проверено»). */
    suspend fun putLinks(animeId: String, language: AppLanguage, links: List<ResolvedWebLink>) {
        ensureLoaded()
        mutex.withLock {
            val now = System.currentTimeMillis()
            val prev = _flow.value[animeId] ?: WebLinksEntry(animeId = animeId)
            val updated = if (language == AppLanguage.RU) {
                prev.copy(ruLinks = links, ruResolvedAt = now)
            } else {
                prev.copy(enLinks = links, enResolvedAt = now)
            }
            val map = _flow.value.toMutableMap().apply { put(animeId, updated) }
            _flow.value = map
            persist(map)
        }
    }

    /** Убрать записи для тайтлов, которых больше нет в коллекции. */
    suspend fun retainOnly(existingIds: Set<String>) {
        ensureLoaded()
        mutex.withLock {
            val map = _flow.value.filterKeys { it in existingIds }
            if (map.size != _flow.value.size) {
                _flow.value = map
                persist(map)
            }
        }
    }

    private suspend fun persist(map: Map<String, WebLinksEntry>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "$CACHE_FILE.tmp")
            tmp.writeText(json.encodeToString(serializer, map))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Log.w(TAG, "Failed to write web-links cache", it) }
    }

    companion object {
        private const val TAG = "WebLinksStore"
        // v8: use case дошёл до multi-alias резолва (до 3 вариантов названия) + резолвер сканирует до
        // 10 кандидатов на совпадение (раньше — 1). Большинство тайтлов в старом кэше (v7 и раньше)
        // резолвились ДО этих изменений одним запросом и первым же кандидатом — застряли на TTL 14
        // дней с меньшим числом источников, чем нашёл бы текущий код. Сброс форсирует полный re-resolve.
        private const val CACHE_FILE = "web_links_cache_v9.json"
        private val OLD_CACHE_FILES = listOf(
            "web_links_cache.json", "web_links_cache_v2.json", "web_links_cache_v3.json",
            "web_links_cache_v4.json", "web_links_cache_v5.json", "web_links_cache_v6.json",
            "web_links_cache_v7.json", "web_links_cache_v8.json",
        )
        /** 14 дней — каталоги сайтов меняются медленно; новый тайтл догоним по TTL. */
        const val TTL_MILLIS = 14L * 24 * 60 * 60 * 1000
        private const val HEALTHY_RESULT_SIZE = 2
        private const val PARTIAL_RESULT_TTL_MILLIS = 2L * 60 * 60 * 1000
        private const val EMPTY_RESULT_TTL_MILLIS = 30L * 60 * 1000
    }
}
