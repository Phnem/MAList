package com.example.myapplication.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ktor 3.x–based API facade. Uses Shikimori + AniList remote data sources,
 * returns Result everywhere.
 */
class VetroApiService(
    private val httpClient: HttpClient,
    private val shikimori: ShikimoriRemoteDataSource,
    private val aniList: AniListRemoteDataSource,
    private val heavyRate: TokenBucketRateLimiter,
    private val searchRate: TokenBucketRateLimiter,
    private val burstRate: TokenBucketRateLimiter
) : ApiService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchDetails(title: String, language: AppLanguage): Result<AnimeDetails?> {
        return runCatching {
            heavyRate.acquire()
            val query = title.trim()
            when (language) {
                AppLanguage.EN -> aniList.fetchAnimeDetails(query).getOrNull()
                    ?: shikimori.fetchAnimeDetails(query).getOrNull()
                AppLanguage.RU -> shikimori.fetchAnimeDetails(query).getOrNull()
                    ?: aniList.fetchAnimeDetails(query).getOrNull()
            }
        }
    }

    override suspend fun findTotalEpisodes(
        title: String,
        categoryType: String,
        appContentType: AppContentType
    ): Result<Pair<Int, String>?> {
        return runCatching {
            heavyRate.acquire()
            when (appContentType) {
                AppContentType.ANIME -> {
                    aniList.findTotalEpisodes(title.trim()).getOrNull()
                        ?: shikimori.findTotalEpisodes(title.trim()).getOrNull()
                        ?: checkJikan(title.trim())
                }
                AppContentType.MOVIE, AppContentType.SERIES -> checkTmdb(title.trim())
            }
        }
    }

    override suspend fun searchApi(query: String, contentType: AppContentType, language: AppLanguage): Result<List<ApiSearchResult>> {
        return runCatching {
            searchRate.acquire()
            val q = query.trim()
            if (q.isEmpty()) return@runCatching emptyList<ApiSearchResult>()
            when (contentType) {
                AppContentType.ANIME -> searchAnimeApis(q, language)
                AppContentType.MOVIE -> filterAndRankByQuery(q, searchTmdbMovie(q))
                AppContentType.SERIES -> filterAndRankByQuery(q, searchTmdbTv(q))
            }
        }
    }

    override suspend fun searchAnimeShikimoriOnly(query: String, language: AppLanguage): Result<List<ApiSearchResult>> {
        return runCatching {
            searchRate.acquire()
            val q = query.trim()
            if (q.isEmpty()) return@runCatching emptyList()
            val raw = shikimori.searchAnime(q, 20, language).getOrThrow()
            // Inspect: выкидываем результаты Shikimori с 0 серий (episodes == 0)
            val rawNonZeroEpisodes = raw.filter { it.episodes > 0 }
            if (rawNonZeroEpisodes.isEmpty()) return@runCatching emptyList()

            // Для Inspect RU-аниме нам критично "не получить пусто" из-за слишком строгого fuzzy:
            // Gemini часто даёт близкое, но не 1:1 совпадение. Поэтому делаем мягкую фильтрацию.
            val normQuery = normalizeForSearch(q)
            if (normQuery.isEmpty()) return@runCatching rawNonZeroEpisodes

            fun searchableKey(r: ApiSearchResult): String {
                val both = (r.title + " " + (r.altTitle ?: "")).trim()
                return normalizeForSearch(both)
            }

            val exactOrPartial = rawNonZeroEpisodes.filter { r ->
                val key = searchableKey(r)
                key.isNotEmpty() && (key.contains(normQuery) || normQuery.contains(key))
            }

            val ranked = (exactOrPartial.ifEmpty { rawNonZeroEpisodes })
                .sortedWith(compareBy<ApiSearchResult> { r ->
                    val key = searchableKey(r)
                    when {
                        key == normQuery -> 0
                        key.contains(normQuery) || normQuery.contains(key) -> 1
                        else -> 2
                    }
                }.thenByDescending { it.rating ?: Int.MIN_VALUE })
                .distinctBy { it.source + "_" + (it.externalId ?: it.title) }

            ranked.take(20)
        }
    }

    override suspend fun mediaByAnilistId(id: Int): Result<ApiSearchResult?> {
        return runCatching {
            searchRate.acquire()
            aniList.mediaByAnilistId(id).getOrThrow()
        }
    }

    private fun tmdbKey(): String = BuildConfig.TMDB_API_KEY

    private enum class SearchMatch { EXACT, PARTIAL, FUZZY, NONE }

    private fun normalizeForSearch(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun searchMatchType(result: ApiSearchResult, query: String): SearchMatch {
        val q = query.trim()
        if (q.isEmpty()) return SearchMatch.NONE
        val normQuery = normalizeForSearch(q)
        val searchable = (result.title + " " + (result.altTitle ?: "")).trim()
        val normTitle = normalizeForSearch(searchable)
        if (normTitle == normQuery) return SearchMatch.EXACT
        if (normTitle.contains(normQuery) || normQuery.contains(normTitle)) return SearchMatch.PARTIAL
        val queryWords = q.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        if (queryWords.isEmpty()) return SearchMatch.PARTIAL
        val titleWords = searchable.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val fuzzyOk = queryWords.all { qw -> titleWords.any { tw -> levenshtein(qw, tw) <= 2 } }
        return if (fuzzyOk) SearchMatch.FUZZY else SearchMatch.NONE
    }

    /** Фильтрация (exact/partial/fuzzy) + ранжирование: exact > partial > fuzzy. */
    private fun filterAndRankByQuery(query: String, results: List<ApiSearchResult>): List<ApiSearchResult> {
        val seen = mutableSetOf<String>()
        val order = listOf(SearchMatch.EXACT, SearchMatch.PARTIAL, SearchMatch.FUZZY)
        return results
            .mapNotNull { r ->
                val match = searchMatchType(r, query)
                if (match == SearchMatch.NONE) return@mapNotNull null
                val key = normalizeForSearch(r.title)
                if (key.isEmpty() || !seen.add(key)) return@mapNotNull null
                r to match
            }
            .sortedBy { (_, m) -> order.indexOf(m) }
            .map { it.first }
    }

    private suspend fun searchAnimeApis(query: String, language: AppLanguage): List<ApiSearchResult> {
        val raw = mutableListOf<ApiSearchResult>()
        val seenKeys = mutableSetOf<String>()
        fun addIfNew(r: ApiSearchResult) {
            val key = normalizeForSearch(r.title)
            if (key.isEmpty() || !seenKeys.add(key)) return
            raw.add(r)
        }
        shikimori.searchAnime(query, 20, language).getOrNull()?.forEach { addIfNew(it) }
        burstRate.acquire()
        aniList.searchAnime(query, 20, language).getOrNull()?.forEach { addIfNew(it) }
        burstRate.acquire()
        searchJikan(query, language).forEach { addIfNew(it) }
        return filterAndRankByQuery(query, raw)
    }

    private suspend fun searchJikan(query: String, language: AppLanguage): List<ApiSearchResult> = runCatching {
        val response = httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.jikan.moe"
                appendPathSegments("v4", "anime")
                parameters.append("q", query)
                parameters.append("limit", "20")
            }
        }.bodyAsText()
        val root = json.parseToJsonElement(response).jsonObject
        val data = root["data"]?.jsonArray ?: return@runCatching emptyList()
        data.mapNotNull { el ->
            val obj = el.jsonObject
            val titleJp = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val titleEng = obj["title_english"]?.jsonPrimitive?.content ?: ""
            val (displayTitle, altTitle) = when {
                titleEng.isNotBlank() -> titleEng to titleJp.takeIf { it != titleEng }
                else -> titleJp to null
            }
            val desc = obj["synopsis"]?.jsonPrimitive?.content?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
            val episodes = obj["episodes"]?.jsonPrimitive?.intOrNull ?: 0
            val score = obj["score"]?.jsonPrimitive?.content?.toFloatOrNull()?.toInt()
            val jpg = obj["images"]?.jsonObject?.get("jpg")?.jsonObject
            val image = jpg?.get("large_image_url")?.jsonPrimitive?.content
                ?: jpg?.get("image_url")?.jsonPrimitive?.content
            val genres = obj["genres"]?.jsonArray?.mapNotNull { g ->
                g.jsonObject["name"]?.jsonPrimitive?.content
            } ?: emptyList()
            val type = obj["type"]?.jsonPrimitive?.content ?: ""
            ApiSearchResult(
                title = displayTitle,
                altTitle = altTitle,
                posterUrl = image,
                episodes = episodes,
                description = desc,
                type = type,
                genres = genres,
                rating = score,
                source = "Jikan",
                categoryType = "ANIME",
                externalId = obj["mal_id"]?.jsonPrimitive?.intOrNull?.toString()
                    ?: obj["mal_id"]?.jsonPrimitive?.content
            )
        }
    }.getOrElse { emptyList() }

    private suspend fun searchTmdbMovie(query: String): List<ApiSearchResult> = runCatching {
        val key = tmdbKey()
        val response = httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.themoviedb.org"
                appendPathSegments("3", "search", "movie")
                parameters.append("api_key", key)
                parameters.append("query", query)
            }
        }.bodyAsText()
        parseTmdbResults(response, "MOVIE")
    }.getOrElse { emptyList() }

    private suspend fun searchTmdbTv(query: String): List<ApiSearchResult> = runCatching {
        val key = tmdbKey()
        val response = httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.themoviedb.org"
                appendPathSegments("3", "search", "tv")
                parameters.append("api_key", key)
                parameters.append("query", query)
            }
        }.bodyAsText()
        parseTmdbResults(response, "SERIES")
    }.getOrElse { emptyList() }

    private suspend fun parseTmdbResults(jsonStr: String, categoryType: String): List<ApiSearchResult> {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val obj = el.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val overview = obj["overview"]?.jsonPrimitive?.content ?: ""
            val posterPath = obj["poster_path"]?.jsonPrimitive?.content
            val posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            val id = obj["id"]?.jsonPrimitive?.int ?: return@mapNotNull null
            val voteAvg = obj["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()?.times(10)?.toInt()
            val episodeCount = if (categoryType == "SERIES") {
                obj["number_of_episodes"]?.jsonPrimitive?.intOrNull ?: 0
            } else 1
            ApiSearchResult(
                title = title,
                altTitle = null,
                posterUrl = posterUrl,
                episodes = episodeCount,
                description = overview,
                type = if (categoryType == "SERIES") "TV" else "Movie",
                genres = emptyList(),
                rating = voteAvg,
                source = "TMDB",
                categoryType = categoryType,
                externalId = id.toString()
            )
        }
    }

    override suspend fun checkGithubUpdate(owner: String, repo: String): Result<GithubReleaseInfo?> {
        return runCatching {
            val response = httpClient.get("https://api.github.com/repos/$owner/$repo/releases").bodyAsText()
            val arr = json.parseToJsonElement(response).jsonArray
            val root = arr.firstOrNull()?.jsonObject ?: return@runCatching null
            val tagName = root["tag_name"]?.jsonPrimitive?.content ?: ""
            val htmlUrl = root["html_url"]?.jsonPrimitive?.content ?: ""
            val body = root["body"]?.jsonPrimitive?.content
            val assets = root["assets"]?.jsonArray
            val downloadUrl = assets?.firstOrNull()?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content ?: ""
            if (tagName.isNotEmpty()) GithubReleaseInfo(tagName, htmlUrl, downloadUrl, body) else null
        }
    }

    private suspend fun checkJikan(query: String): Pair<Int, String>? {
        return runCatching {
            val response = httpClient.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.jikan.moe"
                    appendPathSegments("v4", "anime")
                    parameters.append("q", query)
                    parameters.append("limit", "1")
                }
            }.bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonArray ?: return@runCatching null
            val first = data.firstOrNull()?.jsonObject ?: return@runCatching null
            val title = first["title"]?.jsonPrimitive?.content ?: return@runCatching null
            val titleEng = first["title_english"]?.jsonPrimitive?.content ?: ""
            if (!isTitleSimilar(query, title, titleEng)) return@runCatching null
            val episodes = first["episodes"]?.jsonPrimitive?.intOrNull ?: 0
            if (episodes > 0) episodes to "Jikan" else null
        }.getOrNull()
    }

    private suspend fun checkTmdb(query: String): Pair<Int, String>? {
        return runCatching {
            val key = tmdbKey()
            val searchResponse = httpClient.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.themoviedb.org"
                    appendPathSegments("3", "search", "tv")
                    parameters.append("api_key", key)
                    parameters.append("query", query)
                }
            }.bodyAsText()
            val root = json.parseToJsonElement(searchResponse).jsonObject
            val results = root["results"]?.jsonArray ?: return@runCatching null
            val show = results.firstOrNull()?.jsonObject ?: return@runCatching null
            val id = show["id"]?.jsonPrimitive?.int ?: return@runCatching null
            val detailsResponse = httpClient.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.themoviedb.org"
                    appendPathSegments("3", "tv", id.toString())
                    parameters.append("api_key", key)
                }
            }.bodyAsText()
            val details = json.parseToJsonElement(detailsResponse).jsonObject
            val seasons = details["seasons"]?.jsonArray ?: return@runCatching null
            var total = 0
            for (s in seasons) {
                val obj = s.jsonObject
                val sNum = obj["season_number"]?.jsonPrimitive?.int ?: 0
                if (sNum > 0 || seasons.size == 1) {
                    total += obj["episode_count"]?.jsonPrimitive?.int ?: 0
                }
            }
            if (total > 0) total to "TMDB" else null
        }.getOrNull()
    }
}
