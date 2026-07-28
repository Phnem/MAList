package com.example.myapplication.manga.source

import com.example.myapplication.manga.domain.MangaChapter
import com.example.myapplication.manga.domain.MangaDetails
import com.example.myapplication.manga.domain.MangaItem
import com.example.myapplication.manga.domain.MangaPage
import com.example.myapplication.manga.domain.MangaSearchPage
import com.example.myapplication.manga.domain.MangaSourceId
import com.example.myapplication.manga.domain.MangaStatus
import com.example.myapplication.manga.domain.VetroMangaSource
import com.example.myapplication.network.TokenBucketRateLimiter
import com.example.myapplication.network.remanga.dto.RemangaResponse
import com.example.myapplication.network.remanga.dto.SearchTitleDto
import com.example.myapplication.network.remanga.dto.TitleDetailsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime

/**
 * Remanga — русскоязычный источник глав и страниц.
 *
 * Две особенности API, ради которых здесь больше кода, чем у MangaDex:
 * 1. Главы висят не на тайтле, а на «ветке» перевода (`branches`) — берём самую полную;
 * 2. `pages` приходит то плоским списком, то списком разворотов (список списков), поэтому
 *    разбирается вручную через [JsonElement], а не типизированным DTO.
 *
 * Заголовки браузерные и с Referer — тем же способом, что уже работающий
 * [com.example.myapplication.network.remanga.RemangaRemoteDataSource]; никакого обхода защиты,
 * просто корректный обычный запрос.
 */
