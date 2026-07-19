package com.example.myapplication.domain.titles

import android.util.Log
import com.example.myapplication.data.ai.AiLlmFallbackRouter
import com.example.myapplication.data.ai.AiRateLimitException
import com.example.myapplication.data.ai.NoAiProviderException
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI-дубляж английского названия одной записи (fallback после API-обогащения, когда внешние
 * базы не дали english). Просит модель вернуть официальное английское название и пишет его в БД.
 */
class AiTitleTranslationUseCase(
    private val router: AiLlmFallbackRouter,
    private val localDataSource: AnimeLocalDataSource,
) {
    sealed interface Outcome {
        data class Translated(val english: String) : Outcome
        data object NoEnglishFound : Outcome
        data object NoProvider : Outcome
        data object Failed : Outcome

        /** Провайдер вернул 429. [retryAfterMs] — сколько ждать до повтора. */
        data class RateLimited(val retryAfterMs: Long) : Outcome
    }

    suspend fun translate(anime: Anime): Outcome {
        if (anime.mediaType != MediaType.ANIME) return Outcome.NoEnglishFound
        if (!anime.titleEn.isNullOrBlank()) return Outcome.Translated(anime.titleEn)

        val prompt = TitleDubbingPromptBuilder.build(anime)
        val routed = router.completeText(
            userPrompt = prompt.user,
            systemPrompt = prompt.system,
            jsonMode = true,
        ).getOrElse { e ->
            Log.w(TAG, "AI complete failed for «${anime.title}»: ${e.message}")
            return when (e) {
                is NoAiProviderException -> Outcome.NoProvider
                is AiRateLimitException -> Outcome.RateLimited(e.retryAfterMs)
                else -> Outcome.Failed
            }
        }

        val trimmed = routed.text.trim()
        if (trimmed.isEmpty()) {
            Log.w(TAG, "AI empty body for «${anime.title}» via ${routed.provider.displayName}")
            return Outcome.Failed
        }

        val english = parseEnglish(trimmed)
            ?.takeIf { it.isNotBlank() && !it.equals(anime.title, ignoreCase = true) }
        if (english == null) {
            // Явный JSON с пустым english / unknown — уверенный «не знаем».
            // Неразборчивый ответ — временный сбой, не выжигаем очередь.
            val explicitEmpty = extractJson(trimmed)?.let { jsonText ->
                runCatching {
                    Json.parseToJsonElement(jsonText).jsonObject["english"]
                        ?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                }.getOrNull()
            } != null
            Log.w(
                TAG,
                "AI no english for «${anime.title}» via ${routed.provider.displayName} " +
                    "(explicitEmpty=$explicitEmpty): ${trimmed.take(160).replace('\n', ' ')}",
            )
            return if (explicitEmpty) Outcome.NoEnglishFound else Outcome.Failed
        }

        localDataSource.setTitleEn(anime.id, english)
        Log.i(TAG, "AI translated «${anime.title}» → «$english» (${routed.provider.displayName})")
        return Outcome.Translated(english)
    }

    private fun parseEnglish(raw: String): String? {
        val jsonText = extractJson(raw) ?: return null
        val value = runCatching {
            Json.parseToJsonElement(jsonText).jsonObject["english"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.equals("null", ignoreCase = true) || value.equals("unknown", ignoreCase = true)) return null
        return value
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
        const val TAG = "AiTitleTranslate"
    }
}

/** Промпт для AI-дубляжа: строгий JSON-контракт, минимум «болтовни». */
object TitleDubbingPromptBuilder {
    data class Prompt(val system: String, val user: String)

    fun build(anime: Anime): Prompt {
        val system = "You are an anime database expert. Given a title (which may be in Russian, " +
            "Japanese romaji, or another language), return the OFFICIAL English title used by " +
            "MyAnimeList / AniList. Do not translate literally — use the recognized official English " +
            "release title. If you are not confident, return an empty string. " +
            "Respond ONLY with a single JSON object, no prose, no markdown."

        val hints = buildList {
            anime.anilistId?.let { add("AniList id: $it") }
            anime.malId?.let { add("MAL id: $it") }
            anime.shikimoriId?.let { add("Shikimori id: $it") }
        }.joinToString("\n")

        val user = buildString {
            append("Title: \"").append(anime.title).append("\"\n")
            if (hints.isNotEmpty()) append(hints).append('\n')
            append("Return JSON exactly: {\"english\": \"<official english title, or empty string if unknown>\"}")
        }
        return Prompt(system = system, user = user)
    }
}
