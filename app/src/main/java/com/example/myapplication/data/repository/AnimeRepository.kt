package com.example.myapplication.data.repository

import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.SortOption
import com.example.myapplication.network.ApiService
import com.example.myapplication.network.AnimeDetails
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.GithubReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

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
     * Реактивный поток списка: БД подписывается один раз, фильтрация/сортировка в памяти.
     * При изменении БД или параметров (поиск, сортировка, фильтр) пересчитывается только фильтрация.
     */
    fun observeAnimeList(
        searchQuery: Flow<String>,
        sortOption: Flow<SortOption>,
        sortAscending: Flow<Boolean>,
        filterTags: Flow<List<String>>
    ): Flow<List<Anime>> = combine(
        localDataSource.observeAllAnime(), // БД читается один раз при старте
        searchQuery,
        sortOption,
        sortAscending,
        filterTags
    ) { list, q, sort, asc, tags ->
        // Фильтрация/сортировка в памяти (~0.1 мс) при любом изменении параметров
        filterAndSortInMemory(list, q, sort, asc, tags)
    }
        .flowOn(Dispatchers.Default)

    private fun filterAndSortInMemory(
        list: List<Anime>,
        searchQuery: String,
        sortOption: SortOption,
        sortAscending: Boolean,
        filterTags: List<String>
    ): List<Anime> {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotEmpty()) {
            var result = list
            val lower = trimmed.lowercase()
            result = result.filter { it.title.lowercase().contains(lower) }
            if (filterTags.isNotEmpty()) {
                result = result.filter { it.tags.containsAll(filterTags) }
            }
            return when (sortOption) {
                SortOption.RATING -> if (sortAscending) result.sortedBy { it.rating } else result.sortedByDescending { it.rating }
                SortOption.EPISODES -> if (sortAscending) result.sortedBy { it.episodes } else result.sortedByDescending { it.episodes }
                SortOption.TITLE -> if (sortAscending) result.sortedBy { it.title } else result.sortedByDescending { it.title }
            }
        }
        var result = list
        if (filterTags.isNotEmpty()) {
            result = result.filter { it.tags.containsAll(filterTags) }
        }
        return when (sortOption) {
            SortOption.RATING -> if (sortAscending) result.sortedBy { it.rating } else result.sortedByDescending { it.rating }
            SortOption.EPISODES -> if (sortAscending) result.sortedBy { it.episodes } else result.sortedByDescending { it.episodes }
            SortOption.TITLE -> if (sortAscending) result.sortedBy { it.title } else result.sortedByDescending { it.title }
        }
    }

    fun getAnimeById(id: String): Anime? = localDataSource.getAnimeById(id)

    suspend fun toggleFavorite(id: String): Anime? {
        val anime = getAnimeById(id) ?: return null
        val updated = anime.copy(isFavorite = !anime.isFavorite)
        localDataSource.updateAnime(updated)
        return updated
    }

    suspend fun fetchDetails(title: String, language: AppLanguage): Result<AnimeDetails?> {
        return apiService.fetchDetails(title, language)
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
