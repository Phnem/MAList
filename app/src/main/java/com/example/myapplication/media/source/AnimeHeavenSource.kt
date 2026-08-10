package com.example.myapplication.media.source

import android.util.Log
import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.DiscoveredSeason
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.sync.TitleMatcher
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Native Kotlin source for animeheaven.me (EN).
 *
 * Replaces the Consumet/Gogoanime source: `api.consumet.org` was shut down and now answers every
 * request with an HTML redirect to GitHub (451), so the EN chain could never produce a video.
 *
 * Flow (three plain GETs, no anti-bot bypass):
 *  1. `search.php?s={query}` → `anime.php?{id}` cards with their titles.
 *  2. `anime.php?{id}` → one `<a href='gate.php' id='{key}'>` per episode, number in `.watch2`.
 *  3. `gate.php` with `Cookie: key={key}` → `<source src="https://c*.animeheaven.me/video.mp4?…">`,
 *     direct progressive MP4 across a few CDN mirrors (Range supported → seeking + download work).
 *
 * Seasons are separate site entries ("… Season 2", "… 2nd Season", "Overlord II", "… 3"), so the
 * franchise season number is matched explicitly — TitleMatcher scores a season suffix as the same
 * title (subset rule) and on its own would happily return season 1 for every season.
 */
