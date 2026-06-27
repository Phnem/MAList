package com.example.myapplication.data.repository

import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.SortOption
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.ApiService
import com.example.myapplication.network.AnimeDetails
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.GithubReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Single source of truth for anime data. Network calls return Result;
 * no try/catch in ViewModel — use result.fold(onSuccess, onFailure).
 *
 * Список аниме — реактивный пайплайн: БД (asFlow) + фильтр/сортировка в памяти (до ~3–5k записей).
 */
class AnimeRepository(
    private val apiService: ApiService,
    private val localDataSource: AnimeLocalDataSource
) {

    fun getAllAnimeSnapshot(): List<Anime> = localDataSource.getAllAnimeList()

    /**
     * Реактивный поток списка: БД + фильтр/сортировка в памяти (до ~3–5k записей).
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observeAnimeList(
        searchQuery: Flow<String>,
        sortOption: Flow<SortOption>,
        sortAscending: Flow<Boolean>,
        filterTags: Flow<List<String>>,
        mediaTypeFilter: Flow<MediaType?>
    ): Flow<List<Anime>> = mediaTypeFilter.flatMapLatest { filterType ->
        combine(
            localDataSource.observeAllAnime(filterType),
            searchQuery.debounce(300L).distinctUntilChanged(),
            sortOption.distinctUntilChanged(),
            sortAscending.distinctUntilChanged(),
            filterTags.distinctUntilChanged(),
        ) { list, q, sort, asc, tags ->
            filterAndSortInMemory(list, q, sort, asc, tags)
        }
    }.flowOn(Dispatchers.Default)

    private fun filterAndSortInMemory(
        list: List<Anime>,
        searchQuery: String,
        sortOption: SortOption,
        sortAscending: Boolean,
        filterTags: List<String>
    ): List<Anime> {
        val trimmed = searchQuery.trim()
        val filtered = list.asSequence()
            .let { seq ->
                if (trimmed.isEmpty()) seq
                else {
                    val lower = trimmed.lowercase()
                    seq.filter { it.title.lowercase().contains(lower) }
                }
            }
            .let { seq ->
                if (filterTags.isEmpty()) seq
                else seq.filter { it.tags.containsAll(filterTags) }
            }
        return when (sortOption) {
            SortOption.RATING -> if (sortAscending) filtered.sortedBy { it.rating } else filtered.sortedByDescending { it.rating }
            SortOption.EPISODES -> if (sortAscending) filtered.sortedBy { it.episodes } else filtered.sortedByDescending { it.episodes }
            SortOption.TITLE -> if (sortAscending) filtered.sortedBy { it.title } else filtered.sortedByDescending { it.title }
        }.toList()
    }

    fun getAnimeById(id: String): Anime? = localDataSource.getAnimeById(id)

    suspend fun toggleFavorite(id: String): Anime? {
        val anime = getAnimeById(id) ?: return null
        val updated = anime.copy(isFavorite = !anime.isFavorite)
        localDataSource.updateAnime(updated)
        return updated
    }

    suspend fun fetchDetails(title: String, language: AppLanguage, isManga: Boolean = false, apiId: String? = null): Result<AnimeDetails?> {
        return apiService.fetchDetails(title, language, isManga, apiId)
    }

    suspend fun findTotalEpisodes(
        title: String,
        categoryType: String,
        appContentType: AppContentType
    ): Result<Pair<Int, String>?> {
        return apiService.findTotalEpisodes(title, categoryType, appContentType)
    }

    suspend fun checkGithubUpdate(owner: String, repo: String): Result<GithubReleaseInfo?> {
        return apiService.checkGithubUpdate(owner, repo)
    }

    suspend fun searchApi(query: String, contentType: AppContentType, language: AppLanguage): Result<List<ApiSearchResult>> {
        return apiService.searchApi(query, contentType, language)
    }

    suspend fun searchAnimeShikimoriOnly(
        query: String,
        language: AppLanguage,
        allowZeroEpisodes: Boolean = false
    ): Result<List<ApiSearchResult>> {
        return apiService.searchAnimeShikimoriOnly(query, language, allowZeroEpisodes)
    }

    suspend fun mediaByAnilistId(id: Int): Result<ApiSearchResult?> {
        return apiService.mediaByAnilistId(id)
    }

    suspend fun searchAnimeAniListOnly(
        query: String,
        language: AppLanguage,
        limit: Int = 20
    ): Result<List<ApiSearchResult>> {
        return apiService.searchAnimeAniListOnly(query, language, limit)
    }

    suspend fun shikimoriById(id: Int, language: AppLanguage): Result<ApiSearchResult?> {
        return apiService.shikimoriById(id, language)
    }

    suspend fun malById(id: Int, language: AppLanguage): Result<ApiSearchResult?> {
        return apiService.malById(id, language)
    }

    suspend fun searchAnimeMalOnly(
        query: String,
        language: AppLanguage,
        limit: Int = 20
    ): Result<List<ApiSearchResult>> {
        return apiService.searchAnimeMalOnly(query, language, limit)
    }
}
