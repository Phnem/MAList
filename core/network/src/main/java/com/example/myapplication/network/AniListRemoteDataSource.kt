package com.example.myapplication.network

import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Optional
import com.example.myapplication.network.anilist.MediaByIdQuery
import com.example.myapplication.network.anilist.MediaListCollectionChunkQuery
import com.example.myapplication.network.anilist.SaveMediaListEntryMutation
import com.example.myapplication.network.anilist.SearchMediaPageQuery
import com.example.myapplication.network.anilist.SearchMediaQuery
import com.example.myapplication.network.anilist.ViewerIdQuery
import com.example.myapplication.network.anilist.type.MediaListStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AniListRemote"

/**
 * AniList GraphQL API via Apollo Kotlin. Type-safe queries from .graphql → generated models.
 * Публичные запросы без Bearer; список пользователя и мутации — с заголовком на вызове.
 */
class AniListRemoteDataSource(
    private val apolloClient: ApolloClient,
    private val rateLimiter: TokenBucketRateLimiter
) {
    suspend fun fetchAnimeDetails(query: String): Result<AnimeDetails?> = runCatching {
        withContext(Dispatchers.IO) {
            rateLimiter.acquire()
            val response = apolloClient.query(SearchMediaQuery(q = Optional.presentIfNotNull(query))).execute()
            response.throwIfGraphQlErrors("SearchMedia")
            val media = response.data?.Media ?: return@withContext null
            val title = media.title
            val romaji = title?.romaji?.orEmpty() ?: ""
            val english = title?.english?.orEmpty() ?: ""
            if (!isTitleSimilar(query, romaji, english)) return@withContext null

            val desc = media.description?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
            val type = media.type?.name ?: "ANIME"
            val status = media.status?.name ?: ""
            val episodes = media.episodes ?: 0
            val nextEp = media.nextAiringEpisode?.let { "Episode ${it.episode}" }
            val genres = media.genres?.filterNotNull() ?: emptyList()
            val rating = media.averageScore

            val startDate = media.startDate
            val airedOn = if (startDate?.year != null) {
                buildString {
                    append(startDate.year)
                    if (startDate.month != null) append("-${startDate.month.toString().padStart(2, '0')}")
                    if (startDate.day != null) append("-${startDate.day.toString().padStart(2, '0')}")
                }
            } else null

            AnimeDetails(
                title = romaji.ifEmpty { english }.ifEmpty { query },
                altTitle = if (english.isNotEmpty() && english != romaji) english else null,
                description = desc,
                type = type,
                status = status,
                episodesAired = episodes,
                episodesTotal = media.episodes,
                nextEpisode = nextEp,
                genres = genres,
                rating = rating,
                posterUrl = null,
                source = "AniList",
                airedOn = airedOn
            )
        }
    }

    suspend fun searchAnime(query: String, limit: Int = 20, language: AppLanguage = AppLanguage.EN, isManga: Boolean = false): Result<List<ApiSearchResult>> = runCatching {
        withContext(Dispatchers.IO) {
            rateLimiter.acquire()
            val response = apolloClient.query(
                SearchMediaPageQuery(
                    q = Optional.presentIfNotNull(query.takeIf { it.isNotBlank() }),
                    page = Optional.present(1),
                    perPage = Optional.present(limit),
                    type = Optional.present(if (isManga) com.example.myapplication.network.anilist.type.MediaType.MANGA else com.example.myapplication.network.anilist.type.MediaType.ANIME)
                )
            ).execute()
            response.throwIfGraphQlErrors("SearchMediaPage")
            val page = response.data?.Page ?: return@withContext emptyList()
            val mediaList = page.media?.filterNotNull() ?: return@withContext emptyList()
            mediaList.mapNotNull { media ->
                val title = media.title
                val romaji = title?.romaji?.orEmpty() ?: ""
                val english = title?.english?.orEmpty() ?: ""
                val displayTitle = english.ifEmpty { romaji }.ifEmpty { return@mapNotNull null }
                val desc = media.description?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
                val posterUrl = media.coverImage?.large
                ApiSearchResult(
                    title = displayTitle,
                    altTitle = if (romaji.isNotEmpty() && romaji != displayTitle) romaji else null,
                    posterUrl = posterUrl,
                    episodes = media.episodes ?: 0,
                    description = desc,
                    type = media.type?.name ?: if (isManga) "MANGA" else "ANIME",
                    genres = media.genres?.filterNotNull() ?: emptyList(),
                    rating = media.averageScore,
                    source = "AniList",
                    categoryType = if (isManga) "MANGA" else "ANIME",
                    externalId = media.id?.toString()
                )
            }
        }
    }

    suspend fun mediaByAnilistId(id: Int): Result<ApiSearchResult?> = runCatching {
        withContext(Dispatchers.IO) {
            rateLimiter.acquire()
            val response = apolloClient.query(MediaByIdQuery(id = Optional.present(id))).execute()
            response.throwIfGraphQlErrors("MediaById")
            val media = response.data?.Media ?: return@withContext null
            val title = media.title
            val romaji = title?.romaji?.orEmpty().orEmpty()
            val english = title?.english?.orEmpty().orEmpty()
            val displayTitle = english.ifEmpty { romaji }.ifEmpty { return@withContext null }
            val desc = media.description?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
            val posterUrl = media.coverImage?.large
            ApiSearchResult(
                title = displayTitle,
                altTitle = if (romaji.isNotEmpty() && romaji != displayTitle) romaji else null,
                posterUrl = posterUrl,
                episodes = media.episodes ?: 0,
                description = desc,
                type = media.type?.name ?: "ANIME",
                genres = media.genres?.filterNotNull() ?: emptyList(),
                rating = media.averageScore,
                source = "AniList",
                categoryType = "ANIME",
                externalId = media.id?.toString()
            )
        }
    }

    suspend fun findTotalEpisodes(query: String): Result<Pair<Int, String>?> = runCatching {
        withContext(Dispatchers.IO) {
            rateLimiter.acquire()
            val response = apolloClient.query(SearchMediaQuery(q = Optional.presentIfNotNull(query))).execute()
            response.throwIfGraphQlErrors("SearchMedia findTotal")
            val media = response.data?.Media ?: return@withContext null
            val title = media.title
            val romaji = title?.romaji?.orEmpty() ?: ""
            val english = title?.english?.orEmpty() ?: ""
            if (!isTitleSimilar(query, romaji, english)) return@withContext null

            var count = media.episodes ?: 0
            media.nextAiringEpisode?.let { count = (it.episode ?: 0) - 1 }
            if (count > 0) count to "AniList" else null
        }
    }

    suspend fun getViewerId(accessToken: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            rateLimiter.acquire()
            val response = apolloClient.query(ViewerIdQuery())
                .addHttpHeader("Authorization", "Bearer $accessToken")
                .execute()
            response.throwIfGraphQlErrors("ViewerId")
            val id = response.data?.Viewer?.id
                ?: error("Viewer.id is null (token or rights?)")
            id
        }
    }

    /**
     * Все записи аниме-списка пользователя: MediaListCollection по chunk, плоский [AniListFlatListEntry].
     */
    suspend fun fetchAllAnimeListEntries(accessToken: String, userId: Int): Result<List<AniListFlatListEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val all = mutableListOf<AniListFlatListEntry>()
                var chunk = 1
                val perChunk = 50
                while (true) {
                    rateLimiter.acquire()
                    val response = apolloClient.query(
                        MediaListCollectionChunkQuery(
                            userId = Optional.present(userId),
                            chunk = Optional.present(chunk),
                            perChunk = Optional.present(perChunk)
                        )
                    )
                        .addHttpHeader("Authorization", "Bearer $accessToken")
                        .execute()
                    response.throwIfGraphQlErrors("MediaListCollection chunk=$chunk")
                    val col = response.data?.MediaListCollection
                        ?: error("MediaListCollection null")
                    val flat = col.lists?.filterNotNull().orEmpty().flatMap { group ->
                        (group.entries ?: emptyList()).filterNotNull().mapNotNull { e ->
                            val media = e.media ?: return@mapNotNull null
                            val mid = e.mediaId ?: media.id ?: return@mapNotNull null
                            val t = media.title
                            val romaji = t?.romaji.orEmpty()
                            val english = t?.english.orEmpty()
                            AniListFlatListEntry(
                                entryId = e.id,
                                mediaId = mid,
                                romaji = romaji,
                                english = english,
                                progress = e.progress ?: 0
                            )
                        }
                    }
                    all += flat
                    if (col.hasNextChunk != true) break
                    chunk++
                }
                all
            }
        }

    suspend fun saveMediaListEntry(
        accessToken: String,
        mediaId: Int,
        status: MediaListStatus,
        progress: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            rateLimiter.acquire()
            val response = apolloClient.mutation(
                SaveMediaListEntryMutation(
                    mediaId = Optional.present(mediaId),
                    status = Optional.present(status),
                    progress = Optional.present(progress)
                )
            )
                .addHttpHeader("Authorization", "Bearer $accessToken")
                .execute()
            response.throwIfGraphQlErrors("SaveMediaListEntry mediaId=$mediaId")
            if (response.data?.SaveMediaListEntry == null) {
                error("SaveMediaListEntry returned null data")
            }
        }
    }

    private fun <T : com.apollographql.apollo.api.Operation.Data> ApolloResponse<T>.throwIfGraphQlErrors(operation: String) {
        val errs = errors
        if (!errs.isNullOrEmpty()) {
        val msg = errs.joinToString { it.message ?: "" }
            Log.w(TAG, "GraphQL errors ($operation): $msg")
            error(msg)
        }
    }
}
