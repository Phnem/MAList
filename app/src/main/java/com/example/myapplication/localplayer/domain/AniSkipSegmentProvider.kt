package com.example.myapplication.localplayer.domain

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Тайминги опенинга/эндинга из открытой community-базы AniSkip (v2) — стандартный источник
 * skip-времён, которым пользуются опенсорс-плееры. Запрос по MAL id + номеру эпизода + длине
 * эпизода (нужна, т.к. у разных релизов разный хронометраж, и AniSkip подгоняет интервал).
 *
 * Только чтение публичного API, никакого обхода — при любой ошибке возвращаем пустой список.
 */
class AniSkipSegmentProvider(
    private val httpClient: HttpClient,
    private val franchiseMapper: FranchiseEpisodeMapper,
) : SkipSegmentProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(
        anilistId: Int?,
        malId: Int?,
        episodeNumber: Int?,
        durationMs: Long,
    ): List<SkipSegment> {
        if (episodeNumber == null || episodeNumber <= 0 || durationMs <= 0) return emptyList()

        // Сопоставляем (возможно сквозной) номер конкретному сезону франшизы; при неудаче — сырой malId+номер.
        val mapped = runCatching { franchiseMapper.resolve(anilistId, malId, episodeNumber) }.getOrNull()
        val targetMalId = mapped?.malId ?: malId ?: return emptyList()
        val targetEpisode = mapped?.episode ?: episodeNumber
        val lengthSec = durationMs / 1000.0

        val url = buildString {
            append("https://api.aniskip.com/v2/skip-times/")
            append(targetMalId).append('/').append(targetEpisode)
            append("?types=op&types=ed&types=mixed-op&types=mixed-ed&types=recap")
            append("&episodeLength=").append(lengthSec)
        }

        return runCatching {
            val response = httpClient.get(url)
            if (response.status.value !in 200..299) return emptyList()
            parse(response.bodyAsText())
        }.getOrElse {
            Log.w(TAG, "AniSkip fetch failed for mal=$targetMalId ep=$targetEpisode: ${it.message}")
            emptyList()
        }
    }

    private fun parse(body: String): List<SkipSegment> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val obj = el.jsonObject
            val interval = obj["interval"]?.jsonObject ?: return@mapNotNull null
            val start = interval["startTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val end = interval["endTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            val kind = when (obj["skipType"]?.jsonPrimitive?.content) {
                "op", "mixed-op" -> SkipKind.OPENING
                "ed", "mixed-ed" -> SkipKind.ENDING
                "recap" -> SkipKind.RECAP
                else -> SkipKind.MIXED
            }
            SkipSegment(
                startMs = (start * 1000).toLong(),
                endMs = (end * 1000).toLong(),
                kind = kind,
            )
        }
    }

    private companion object {
        const val TAG = "AniSkip"
    }
}
