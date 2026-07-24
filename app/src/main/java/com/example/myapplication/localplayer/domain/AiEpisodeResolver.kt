package com.example.myapplication.localplayer.domain

import android.util.Log
import com.example.myapplication.data.ai.AiLlmFallbackRouter
import com.example.myapplication.data.ai.NoAiProviderException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ИИ-фолбэк расстановки эпизодов: когда [EpisodeFilenameParser] не смог определить номера у части
 * файлов, отдаём модели название аниме + список исходных имён и просим сопоставить каждому имени
 * номер эпизода. Возвращаем map `originalName → episodeNumber`. Использует общий [AiLlmFallbackRouter]
 * (те же ключи/провайдеры, что и дубляж названий).
 */
class AiEpisodeResolver(private val router: AiLlmFallbackRouter) {

    sealed interface Outcome {
        data class Resolved(val numbers: Map<String, Int>) : Outcome
        data object NoProvider : Outcome
        data object Failed : Outcome
    }

    suspend fun resolve(animeTitle: String, fileNames: List<String>): Outcome {
        if (fileNames.isEmpty()) return Outcome.Resolved(emptyMap())

        val system = "You are a media library assistant. You are given an anime title and a list of " +
            "raw video filenames from one folder. Assign each filename its episode number (integer). " +
            "Use only the ordering/numbering signals inside the filenames — do not invent seasons. " +
            "If a filename clearly is not a numbered episode (extras, OP/ED, trailer), use null. " +
            "Respond ONLY with a single JSON object, no prose, no markdown."

        val user = buildString {
            append("Anime: \"").append(animeTitle).append("\"\n")
            append("Files:\n")
            fileNames.forEachIndexed { i, n -> append(i).append(". ").append(n).append('\n') }
            append(
                "\nReturn JSON exactly: {\"episodes\":[{\"name\":\"<exact filename>\"," +
                    "\"episode\":<int or null>}]}",
            )
        }

        val routed = router.completeText(userPrompt = user, systemPrompt = system, jsonMode = true)
            .getOrElse { e ->
                Log.w(TAG, "AI resolve failed: ${e.message}")
                return if (e is NoAiProviderException) Outcome.NoProvider else Outcome.Failed
            }

        val map = parse(routed.text, fileNames)
        return if (map.isEmpty()) Outcome.Failed else Outcome.Resolved(map)
    }

    private fun parse(raw: String, fileNames: List<String>): Map<String, Int> {
        val jsonText = extractJson(raw) ?: return emptyMap()
        val known = fileNames.toHashSet()
        return runCatching {
            val arr = Json.parseToJsonElement(jsonText).jsonObject["episodes"]?.jsonArray
                ?: return emptyMap()
            buildMap {
                for (el in arr) {
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                    val ep = obj["episode"]?.jsonPrimitive?.intOrNull ?: continue
                    if (name in known && ep >= 0) put(name, ep)
                }
            }
        }.getOrElse {
            Log.w(TAG, "AI resolve parse error", it)
            emptyMap()
        }
    }

    private fun extractJson(raw: String): String? {
        val t = raw.trim()
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        return if (start == -1 || end <= start) null else t.substring(start, end + 1)
    }

    private companion object {
        const val TAG = "AiEpisodeResolver"
    }
}