class AnimeHeavenSource(
    client: HttpClient,
) : VetroHttpSource(client) {

    override val name: String = "AnimeHeaven"
    override val baseUrl: String = "https://animeheaven.me"

    private data class Entry(val id: String, val title: String)

    private data class Candidate(
        val entry: Entry,
        val score: Double,
        val rawScore: Double,
        val season: Int,
    )

    private class Cached<T>(val value: T, val at: Long = System.currentTimeMillis())

    /**
     * Downloading a season resolves every episode separately, and each resolve would otherwise
     * repeat the same search + title page. Short-lived so a newly aired episode still shows up.
     */
    private val entryCache = java.util.concurrent.ConcurrentHashMap<String, Cached<Entry>>()
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<String, Cached<Map<Int, String>>>()

    suspend fun resolveEpisode(
        anime: Anime,
        episodeNumber: Int,
        seasonInfo: SeasonInfo? = null,
    ): List<VetroHoster> {
        if (episodeNumber <= 0) return emptyList()
        val targetSeason = seasonInfo?.seasonNumber?.takeIf { it > 0 } ?: 1

        // The site is EN-only: Cyrillic titles return an empty result page, so don't spend a request.
        val titles = listOfNotNull(
            seasonInfo?.title?.takeIf { it.isNotBlank() },
            anime.titleEn?.takeIf { it.isNotBlank() },
            anime.title.takeIf { it.isNotBlank() },
        ).filter { it.hasLatin() }.distinctBy { it.lowercase() }
        if (titles.isEmpty()) {
            Log.i(TAG, "no latin title for \"${anime.title}\"")
            return emptyList()
        }

        val cacheKey = "${titles.joinToString("|") { it.lowercase() }}#$targetSeason"
        val entry = entryCache.fresh(cacheKey)
            ?: findEntry(titles, targetSeason)?.also { entryCache[cacheKey] = Cached(it) }
            ?: return emptyList()

        val episodes = episodeCache.fresh(entry.id)
            ?: episodeKeys(entry.id).also { if (it.isNotEmpty()) episodeCache[entry.id] = Cached(it) }
        val episodeKey = episodes[episodeNumber]
        if (episodeKey == null) {
            Log.w(TAG, "\"${entry.title}\" has no episode $episodeNumber")
            return emptyList()
        }

        val videos = scrapeGate(entry.id, episodeKey)
        if (videos.isEmpty()) {
            Log.w(TAG, "no sources on gate.php for \"${entry.title}\" ep $episodeNumber")
            return emptyList()
        }
        Log.i(TAG, "OK \"${entry.title}\" ep $episodeNumber → ${videos.size} mirrors")
        return listOf(
            VetroHoster(
                name = "AnimeHeaven",
                url = "$baseUrl/anime.php?${entry.id}",
                videos = videos,
                lazy = false,
            )
        )
    }

    /**
     * Seasons the site actually carries — for "Find more"
     * (see [com.example.myapplication.domain.seasons.StreamingSeasonDiscovery]).
     *
     * Seasons here are separate site entries, so there is no season list to parse: we probe season
     * numbers upwards and stop at the first gap. [MAX_PROBED_SEASONS] caps a franchise that keeps
     * matching (long-running shows with many entries) — beyond it the request count stops being
     * worth one button press.
     */
    suspend fun findSeasons(anime: Anime): List<DiscoveredSeason> {
        val titles = listOfNotNull(
            anime.titleEn?.takeIf { it.isNotBlank() },
            anime.title.takeIf { it.isNotBlank() },
        ).filter { it.hasLatin() }.distinctBy { it.lowercase() }
        if (titles.isEmpty()) return emptyList()

        val seasons = ArrayList<DiscoveredSeason>()
        for (season in 1..MAX_PROBED_SEASONS) {
            val entry = findEntry(titles, season) ?: break
            val episodes = episodeKeys(entry.id).keys.maxOrNull() ?: break
            if (episodes <= 0) break
            seasons += DiscoveredSeason(season, episodes, SOURCE_NAME)
        }
        Log.i(TAG, "AnimeHeaven seasons for \"${titles.first()}\": ${seasons.size}")
        return seasons
    }

    // ==========================================================
    // Search → title entry of the requested season
    // ==========================================================

    private suspend fun findEntry(titles: List<String>, targetSeason: Int): Entry? {
        // Searching the season-less title returns the whole franchise in one page, which is what the
        // season picking below needs; the raw title is a fallback for entries named only by season.
        val queries = titles.flatMap { listOf(baseTitle(it), it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(MAX_QUERIES)

        val found = LinkedHashMap<String, Entry>()
        for (query in queries) {
            search(query).forEach { found.putIfAbsent(it.id, it) }
            if (found.isNotEmpty()) break
        }
        if (found.isEmpty()) {
            Log.i(TAG, "search miss for ${queries.firstOrNull()}")
            return null
        }

        val scored = found.values.map { entry ->
            Candidate(
                entry = entry,
                score = titles.maxOfOrNull {
                    TitleMatcher.bestScore(baseTitle(it), listOf(baseTitle(entry.title)))
                } ?: 0.0,
                // Raw score keeps the season marker, so it breaks ties between "X" and "X 0".
                rawScore = titles.maxOfOrNull { TitleMatcher.bestScore(it, listOf(entry.title)) } ?: 0.0,
                season = seasonOf(entry.title),
            )
        }.filter { it.score >= TITLE_MATCH_THRESHOLD }

        if (scored.isEmpty()) {
            Log.i(TAG, "no candidate above threshold for \"${titles.first()}\"")
            return null
        }

        val best = compareBy<Candidate>({ it.score }, { it.rawScore })
        val picked = scored.filter { it.season == targetSeason }.maxWithOrNull(best)
            // Only season 1 may fall back to "whatever matched": for later seasons an unmarked entry
            // is the first season, and silently playing its episodes is worse than showing nothing.
            ?: scored.takeIf { targetSeason == 1 }?.maxWithOrNull(best)
        if (picked == null) {
            Log.w(
                TAG,
                "no entry for season $targetSeason of \"${titles.first()}\" " +
                    "(have ${scored.map { "${it.entry.title}=S${it.season}" }})",
            )
            return null
        }
        Log.i(TAG, "matched \"${picked.entry.title}\" score=${picked.score} season=${picked.season}")
        return picked.entry
    }

    private suspend fun search(query: String): List<Entry> = runCatching {
        val html = getText(
            "$baseUrl/search.php?s=${URLEncoder.encode(query, "UTF-8")}",
            extraHeaders = mapOf("Referer" to "$baseUrl/"),
        )
        Jsoup.parse(html, baseUrl).select("a[href^=anime.php]")
            .mapNotNull { element ->
                val id = element.attr("href").substringAfter('?').trim()
                val title = element.text().trim()
                if (id.isBlank() || title.isBlank()) null else Entry(id, title)
            }
            .distinctBy { it.id }
            .take(MAX_CANDIDATES)
    }.onFailure { Log.w(TAG, "search '$query': ${it.message}") }.getOrElse { emptyList() }

    // ==========================================================
    // Title page → per-episode gate key
    // ==========================================================

    /** Episode number → gate key, for every episode the site actually has (ongoing = aired only). */
    private suspend fun episodeKeys(animeId: String): Map<Int, String> = runCatching {
        val html = getText(
            "$baseUrl/anime.php?$animeId",
            extraHeaders = mapOf("Referer" to "$baseUrl/search.php"),
        )
        Jsoup.parse(html, baseUrl).select("a[href=gate.php][id]")
            .mapNotNull { element ->
                val number = element.selectFirst("div.watch2")?.text()?.trim()?.toIntOrNull()
                val key = element.id().takeIf { it.isNotBlank() }
                if (number == null || key == null) null else number to key
            }
            .toMap()
    }.onFailure { Log.w(TAG, "anime.php?$animeId: ${it.message}") }.getOrElse { emptyMap() }

    private fun <T> java.util.concurrent.ConcurrentHashMap<String, Cached<T>>.fresh(key: String): T? {
        val hit = this[key] ?: return null
        if (System.currentTimeMillis() - hit.at > CACHE_TTL_MS) {
            remove(key)
            return null
        }
        return hit.value
    }

    // ==========================================================
    // Gate → direct MP4 mirrors
    // ==========================================================

    private suspend fun scrapeGate(animeId: String, episodeKey: String): List<VetroVideo> = runCatching {
        val pageUrl = "$baseUrl/anime.php?$animeId"
        val html = getText(
            "$baseUrl/gate.php",
            // gate.php serves whichever episode the `key` cookie names — the site sets it in JS
            // right before navigating. Sent per request so parallel episode resolves can't race.
            extraHeaders = mapOf(
                "Referer" to pageUrl,
                HttpHeaders.Cookie to "key=$episodeKey",
            ),
        )
        Jsoup.parse(html, baseUrl).select("source[src]")
            .mapNotNull { it.attr("src").trim().takeIf { src -> src.startsWith("http") } }
            .distinct()
            .mapIndexed { index, url ->
                VetroVideo(
                    url = url,
                    // The site publishes a single encode per episode; the extra <source> entries are
                    // CDN mirrors used as onerror fallbacks, so there is no quality to label.
                    label = if (index == 0) "auto" else "auto (mirror ${index + 1})",
                    resolution = null,
                    headers = mapOf(
                        "Referer" to "$baseUrl/",
                        "User-Agent" to DEFAULT_UA,
                        "Accept" to "video/webm,video/mp4,video/*;q=0.9,*/*;q=0.8",
                    ),
                    isPreferred = index == 0,
                )
            }
    }.onFailure { Log.w(TAG, "gate.php: ${it.message}") }.getOrElse { emptyList() }

    // ==========================================================
    // Season markers
    // ==========================================================

    /** Season number a site title advertises; 1 when it carries no marker. */
    internal fun seasonOf(title: String): Int {
        val text = title.trim()
        SEASON_WORD.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        ORDINAL_SEASON.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        TRAILING_ROMAN.find(text)?.groupValues?.get(1)?.let { roman ->
            ROMAN[roman.uppercase()]?.let { return it }
        }
        TRAILING_NUMBER.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 1
    }

    /** Title without its season marker — what TitleMatcher should compare. */
    internal fun baseTitle(title: String): String =
        title.replace(SEASON_WORD, " ")
            .replace(ORDINAL_SEASON, " ")
            .replace(TRAILING_ROMAN, " ")
            .replace(TRAILING_NUMBER, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { title.trim() }

    private fun String.hasLatin(): Boolean = any { it in 'a'..'z' || it in 'A'..'Z' }

    override fun headersBuilder(): Map<String, String> = mapOf(
        HttpHeaders.UserAgent to DEFAULT_UA,
        HttpHeaders.AcceptLanguage to "en-US,en;q=0.9",
    )

    companion object {
        private const val TAG = "AnimeHeavenSource"
        private const val MAX_QUERIES = 3
        private const val MAX_CANDIDATES = 24
        private const val CACHE_TTL_MS = 10 * 60 * 1000L

        /** Matches `StreamingSeasonDiscovery.STREAMING_SOURCES`. */
        private const val SOURCE_NAME = "AnimeHeaven"
        /** Season probing is one search + one title page each — cap it. */
        private const val MAX_PROBED_SEASONS = 8

        /** "… Season 2", "… The Final Season" (unnumbered → not a marker). */
        private val SEASON_WORD = Regex("""\bseason\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE)
        /** "… 2nd Season", "… 3rd Season". */
        private val ORDINAL_SEASON = Regex("""\b(\d{1,2})(?:st|nd|rd|th)\s+season\b""", RegexOption.IGNORE_CASE)
        /** "Overlord II" — only at the very end, so "Fate/Zero II Part" style noise is ignored. */
        private val TRAILING_ROMAN = Regex("""\s(I{1,3}|IV|V|VI{1,3}|IX|X)$""")
        /** "KonoSuba …! 3" — a bare trailing number. */
        private val TRAILING_NUMBER = Regex("""\s(\d{1,2})$""")

        private val ROMAN = mapOf(
            "I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5,
            "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10,
        )
    }
}
