package com.example.myapplication.network.remanga

import com.example.myapplication.network.AnimeDetails
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.remanga.dto.ImageDto
import com.example.myapplication.network.remanga.dto.RemangaResponse
import com.example.myapplication.network.remanga.dto.SearchTitleDto
import com.example.myapplication.network.remanga.dto.TitleDetailsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class RemangaRemoteDataSource(
    private val httpClient: HttpClient
) {
    private val baseUrl = "https://api.remanga.org/api"
    private val mediaBaseUrl = "https://remanga.org"

    suspend fun searchManga(query: String, limit: Int = 30): List<ApiSearchResult> = coroutineScope {
        try {
            val response: RemangaResponse<List<SearchTitleDto>> = httpClient.get("$baseUrl/search/") {
                parameter("query", query)
                parameter("count", 30)
                remangaHeaders()
            }.body()

            val items = response.content ?: return@coroutineScope emptyList()

            val bestMatches = items
                .sortedWith(
                    compareByDescending<SearchTitleDto> { calculateRelevanceScore(it, query) }
                        .thenByDescending { it.avgRating ?: 0.0 }
                )
                .filter { calculateRelevanceScore(it, query) > 0 }
                .take(limit)

            bestMatches.mapNotNull { item ->
                val dir = item.dir ?: return@mapNotNull null
                try {
                    val detailsResponse: RemangaResponse<TitleDetailsDto> =
                        httpClient.get("$baseUrl/titles/$dir/") {
                            remangaHeaders()
                        }.body()
                    detailsResponse.content?.let { mapDetailsToResult(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    mapSearchItemToResult(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getMangaDetails(slug: String): AnimeDetails? = withContext(Dispatchers.IO) {
        try {
            val response: RemangaResponse<TitleDetailsDto> = httpClient.get("$baseUrl/titles/$slug/") {
                remangaHeaders()
            }.body()
            val dto = response.content ?: return@withContext null

            AnimeDetails(
                title = dto.rusName ?: dto.enName ?: "Unknown",
                altTitle = dto.enName,
                posterUrl = buildPosterUrl(dto.img, dto.dir),
                episodesAired = dto.countChapters ?: 0,
                episodesTotal = null,
                nextEpisode = null,
                description = dto.description ?: "",
                type = "Manga",
                genres = dto.genres?.mapNotNull { it.name } ?: emptyList(),
                rating = ratingToInt10(dto.avgRating),
                source = "Remanga",
                status = dto.status?.name ?: "Unknown"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun mapDetailsToResult(dto: TitleDetailsDto): ApiSearchResult = ApiSearchResult(
        title = dto.rusName ?: dto.enName ?: "Unknown",
        altTitle = dto.enName,
        posterUrl = buildPosterUrl(dto.img, dto.dir),
        episodes = dto.countChapters ?: 0,
        description = dto.description ?: "",
        type = "Manga",
        genres = dto.genres?.mapNotNull { it.name } ?: emptyList(),
        rating = ratingToInt10(dto.avgRating),
        source = "Remanga",
        categoryType = "MANGA",
        externalId = dto.dir ?: dto.id?.toString()
    )

    private fun mapSearchItemToResult(item: SearchTitleDto): ApiSearchResult = ApiSearchResult(
        title = item.rusName ?: item.enName ?: "Unknown",
        altTitle = item.enName,
        posterUrl = buildPosterUrl(item.img, item.dir),
        episodes = item.countChapters ?: 0,
        description = "",
        type = item.type ?: "Manga",
        genres = emptyList(),
        rating = ratingToInt10(item.avgRating),
        source = "Remanga",
        categoryType = "MANGA",
        externalId = item.dir ?: item.id?.toString()
    )

    private fun buildPosterUrl(img: ImageDto?, dir: String?): String =
        mediaBaseUrl + (img?.high ?: img?.mid ?: img?.low ?: dir?.let { "/media/titles/$it/cover.jpg" } ?: "")

    private fun ratingToInt10(avgRating: Double?): Int? =
        avgRating?.times(10)?.toInt()

    private fun io.ktor.client.request.HttpRequestBuilder.remangaHeaders() {
        headers {
            append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append("Accept", "application/json, text/plain, */*")
            append("Origin", "https://remanga.org")
            append("Referer", "https://remanga.org/")
        }
    }

    private fun calculateRelevanceScore(title: SearchTitleDto, query: String): Int {
        val q = query.lowercase()
        val rus = (title.rusName ?: "").lowercase()
        val en = (title.enName ?: "").lowercase()

        if (rus == q || en == q) return 100
        if (rus.startsWith(q) || en.startsWith(q)) return 75

        val paddedRus = " $rus "
        val paddedEn = " $en "
        val paddedQ = " $q "

        if (paddedRus.contains(paddedQ) || paddedEn.contains(paddedQ)) return 50
        if (rus.contains(q) || en.contains(q)) return 25

        return 0
    }
}
