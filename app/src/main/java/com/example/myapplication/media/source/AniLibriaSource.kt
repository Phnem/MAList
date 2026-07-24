package com.example.myapplication.media.source

import android.util.Log
import com.example.myapplication.data.models.Anime
import com.example.myapplication.localplayer.domain.SkipKind
import com.example.myapplication.sync.TitleMatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Native AniLiberty source.
 *
 * The old api.anilibria.tv/v3 endpoint currently fails TLS validation on a number of Android
 * devices. The current public API is anilibria.top/api/v1 and exposes direct hls_480/720/1080
 * links, so no WebView, yt-dlp or Python process is needed.
 */
class AniLibriaSource(
    client: HttpClient,
) : VetroHttpSource(client) {

    override val name: String = "AniLiberty"
    override val baseUrl: String = API_ORIGIN

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolveEpisode(
        anime: Anime,
        episodeNumber: Int,
        knownReleaseUrl: String? = null,
    ): List<VetroHoster> {
        if (episodeNumber <= 0) return emptyList()

        val knownAlias = extractAniLibriaAlias(knownReleaseUrl)
        val releaseRef = knownAlias?.let { ReleaseRef(alias = it, title = it) }
            ?: findRelease(anime)
            ?: return emptyList()
        val release = fetchRelease(releaseRef.alias) ?: return emptyList()
        val videos = parseAniLibriaV1Episode(release, episodeNumber)
        if (videos.isEmpty()) {
            Log.i(TAG, "No playable episode $episodeNumber in '${releaseRef.title}'")
            return emptyList()
        }

        Log.i(TAG, "Resolved '${releaseRef.title}' ep=$episodeNumber qualities=${videos.map { it.label }}")
        return listOf(
            VetroHoster(
                name = "AniLiberty",
                url = "$SITE_ORIGIN/anime/releases/release/${releaseRef.alias}",
                videos = videos,
                lazy = false,
            )
        )
    }

    private suspend fun findRelease(anime: Anime): ReleaseRef? {
        val queries = listOfNotNull(anime.title, anime.titleRu, anime.titleEn)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (queries.isEmpty()) return null

        val localTitles = queries
        for (query in queries) {
            val matches = searchTitles(query)
            val best = matches
                .map { obj -> obj to scoreTitle(localTitles, obj) }
                .maxByOrNull { it.second }
                ?: continue
            if (best.second < TitleMatcher.MATCH_THRESHOLD) continue
            val alias = best.first.string("alias")
                ?: best.first["id"]?.jsonPrimitive?.intOrNull?.toString()
                ?: continue
            val title = best.first["name"]?.jsonObject?.string("main")
                ?: best.first["name"]?.jsonObject?.string("english")
                ?: query
            return ReleaseRef(alias, title)
        }
        return null
    }

    private suspend fun searchTitles(query: String): List<JsonObject> = runCatching {
        val body = client.get("$API_ORIGIN/app/search/releases") {
            parameter("query", query)
        }.bodyAsText()
        when (val root = json.parseToJsonElement(body)) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> root["data"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
            else -> emptyList()
        }
    }.onFailure { Log.w(TAG, "v1 search failed: ${it.message}") }
        .getOrElse { emptyList() }

    private suspend fun fetchRelease(alias: String): JsonObject? = runCatching {
        val body = client.get("$API_ORIGIN/anime/releases/$alias").bodyAsText()
        json.parseToJsonElement(body) as? JsonObject
    }.onFailure { Log.w(TAG, "v1 release '$alias' failed: ${it.message}") }
        .getOrNull()

    private fun scoreTitle(localTitles: List<String>, obj: JsonObject): Double {
        val names = obj["name"]?.jsonObject
        val remotes = listOfNotNull(
            names?.string("main"),
            names?.string("english"),
            names?.string("alternative"),
            obj.string("alias")?.replace('-', ' '),
        )
        return localTitles.maxOfOrNull { TitleMatcher.bestScore(it, remotes) } ?: 0.0
    }

    private data class ReleaseRef(val alias: String, val title: String)

    companion object {
        private const val TAG = "AniLibriaSource"
        private const val SITE_ORIGIN = "https://anilibria.top"
        private const val API_ORIGIN = "$SITE_ORIGIN/api/v1"
    }
}

internal fun extractAniLibriaAlias(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return Regex(
        """(?i)https?://(?:www\.)?anilibria\.(?:top|tv)/(?:anime/releases/release|release)/([^/?#]+)"""
    ).find(url)?.groupValues?.getOrNull(1)
        ?.removeSuffix(".html")
        ?.takeIf { it.isNotBlank() }
}

internal fun parseAniLibriaV1Episode(
    release: JsonObject,
    episodeNumber: Int,
): List<VetroVideo> {
    val episode = release["episodes"]?.jsonArray
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull {
            it["ordinal"]?.jsonPrimitive?.intOrNull == episodeNumber ||
                it["ordinal"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() == episodeNumber
        }
        ?: return emptyList()

    val timestamps = buildList {
        episode.skipTimestamp("opening", SkipKind.OPENING)?.let(::add)
        episode.skipTimestamp("ending", SkipKind.ENDING)?.let(::add)
    }
    val headers = mapOf(
        "Referer" to "https://anilibria.top/",
        "Origin" to "https://anilibria.top",
        "User-Agent" to VetroHttpSource.DEFAULT_UA,
    )

    return buildList {
        fun addVariant(key: String, resolution: Int) {
            val url = episode.string(key)?.takeIf { it.startsWith("http") } ?: return
            add(
                VetroVideo(
                    url = url,
                    label = "${resolution}p",
                    resolution = resolution,
                    headers = headers,
                    timestamps = timestamps,
                    isPreferred = resolution == 1080,
                )
            )
        }
        addVariant("hls_1080", 1080)
        addVariant("hls_720", 720)
        addVariant("hls_480", 480)
    }.distinctBy { it.url }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

private fun JsonObject.skipTimestamp(
    key: String,
    kind: SkipKind,
): VetroTimestamp? {
    val range = this[key] as? JsonObject ?: return null
    val start = range["start"]?.jsonPrimitive?.intOrNull ?: return null
    val stop = range["stop"]?.jsonPrimitive?.intOrNull ?: return null
    if (start < 0 || stop <= start) return null
    return VetroTimestamp(start * 1_000L, stop * 1_000L, kind)
}
