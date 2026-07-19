package com.example.myapplication.domain.stats

import android.util.Log
import com.example.myapplication.data.ai.AiLlmFallbackRouter
import com.example.myapplication.data.ai.AiRateLimitException
import com.example.myapplication.data.ai.NoAiProviderException
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.network.AppLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI-объяснение одной карточки статистики: «что значат эти данные, коротко и просто».
 * Вызов и разбор — по шаблону [com.example.myapplication.domain.titles.AiTitleTranslationUseCase].
 */
class StatsCardExplanationUseCase(
    private val router: AiLlmFallbackRouter,
    private val genreRepository: GenreRepository,
) {
    sealed interface Outcome {
        data class Explained(val text: String) : Outcome
        data object NoProvider : Outcome
        data object Failed : Outcome

        /** Провайдер вернул 429. [retryAfterMs] — сколько ждать до повтора. */
        data class RateLimited(val retryAfterMs: Long) : Outcome
    }

    suspend fun explain(snapshot: StatsCardSnapshot, language: AppLanguage): Outcome {
        val prompt = StatsCardExplanationPromptBuilder.build(snapshot, language, genreRepository)
        val routed = router.completeText(
            userPrompt = prompt.user,
            systemPrompt = prompt.system,
            jsonMode = true,
        ).getOrElse { e ->
            Log.w(TAG, "AI complete failed for ${snapshot.kind}: ${e.message}")
            return when (e) {
                is NoAiProviderException -> Outcome.NoProvider
                is AiRateLimitException -> Outcome.RateLimited(e.retryAfterMs)
                else -> Outcome.Failed
            }
        }

        val text = parseExplanation(routed.text)
        if (text == null) {
            Log.w(
                TAG,
                "AI unparseable body for ${snapshot.kind} via ${routed.provider.displayName}: " +
                    routed.text.take(160).replace('\n', ' '),
            )
            return Outcome.Failed
        }
        Log.i(TAG, "AI explained ${snapshot.kind} (${routed.provider.displayName}, ${text.length} chars)")
        return Outcome.Explained(text)
    }

    private fun parseExplanation(raw: String): String? {
        val jsonText = extractJson(raw) ?: return null
        return runCatching {
            Json.parseToJsonElement(jsonText).jsonObject["explanation"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Достаёт JSON-объект из ответа модели (снимает markdown-ограждения ```json ... ```). */
    private fun extractJson(raw: String): String? {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return trimmed.substring(start, end + 1)
    }

    private companion object {
        const val TAG = "StatsCardExplain"
    }
}
