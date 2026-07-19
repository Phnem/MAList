package com.example.myapplication.data.ai

/** Успешный ответ + провайдер, который его вернул. */
data class AiRoutedResult(
    val provider: AiProvider,
    val text: String,
)

/**
 * Высокоуровневый вход для AI-задач (дубляж названий и пр.): выбирает провайдера через
 * «гонку хостов» ([AiProviderLatencyProber]) и по очереди пробует «быстрый → медленный»,
 * пока кто-то не ответит успехом. Инкапсулирует получение ключа из [AiCredentialsStore] и
 * низкоуровневые вызовы [AiLlmEndpoint].
 *
 * Возвращает [Result] с [AiRoutedResult] (первый успех) либо агрегированную ошибку всех
 * попыток. Нет подключённых провайдеров → [NoAiProviderException].
 */
class AiLlmFallbackRouter(
    private val credentialsStore: AiCredentialsStore,
    private val endpoint: AiLlmEndpoint,
    private val prober: AiProviderLatencyProber,
) {
    suspend fun completeText(
        userPrompt: String,
        systemPrompt: String? = null,
        jsonMode: Boolean = false,
    ): Result<AiRoutedResult> = route(visionOnly = false) { provider, key ->
        endpoint.completeText(provider, key, userPrompt, systemPrompt, jsonMode)
    }

    suspend fun completeWithImage(
        userPrompt: String,
        imageBase64: String,
        mimeType: String,
        systemPrompt: String? = null,
        jsonMode: Boolean = false,
    ): Result<AiRoutedResult> = route(visionOnly = true) { provider, key ->
        endpoint.completeWithImage(provider, key, userPrompt, imageBase64, mimeType, systemPrompt, jsonMode)
    }

    private suspend fun route(
        visionOnly: Boolean,
        call: suspend (AiProvider, String) -> Result<String>,
    ): Result<AiRoutedResult> {
        val ordered = prober.orderedByLatency(visionOnly = visionOnly)
        if (ordered.isEmpty()) {
            return Result.failure(NoAiProviderException(visionOnly))
        }
        val errors = mutableListOf<String>()
        var rateLimit: AiRateLimitException? = null
        for (provider in ordered) {
            val key = credentialsStore.getApiKey(provider) ?: continue
            call(provider, key).fold(
                onSuccess = { return Result.success(AiRoutedResult(provider, it)) },
                onFailure = { e ->
                    errors += "${provider.displayName}: ${e.message ?: e::class.simpleName}"
                    // Держим ближайший retry-after — воркер по нему решит, сколько ждать.
                    if (e is AiRateLimitException && (rateLimit == null || e.retryAfterMs < rateLimit!!.retryAfterMs)) {
                        rateLimit = e
                    }
                },
            )
        }
        // Все провайдеры упали, и хотя бы один — по rate limit: отдаём именно его, чтобы вызывающий
        // код мог сделать backoff и повтор, а не считать это окончательным провалом.
        rateLimit?.let { return Result.failure(it) }
        return Result.failure(
            IllegalStateException("All AI providers failed: ${errors.joinToString("; ")}")
        )
    }
}

/** Нет подключённого (или vision-capable) провайдера для AI-задачи. */
class NoAiProviderException(val visionRequired: Boolean) : Exception(
    if (visionRequired) "No vision-capable AI provider connected" else "No AI provider connected"
)
