package com.example.myapplication.media.source

import android.util.Log
import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.sync.TitleMatcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Native Kotlin source for jut.su (RU).
 *
 * Search: GET /lookfor/{q} → 302 to title page.
 * Episode pages expose `<source type="video/mp4" label="720p" res="720" src="...">`
 * (direct progressive MP4 with Referer).
 */
class JutSuSource(
    client: HttpClient,
) : VetroHttpSource(client) {

    override val name: String = "jut.su"
    override val baseUrl: String = "https://jut.su"

    suspend fun resolveEpisode(
        anime: Anime,
        episodeNumber: Int,
        seasonInfo: SeasonInfo? = null,
        knownTitleUrl: String? = null,
    ): List<VetroHoster> {
        val titleUrl = knownTitleUrl?.takeIf { it.contains("jut.su", ignoreCase = true) }
            ?: run {
                val query = anime.titleRu?.takeIf { it.isNotBlank() }
                    ?: anime.title.takeIf { it.isNotBlank() }
                    ?: anime.titleEn
                    ?: return emptyList()
                resolveTitleUrl(query, anime)
            }
            ?: return emptyList()

        val slug = titleUrl.trimEnd('/').substringAfterLast('/')
        if (slug.isBlank() || slug in NON_TITLE_SLUGS) return emptyList()

        val season = seasonInfo?.seasonNumber?.takeIf { it > 0 } ?: 1
        val candidates = episodeUrlCandidates(slug, season, episodeNumber)

        for (pageUrl in candidates) {
            val videos = scrapeEpisodeSources(pageUrl)
            if (videos.isNotEmpty()) {
                Log.i(TAG, "OK $pageUrl → ${videos.size} qualities")
                return listOf(
                    VetroHoster(
                        name = "jut.su",
                        url = pageUrl,
                        videos = videos,
                        lazy = false,
                    )
                )
            }
        }
        Log.w(TAG, "No sources for $slug ep=$episodeNumber season=$season")
        return emptyList()
    }

    private suspend fun resolveTitleUrl(query: String, anime: Anime): String? = runCatching {
        val enc = URLEncoder.encode(query, "UTF-8")
        val resp = client.get("$baseUrl/lookfor/$enc") {
            header(HttpHeaders.UserAgent, DEFAULT_UA)
            header(HttpHeaders.AcceptLanguage, "ru,en;q=0.9")
        }
        resp.bodyAsText()
        val finalUrl = resp.call.request.url.toString()
        val slug = TITLE_URL.find(finalUrl)?.groupValues?.get(1)
        if (slug == null || slug in NON_TITLE_SLUGS) {
            Log.i(TAG, "lookfor miss '$query' → $finalUrl")
            return@runCatching null
        }
        val page = getText("$baseUrl/$slug/", extraHeaders = mapOf("Referer" to "$baseUrl/"))
        val docTitle = Jsoup.parse(page).selectFirst("h1, .anime_title, title")?.text().orEmpty()
        val local = listOfNotNull(anime.title, anime.titleRu, anime.titleEn)
        val remotes = listOfNotNull(docTitle, slug.replace('-', ' '))
        val score = local.maxOfOrNull { TitleMatcher.bestScore(it, remotes) } ?: 0.0
        if (score < TITLE_MATCH_THRESHOLD && local.isNotEmpty()) {
            Log.i(TAG, "weak title score=$score for '$docTitle' (query='$query'), keeping slug=$slug")
        }
        "$baseUrl/$slug/"
    }.onFailure { Log.w(TAG, "resolveTitleUrl: ${it.message}") }.getOrNull()

    private fun episodeUrlCandidates(slug: String, season: Int, episode: Int): List<String> {
        val ep = episode.coerceAtLeast(1)
        return buildList {
            if (season > 1) {
                add("$baseUrl/$slug/season-$season/episode-$ep.html")
            }
            add("$baseUrl/$slug/episode-$ep.html")
            add("$baseUrl/$slug/season-1/episode-$ep.html")
            if (season != 2) add("$baseUrl/$slug/season-2/episode-$ep.html")
        }.distinct()
    }

    private suspend fun scrapeEpisodeSources(pageUrl: String): List<VetroVideo> = runCatching {
        val html = getText(
            pageUrl,
            extraHeaders = mapOf(
                "Referer" to "$baseUrl/",
                HttpHeaders.AcceptLanguage to "ru,en;q=0.9",
            ),
        )
        val doc = Jsoup.parse(html, pageUrl)
        val sources = doc.select("source[type=video/mp4], source[src]")
        if (sources.isEmpty()) return@runCatching emptyList()
        val firstMediaUrl = sources.firstNotNullOfOrNull { element ->
            element.attr("src").takeIf { it.startsWith("http") }
        }
        val cookieHeader = firstMediaUrl?.let { mediaUrl ->
            client.cookies(Url(mediaUrl)).joinToString("; ") { cookie ->
                "${cookie.name}=${cookie.value}"
            }
        }.orEmpty()

        val videos = sources.mapNotNull { el ->
            val src = el.attr("src").ifBlank { return@mapNotNull null }
            if (!src.startsWith("http")) return@mapNotNull null
            val label = el.attr("label").ifBlank {
                el.attr("res").takeIf { it.isNotBlank() }?.let { "${it}p" } ?: "mp4"
            }
            val res = el.attr("res").toIntOrNull()
                ?: Regex("""(\d{3,4})""").find(label)?.groupValues?.get(1)?.toIntOrNull()
            VetroVideo(
                url = src,
                label = label,
                resolution = res,
                headers = buildMap {
                    put("Referer", pageUrl)
                    put("User-Agent", DEFAULT_UA)
                    put("Accept", "video/webm,video/mp4,video/*;q=0.9,*/*;q=0.8")
                    put("Sec-Fetch-Dest", "video")
                    put("Sec-Fetch-Mode", "no-cors")
                    put("Sec-Fetch-Site", "same-site")
                    if (cookieHeader.isNotBlank()) put("Cookie", cookieHeader)
                },
                isPreferred = res == 720 || label.contains("720"),
            )
        }.distinctBy { it.url }
            .sortedByDescending { it.resolution ?: 0 }

        if (videos.isNotEmpty() && videos.none { it.isPreferred }) {
            listOf(videos.first().copy(isPreferred = true)) + videos.drop(1)
        } else {
            videos
        }
    }.onFailure { Log.w(TAG, "scrape $pageUrl: ${it.message}") }.getOrElse { emptyList() }

    override fun headersBuilder(): Map<String, String> = mapOf(
        HttpHeaders.UserAgent to DEFAULT_UA,
        HttpHeaders.AcceptLanguage to "ru,en;q=0.9",
    )

    companion object {
        private const val TAG = "JutSuSource"
        private val TITLE_URL = Regex("""^https?://jut\.su/([a-z0-9\-]+)/?$""", RegexOption.IGNORE_CASE)
        private val NON_TITLE_SLUGS = setOf(
            "anime", "search", "lookfor", "login", "register", "top", "new", "ongoing",
            "films", "manga", "forum", "user", "pm", "favicon.ico",
        )
    }
}
