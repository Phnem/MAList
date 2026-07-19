package com.example.myapplication.data.ai

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.measureTimeMillis

/** Результат замера одного провайдера: латентность и доступность ключа. */
data class AiProviderLatency(
    val provider: AiProvider,
    val latencyMs: Long,
    val reachable: Boolean,
)

/**
 * «Гонка хостов»: параллельно замеряет латентность подключённых провайдеров дешёвым
 * запросом ([AiLlmEndpoint.validateApiKey] — GET списка моделей, без расхода токенов) и
 * отдаёт их отсортированными «быстрый → медленный». Недоступные (ошибка/невалидный ключ)
 * уходят в конец, но не выбрасываются — их всё ещё можно попробовать как fallback.
 *
 * Результат кэшируется на [ttlMillis]; кэш сбрасывается при изменении набора подключённых
 * провайдеров или по [forceRefresh].
 */
class AiProviderLatencyProber(
    private val credentialsStore: AiCredentialsStore,
    private val endpoint: AiLlmEndpoint,
    private val ttlMillis: Long = DEFAULT_TTL_MS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private data class Cache(
        val connectedKey: List<AiProvider>,
        val ranked: List<AiProviderLatency>,
        val timestamp: Long,
    )

    private val mutex = Mutex()
    private var cache: Cache? = null

    /** Подключённые провайдеры, отсортированные по латентности (быстрый → медленный). */
    suspend fun orderedByLatency(
        visionOnly: Boolean = false,
        forceRefresh: Boolean = false,
    ): List<AiProvider> = ranked(forceRefresh)
        .asSequence()
        .filter { !visionOnly || it.provider.supportsVision }
        .map { it.provider }
        .toList()

    /** Полные замеры (для диагностики/UI), с учётом кэша. */
    suspend fun ranked(forceRefresh: Boolean = false): List<AiProviderLatency> = mutex.withLock {
        val connected = credentialsStore.getAllConnectedProviders()
        if (connected.isEmpty()) {
            cache = null
            return emptyList()
        }
        val cached = cache
        if (
            !forceRefresh &&
            cached != null &&
            cached.connectedKey == connected &&
            (nowProvider() - cached.timestamp) < ttlMillis
        ) {
            return cached.ranked
        }

        val ranked = probe(connected)
        cache = Cache(connectedKey = connected, ranked = ranked, timestamp = nowProvider())
        ranked
    }

    fun invalidate() {
        cache = null
    }

    private suspend fun probe(providers: List<AiProvider>): List<AiProviderLatency> = coroutineScope {
        providers.map { provider ->
            async {
                val key = credentialsStore.getApiKey(provider)
                if (key.isNullOrBlank()) {
                    AiProviderLatency(provider, Long.MAX_VALUE, reachable = false)
                } else {
                    var ok = false
                    val elapsed = measureTimeMillis {
                        ok = endpoint.validateApiKey(provider, key).isSuccess
                    }
                    AiProviderLatency(provider, if (ok) elapsed else Long.MAX_VALUE, reachable = ok)
                }
            }
        }.awaitAll()
            .sortedWith(compareByDescending<AiProviderLatency> { it.reachable }.thenBy { it.latencyMs })
    }

    private companion object {
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L
    }
}
