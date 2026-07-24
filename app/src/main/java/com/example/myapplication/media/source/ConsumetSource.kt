package com.example.myapplication.media.source

import android.util.Log
import com.example.myapplication.data.models.Anime
import com.example.myapplication.sync.TitleMatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * EN native source — Consumet Gogoanime API (port of `extractors/en/consumet.py`).
 * Same resolve → play | download contract as RU sources.
 */
class ConsumetSource(
    client: HttpClient,
) : VetroHttpSource(client) {

    override val name: String = "Consumet/Gogoanime"
    override val baseUrl: String = "https://api.consumet.org"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolveEpisode(anime: Anime, episodeNumber: Int): List<VetroHoster> {
        val query = anime.titleEn?.takeIf { it.isNotBlank() }
            ?: anime.title.takeIf { it.isNotBlank() }
            ?: anime.titleRu
            ?: return emptyList()

        val hit = searchBest(query, anime) ?: return emptyList()
        val animeId = hit.id
        val episodes = hit.episodes.ifEmpty { fetchEpisodes(animeId) }
        val ep = episodes.firstOrNull { it.number == episodeNumber }
            ?: return emptyList()

        val videos = fetchWatchSources(ep.id)
        if (videos.isEmpty()) return emptyList()
        return listOf(
            VetroHoster(
                name = "Gogoanime",
                url = "$baseUrl/anime/gogoanime/watch/${ep.id}",
                videos = videos,
                lazy = false,
            )
        )
    }

    private data class SearchHit(val id: String, val title: String, val episodes: List<EpRef>)
    private data class EpRef(val id: String, val number: Int)

    private suspend fun searchBest(query: String, anime: Anime): SearchHit? = runCatching {
        val enc = URLEncoder.encode(query, "UTF-8")
        val body = client.get("$baseUrl/anime/gogoanime/$enc") {
            enHeaders()
        }.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray.orEmpty()
        if (results.isEmpty()) return@runCatching null

        val local = listOfNotNull(anime.titleEn, anime.title, anime.titleRu)
        val scored = results.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: id
            val score = local.maxOfOrNull { TitleMatcher.bestScore(it, listOf(title)) } ?: 0.0
            val eps = obj["episodes"]?.jsonArray.orEmpty().mapNotNull { parseEp(it as? JsonObject) }
            Triple(SearchHit(id, title, eps), score, title)
        }
        val matched = scored.filter { it.second >= TITLE_MATCH_THRESHOLD }.maxByOrNull { it.second }
            ?: scored.maxByOrNull { it.second }
        matched?.let {
            Log.i(TAG, "Matched '${it.third}' score=${it.second}")
            it.first
        }
    }.onFailure { Log.w(TAG, "search failed: ${it.message}") }.getOrNull()

    private suspend fun fetchEpisodes(animeId: String): List<EpRef> = runCatching {
        val body = client.get("$baseUrl/anime/gogoanime/info/$animeId") {
            enHeaders()
        }.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        root["episodes"]?.jsonArray.orEmpty().mapNotNull { parseEp(it as? JsonObject) }
    }.getOrElse { emptyList() }

    private fun parseEp(obj: JsonObject?): EpRef? {
        obj ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val num = obj["number"]?.jsonPrimitive?.intOrNull
            ?: obj["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: return null
        return EpRef(id, num)
    }

    private suspend fun fetchWatchSources(episodeId: String): List<VetroVideo> = runCatching {
        val body = client.get("$baseUrl/anime/gogoanime/watch/$episodeId") {
            enHeaders()
        }.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val referer = root["headers"]?.jsonObject?.get("Referer")?.jsonPrimitive?.contentOrNull
            ?: "https://gogoplay.io/"
        val sources = root["sources"]?.jsonArray.orEmpty()

        val videos = sources.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            var url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!url.startsWith("http")) return@mapNotNull null
            if (url.endsWith(".mpd")) url = url.replace(".mpd", ".m3u8")
            val quality = obj["quality"]?.jsonPrimitive?.contentOrNull ?: "default"
            val isM3u8 = obj["isM3U8"]?.jsonPrimitive?.booleanOrNull == true ||
                url.contains("m3u8")
            if (!isM3u8 && !url.contains("m3u8") && !url.contains("mpd")) {
                // still allow progressive
            }
            val res = when {
                "1080" in quality -> 1080
                "720" in quality -> 720
                "480" in quality -> 480
                "360" in quality -> 360
                else -> null
            }
            VetroVideo(
                url = url,
                label = if (quality == "default") "auto" else quality,
                resolution = res,
                headers = mapOf(
                    "Referer" to referer,
                    "User-Agent" to DEFAULT_UA,
                ),
                isPreferred = quality == "1080p" || res == 1080,
            )
        }.sortedByDescending { it.resolution ?: 0 }

        if (videos.isEmpty()) emptyList()
        else if (videos.none { it.isPreferred }) {
            listOf(videos.first().copy(isPreferred = true)) + videos.drop(1)
        } else videos
    }.onFailure { Log.w(TAG, "watch failed: ${it.message}") }.getOrElse { emptyList() }

    private fun io.ktor.client.request.HttpRequestBuilder.enHeaders() {
        header(HttpHeaders.UserAgent, DEFAULT_UA)
        header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
    }

    override fun headersBuilder(): Map<String, String> = mapOf(
        HttpHeaders.UserAgent to DEFAULT_UA,
        HttpHeaders.AcceptLanguage to "en-US,en;q=0.9",
    )

    companion object {
        private const val TAG = "ConsumetSource"
    }
}
