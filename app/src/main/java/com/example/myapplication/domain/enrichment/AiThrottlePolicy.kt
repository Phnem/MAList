package com.example.myapplication.domain.enrichment

import java.util.concurrent.TimeUnit

/**
 * Ритм фоновых AI-переводов, чтобы не выжечь бесплатную квоту (напр. Gemini 429 по дневному лимиту).
 *
 * Идея: бесплатный API закрывает большинство пробелов; в AI уходит лишь остаток, и уходит ПОТИХОНЬКУ —
 * по одному переводу за фоновый заход, а между заходами выдерживается пауза [delayBetweenAiCallsMs].
 * Чем больше остаток, тем реже (растягиваем на бо́льший срок). При нескольких подключённых провайдерах
 * нагрузка размазывается роутером ([com.example.myapplication.data.ai.AiLlmFallbackRouter] уходит на
 * следующего при 429), поэтому эффективную паузу можно пропорционально сократить.
 */
object AiThrottlePolicy {

    private val MIN_DELAY_MS = TimeUnit.MINUTES.toMillis(5)
    private val SMALL_DELAY_MS = TimeUnit.MINUTES.toMillis(15)
    private val MEDIUM_DELAY_MS = TimeUnit.MINUTES.toMillis(30)
    private val LARGE_DELAY_MS = TimeUnit.MINUTES.toMillis(60)

    /**
     * @param residualAiCount сколько названий ещё ждут AI-перевода (после бесплатного API).
     * @param providerCount число подключённых AI-провайдеров (≥1 для деления паузы).
     * @return пауза до следующего AI-вызова; 0, если остатка нет.
     */
    fun delayBetweenAiCallsMs(residualAiCount: Int, providerCount: Int): Long {
        if (residualAiCount <= 0) return 0L
        val base = when {
            residualAiCount <= 3 -> SMALL_DELAY_MS
            residualAiCount <= 10 -> MEDIUM_DELAY_MS
            else -> LARGE_DELAY_MS
        }
        val providers = providerCount.coerceAtLeast(1)
        return (base / providers).coerceAtLeast(MIN_DELAY_MS)
    }
}