class RemangaSource(
    private val client: HttpClient,
    private val rateLimiter: TokenBucketRateLimiter,
) : VetroMangaSource {

    override val id = MangaSourceId("remanga")
    override val name = "Remanga"
    override val language = "ru"

    override suspend fun search(query: String, page: Int): MangaSearchPage {
        if (page > 1) return MangaSearchPage(emptyList(), hasNextPage = false)
        val response: RemangaResponse<List<SearchTitleDto>> = request("$API/search/") {
            parameter("query", query)
            parameter("count", SEARCH_LIMIT)
        }
        val items = response.content.orEmpty().mapNotNull { it.toItem() }
        return MangaSearchPage(items, hasNextPage = false)
    }

    override suspend fun details(manga: MangaItem): MangaDetails {
        val dto = titleDetails(manga.key)
            ?: return MangaDetails(item = manga)
        return MangaDetails(
            item = dto.toItem() ?: manga,
            description = dto.description,
            tags = dto.genres.orEmpty().mapNotNull { it.name },
        )
    }

    override suspend fun chapters(manga: MangaItem): List<MangaChapter> {
        val details = titleDetails(manga.key) ?: return emptyList()
        // Веток перевода бывает несколько; самая полная — та, где больше глав.
        val branchId = details.branches
            .orEmpty()
            .maxByOrNull { it.countChapters ?: 0 }
            ?.id
            ?: return emptyList()

        val collected = mutableListOf<MangaChapter>()
        var page = 1
        while (page <= MAX_PAGES) {
            val response: RemangaResponse<List<RemangaChapterDto>> =
                request("$API/titles/chapters/") {
                    parameter("branch_id", branchId)
                    parameter("count", CHAPTERS_PER_PAGE)
                    parameter("page", page)
                    parameter("ordering", "index")
                }
            val chunk = response.content.orEmpty()
            if (chunk.isEmpty()) break
            collected += chunk.map { it.toChapter(manga) }
            if (chunk.size < CHAPTERS_PER_PAGE) break
            page++
        }
        return collected
    }

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        if (chapter.paid) return emptyList()
        val response: RemangaResponse<JsonObject> = request("$API/titles/chapters/${chapter.key}/")
        val pages = response.content?.get("pages") ?: return emptyList()
        return pages.flattenPageLinks()
            .mapIndexedNotNull { index, link ->
                absoluteMedia(link)?.let { url ->
                    MangaPage(index = index, url = url, headers = IMAGE_HEADERS)
                }
            }
    }

    /**
     * `pages` — либо `[{link}, …]`, либо `[[{link}], [{link}, {link}], …]` (развороты).
     * Разворот в ленте ридера всё равно разъезжается на отдельные страницы, поэтому просто
     * вытягиваем ссылки по порядку.
     */
    private fun JsonElement.flattenPageLinks(): List<String> = when (this) {
        is JsonArray -> flatMap { element ->
            when (element) {
                is JsonArray -> element.flattenPageLinks()
                is JsonObject -> listOfNotNull(element["link"]?.jsonPrimitive?.content)
                else -> emptyList()
            }
        }

        else -> emptyList()
    }

    private suspend fun titleDetails(slug: String): TitleDetailsDto? {
        val response: RemangaResponse<TitleDetailsDto> = request("$API/titles/$slug/")
        return response.content
    }

    private suspend inline fun <reified T> request(
        url: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {},
    ): T {
        rateLimiter.acquire()
        return client.get(url) {
            remangaHeaders()
            block()
        }.body()
    }

    private fun HttpRequestBuilder.remangaHeaders() {
        headers {
            append("User-Agent", BROWSER_UA)
            append("Accept", "application/json, text/plain, */*")
            append("Origin", SITE)
            append("Referer", "$SITE/")
        }
    }

    private fun SearchTitleDto.toItem(): MangaItem? {
        val slug = dir ?: return null
        return MangaItem(
            sourceId = this@RemangaSource.id,
            key = slug,
            title = rusName ?: enName ?: return null,
            altTitle = enName?.takeIf { it != rusName },
            coverUrl = absoluteMedia(img?.high ?: img?.mid ?: img?.low),
            year = issueYear,
            languages = listOf("ru"),
        )
    }

    /** Обложки приходят путём вида `/media/titles/…` — Coil нужен абсолютный URL. */
    private fun absoluteMedia(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        else -> SITE + path
    }

    private fun TitleDetailsDto.toItem(): MangaItem? {
        val slug = dir ?: return null
        return MangaItem(
            sourceId = this@RemangaSource.id,
            key = slug,
            title = rusName ?: enName ?: return null,
            altTitle = enName?.takeIf { it != rusName },
            coverUrl = absoluteMedia(img?.high ?: img?.mid ?: img?.low),
            status = when (status?.name?.lowercase()) {
                "продолжается" -> MangaStatus.Ongoing
                "закончен" -> MangaStatus.Completed
                "заморожен" -> MangaStatus.Hiatus
                "заброшен" -> MangaStatus.Cancelled
                else -> MangaStatus.Unknown
            },
            languages = listOf("ru"),
        )
    }

    private fun RemangaChapterDto.toChapter(manga: MangaItem): MangaChapter = MangaChapter(
        sourceId = this@RemangaSource.id,
        mangaKey = manga.key,
        key = id.toString(),
        number = chapter?.toDoubleOrNull(),
        volume = tome?.toDouble(),
        title = name?.takeIf { it.isNotBlank() },
        language = "ru",
        scanlator = publishers?.firstNotNullOfOrNull { it.name },
        // Купленную главу источник отдаёт как обычную — платной считаем только незакрытую.
        paid = isPaid == true && isBought != true,
        publishedAt = uploadDate.toEpochMillisOrZero(),
    )

    private fun String?.toEpochMillisOrZero(): Long =
        this?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: 0L

    private companion object {
        const val API = "https://api.remanga.org/api"
        const val SITE = "https://remanga.org"
        const val SEARCH_LIMIT = 30
        const val CHAPTERS_PER_PAGE = 100
        /** 100 глав на страницу × 60 = 6000: длиннее манги в природе почти не бывает. */
        const val MAX_PAGES = 60
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        /** Картинки отдаются только со своим Referer — без него CDN вернёт 403. */
        val IMAGE_HEADERS = mapOf(
            "Referer" to "$SITE/",
            "User-Agent" to BROWSER_UA,
        )
    }
}

@Serializable
private data class RemangaChapterDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("tome") val tome: Int? = null,
    @SerialName("chapter") val chapter: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("is_paid") val isPaid: Boolean? = null,
    @SerialName("is_bought") val isBought: Boolean? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    @SerialName("publishers") val publishers: List<RemangaPublisherDto>? = null,
)

@Serializable
private data class RemangaPublisherDto(
    @SerialName("name") val name: String? = null,
)
