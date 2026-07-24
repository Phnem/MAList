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
import com.example.myapplication.network.EnrichedTitles
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

    suspend fun fetchDetails(
        title: String,
        language: AppLanguage,
        isManga: Boolean = false,
        apiId: String? = null,
        malId: Int? = null,
        anilistId: Int? = null,
        titleEn: String? = null,
        shikimoriId: Int? = null,
    ): Result<AnimeDetails?> {
        return apiService.fetchDetails(title, language, isManga, apiId, malId, anilistId, titleEn, shikimoriId)
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

    /** Батч-проверка серий: AniList id_in, до 50 тайтлов на запрос. */
    suspend fun episodeCheckByAnilistIds(ids: List<Int>): Result<List<com.example.myapplication.network.EpisodeCheckMedia>> {
        return apiService.episodeCheckByAnilistIds(ids)
    }

    /** Батч-проверка серий по MAL id (idMal_in) — для записей без anilistId. */
    suspend fun episodeCheckByMalIds(malIds: List<Int>): Result<List<com.example.myapplication.network.EpisodeCheckMedia>> {
        return apiService.episodeCheckByMalIds(malIds)
    }

    /** Явно проставить MAL id (напр. реконсиляция malId по Shikimori myanimelist_id в «Исправить БД»). */
    suspend fun setMalId(animeId: String, malId: Int) {
        localDataSource.setMalId(animeId, malId)
    }

    suspend fun searchAnimeMalOnly(
        query: String,
        language: AppLanguage,
        limit: Int = 20
    ): Result<List<ApiSearchResult>> {
        return apiService.searchAnimeMalOnly(query, language, limit)
    }

    suspend fun searchAnimeKitsuOnly(query: String, limit: Int = 10): Result<List<ApiSearchResult>> {
        return apiService.searchAnimeKitsuOnly(query, limit)
    }

    suspend fun searchAnimeAnilibriaOnly(query: String, limit: Int = 10): Result<List<ApiSearchResult>> {
        return apiService.searchAnimeAnilibriaOnly(query, limit)
    }

    suspend fun enrichTitlesByIds(anilistId: Int?, malId: Int?): Result<EnrichedTitles?> {
        return apiService.enrichTitlesByIds(anilistId, malId)
    }

    suspend fun enrichTitlesBySearch(query: String, limit: Int = 8): Result<List<EnrichedTitles>> {
        return apiService.enrichTitlesBySearch(query, limit)
    }

    suspend fun malTitlesById(id: Int): Result<EnrichedTitles?> {
        return apiService.malTitlesById(id)
    }

    suspend fun malTitlesBySearch(query: String, limit: Int = 8): Result<List<EnrichedTitles>> {
        return apiService.malTitlesBySearch(query, limit)
    }

    suspend fun russianTitleByShikimoriId(id: Int): Result<EnrichedTitles?> {
        return apiService.russianTitleByShikimoriId(id)
    }

    suspend fun russianTitlesBySearch(query: String, limit: Int = 8): Result<List<EnrichedTitles>> {
        return apiService.russianTitlesBySearch(query, limit)
    }
}
