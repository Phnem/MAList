package com.example.myapplication.media.source

import android.util.Log
import com.example.myapplication.data.models.Anime
import com.example.myapplication.sync.TitleMatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

/**
 * Native AnimeGo/AniBoom resolver. Search always includes the requested title and never falls
 * back to the first unrelated card.
 */
class AnimeGoSource(
    client: HttpClient,
) : VetroHttpSource(client) {

    override val name: String = "AnimeGo"
    override val baseUrl: String = "https://animego.me"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolveEpisode(anime: Anime, episodeNumber: Int): List<VetroHoster> {
        val query = anime.titleRu?.takeIf { it.isNotBlank() }
            ?: anime.title.takeIf { it.isNotBlank() }
            ?: anime.titleEn
            ?: return emptyList()
        val localTitles = listOfNotNull(anime.title, anime.titleRu, anime.titleEn)
        val best = search(query)
            .map { hit ->
                hit to (
                    localTitles.maxOfOrNull {
                        TitleMatcher.bestScore(it, listOf(hit.title))
                    } ?: 0.0
                )
            }
            .filter { it.second >= TITLE_MATCH_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
            ?: return emptyList()

        val embedUrl = fetchAniboomEmbed(best.id, episodeNumber) ?: return emptyList()
        val stream = scrapeAniboom(embedUrl) ?: return emptyList()
        val video = VetroVideo(
            url = stream,
            label = "720p",
            resolution = 720,
            headers = mapOf(
                "Referer" to "https://aniboom.one/",
                "Origin" to "https://aniboom.one",
                "User-Agent" to DEFAULT_UA,
            ),
            isPreferred = true,
        )
        Log.i(TAG, "Resolved '${best.title}' ep=$episodeNumber")
        return listOf(
            VetroHoster(
                name = "AnimeGo / AniBoom",
                url = embedUrl,
                videos = listOf(video),
            )
        )
    }

    private suspend fun search(query: String): List<SearchHit> = runCatching {
        val body = client.get("$baseUrl/search/all") {
            parameter("type", "all")
            parameter("q", query)
            header("X-Requested-With", "XMLHttpRequest")
            header("Referer", "$baseUrl/")
        }.bodyAsText()

        parseSearchJson(body).ifEmpty {
            Jsoup.parse(body, baseUrl)
                .select("a[href*=/anime/]")
                .mapNotNull { anchor ->
                    val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                    val id = Regex("""(\d+)/?$""").find(href)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?: return@mapNotNull null
                    val title = anchor.text().trim().ifBlank { anchor.attr("title").trim() }
                    title.takeIf { it.isNotBlank() }?.let { SearchHit(id, it) }
                }
                .distinctBy { it.id }
                .take(15)
        }
    }.onFailure { Log.w(TAG, "search '$query' failed: ${it.message}") }
        .getOrElse { emptyList() }

    private fun parseSearchJson(body: String): List<SearchHit> = runCatching {
        val root = json.parseToJsonElement(body)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root["items"]?.jsonArray
                ?: root["results"]?.jsonArray
                ?: return emptyList()
            else -> return emptyList()
        }
        items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.contentOrNull
                ?: item["name"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val rawId = item["id"]?.jsonPrimitive?.contentOrNull
                ?: item["url"]?.jsonPrimitive?.contentOrNull
                ?: item["link"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val id = Regex("""(\d+)""").findAll(rawId).lastOrNull()?.value
                ?: return@mapNotNull null
            SearchHit(id, title)
        }
    }.getOrElse { emptyList() }

    private suspend fun fetchAniboomEmbed(animeId: String, episode: Int): String? = runCatching {
        val body = client.get("$baseUrl/anime/$animeId/player") {
            parameter("episode", episode)
            header("Referer", "$baseUrl/anime/$animeId")
            header("X-Requested-With", "XMLHttpRequest")
        }.bodyAsText()
        val iframe = Jsoup.parse(body, baseUrl)
            .selectFirst("iframe[src*=aniboom], iframe[data-src*=aniboom]")
        val source = iframe?.absUrl("src")?.ifBlank { null }
            ?: iframe?.attr("src")?.ifBlank { null }
            ?: iframe?.attr("data-src")?.ifBlank { null }
            ?: Regex("""https?://[^"'\s]*aniboom[^"'\s]*""").find(body)?.value
            ?: Regex("""//[^"'\s]*aniboom[^"'\s]*""").find(body)?.value
                ?.let { "https:$it" }
        source?.let { if (it.startsWith("//")) "https:$it" else it }
    }.onFailure { Log.w(TAG, "embed ep=$episode failed: ${it.message}") }
        .getOrNull()

    private suspend fun scrapeAniboom(embedUrl: String): String? = runCatching {
        val html = getText(
            embedUrl,
            extraHeaders = mapOf(
                "Referer" to "$baseUrl/",
                "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
            ),
        )
        val video = Jsoup.parse(html, embedUrl).selectFirst("video#video")
            ?: return@runCatching null
        val raw = video.attr("data-parameters").ifBlank { return@runCatching null }
        val data = json.parseToJsonElement(
            org.jsoup.parser.Parser.unescapeEntities(raw, false)
        ).jsonObject

        fun source(key: String): String? {
            val value = data[key]?.jsonPrimitive?.contentOrNull ?: return null
            return runCatching {
                json.parseToJsonElement(value).jsonObject["src"]
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull()
        }

        source("hls")
            ?: source("dash")?.replace(".mpd", ".m3u8")
    }.onFailure { Log.w(TAG, "AniBoom scrape failed: ${it.message}") }
        .getOrNull()

    private data class SearchHit(val id: String, val title: String)

    companion object {
        private const val TAG = "AnimeGoSource"
    }
}
