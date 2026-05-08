package com.example.myapplication.network

import com.example.myapplication.network.dto.ShikimoriSearchItemDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
/**
 * Type-safe remote data source for Shikimori API.
 */
class ShikimoriRemoteDataSource(
    private val client: HttpClient,
    private val burstRate: TokenBucketRateLimiter
) {

    suspend fun fetchAnimeDetails(query: String): Result<AnimeDetails> = runCatching {
        val searchResponse = client.get("https://shikimori.one/api/animes") {
            parameter("search", query.trim())
            parameter("limit", "1")
        }.body<List<ShikimoriSearchItemDto>>()

        val first = searchResponse.firstOrNull()
            ?: throw NoSuchElementException("Anime not found: $query")

        if (!isTitleSimilar(query, first.name, first.russian)) {
            throw NoSuchElementException("Title mismatch: $query")
        }

        burstRate.acquire()
        val full = client.get("https://shikimori.one/api/animes/${first.id}").body<ShikimoriSearchItemDto>()
        full.toDomain()
    }

    suspend fun searchAnime(query: String, limit: Int = 20, language: AppLanguage = AppLanguage.EN): Result<List<ApiSearchResult>> = runCatching {
        val searchResponse = client.get("https://shikimori.one/api/animes") {
            parameter("search", query.trim())
            parameter("limit", limit)
        }.body<List<ShikimoriSearchItemDto>>()
        searchResponse.map { it.toApiSearchResult(language) }
    }

    suspend fun findTotalEpisodes(query: String): Result<Pair<Int, String>?> = runCatching {
        val searchResponse = client.get("https://shikimori.one/api/animes") {
            parameter("search", query.trim())
            parameter("limit", "1")
        }.body<List<ShikimoriSearchItemDto>>()

        val first = searchResponse.firstOrNull() ?: return@runCatching null
        if (!isTitleSimilar(query, first.name, first.russian)) return@runCatching null
        val episodes = first.episodesAired ?: first.episodes ?: 0
        if (episodes > 0) episodes to "Shikimori" else null
    }

    suspend fun getAnimeById(id: Int, language: AppLanguage = AppLanguage.EN): Result<ApiSearchResult?> = runCatching {
        burstRate.acquire()
        val full = client.get("https://shikimori.one/api/animes/$id").body<ShikimoriSearchItemDto>()
        full.toApiSearchResult(language)
    }

    private fun ShikimoriSearchItemDto.toApiSearchResult(language: AppLanguage): ApiSearchResult {
        val desc = description
            ?.replace(Regex("\\[.*?\\]"), "")
            ?.replace(Regex("<[^>]+>"), "")
            ?.trim() ?: ""
        val posterUrl = image?.original?.let { if (it.startsWith("http")) it else "https://shikimori.one$it" }
        val nameVal = name ?: "Unknown"
        val russianVal = russian?.takeIf { it.isNotBlank() }
        val (title, altTitle) = when (language) {
            AppLanguage.RU -> (russianVal ?: nameVal) to (if (russianVal != null) nameVal else null)
            AppLanguage.EN -> nameVal to russianVal
        }
        return ApiSearchResult(
            title = title,
            altTitle = altTitle,
            posterUrl = posterUrl,
            episodes = episodesAired ?: episodes ?: 0,
            description = desc,
            type = kind ?: "",
            genres = genres?.mapNotNull { it.russian ?: it.name } ?: emptyList(),
            rating = score?.toFloatOrNull()?.toInt(),
            source = "Shikimori",
            categoryType = "ANIME",
            externalId = id.toString()
        )
    }

    private fun ShikimoriSearchItemDto.toDomain(): AnimeDetails {
        val desc = description
            ?.replace(Regex("\\[.*?\\]"), "")
            ?.replace(Regex("<[^>]+>"), "")
            ?.trim() ?: ""
        val posterUrl = image?.original?.let { if (it.startsWith("http")) it else "https://shikimori.one$it" }
        return AnimeDetails(
            title = name ?: "Unknown",
            altTitle = russian?.takeIf { it.isNotBlank() },
            description = desc,
            type = kind ?: "",
            status = status ?: "",
            episodesAired = episodesAired ?: episodes ?: 0,
            episodesTotal = episodes,
            nextEpisode = null,
            genres = genres?.mapNotNull { it.russian ?: it.name } ?: emptyList(),
            rating = score?.toFloatOrNull()?.toInt(),
            posterUrl = posterUrl,
            source = "Shikimori",
            airedOn = airedOn
        )
    }
}
