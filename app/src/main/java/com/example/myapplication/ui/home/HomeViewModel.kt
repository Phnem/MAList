package com.example.myapplication.ui.home

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.domain.normalizeForSearch
import com.example.myapplication.domain.search.AddFromApiUseCase
import com.example.myapplication.domain.stats.ResolveStatsFooterPhraseUseCase
import com.example.myapplication.updates.BatchEpisodeCheckUseCase
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.data.models.SortOption
import com.example.myapplication.notifications.AnimeNotifier
import com.example.myapplication.notifications.animeUpdateNotificationId
import com.example.myapplication.SyncReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.work.*
import com.example.myapplication.worker.AnimeUpdateWorker

private val KEY_CONTENT_TYPE = stringPreferencesKey("contentType")
private val KEY_LANG = stringPreferencesKey("lang")

class HomeViewModel(
    private val repository: AnimeRepository,
    private val localDataSource: AnimeLocalDataSource,
    private val notifier: AnimeNotifier,
    private val imageStorage: ImageStorageRepository,
    private val settingsDataStore: DataStore<Preferences>,
    private val addFromApiUseCase: AddFromApiUseCase,
    private val statsFooterPhraseUseCase: ResolveStatsFooterPhraseUseCase,
    private val batchEpisodeCheckUseCase: BatchEpisodeCheckUseCase
) : ViewModel() {

    private var apiSearchJob: Job? = null
    private var pullToRefreshJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val settingsContentType: StateFlow<AppContentType> = settingsDataStore.data
        .map { prefs ->
            runCatching { AppContentType.valueOf(prefs[KEY_CONTENT_TYPE] ?: "ANIME") }
                .getOrElse { AppContentType.ANIME }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppContentType.ANIME)

    /** Language from DataStore — use this in UI instead of a separate SettingsViewModel (avoids duplicate VM scope). */
    val uiLanguage: StateFlow<AppLanguage> = settingsDataStore.data
        .map { prefs ->
            runCatching { AppLanguage.valueOf(prefs[KEY_LANG] ?: "EN") }
                .getOrElse { AppLanguage.EN }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.EN)

    val syncReport = MutableStateFlow(com.example.myapplication.SyncReport())

    val animeListFlow: StateFlow<kotlinx.collections.immutable.ImmutableList<Anime>> = repository.observeAnimeList(
        searchQuery = _uiState.map { it.searchQuery },
        sortOption = _uiState.map { it.sortOption },
        sortAscending = _uiState.map { it.sortAscending },
        filterTags = _uiState.map { it.filterTags },
        mediaTypeFilter = _uiState.map { it.libraryMediaTypeFilter }
    ).map { it.toImmutableList() }
     .onEach { if (!_uiState.value.isListLoaded) _uiState.update { s -> s.copy(isListLoaded = true) } }
     .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = persistentListOf()
    )

    val apiSearchWithStatus: StateFlow<kotlinx.collections.immutable.ImmutableList<ApiSearchUiModel>> = combine(
        _uiState.map { it.apiSearchResults }.distinctUntilChanged(),
        animeListFlow
    ) { apiResults, localList ->
        apiResults.map { result ->
            ApiSearchUiModel(
                result = result,
                isAdded = isAddedInMemory(result, localList)
            )
        }.toImmutableList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    private var ignoredUpdatesMap = mutableMapOf<String, Int>()
    private var hasCheckedForUpdatesThisSession = false

    init {
        viewModelScope.launch {
            ignoredUpdatesMap.putAll(localDataSource.getIgnoredMap())
            localDataSource.observeUpdates().collect { list ->
                _uiState.update { it.copy(updates = list.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            checkForUpdates()
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { statsFooterPhraseUseCase.warmCatalog() }
        }
    }

    fun refreshList() {
        if (pullToRefreshJob?.isActive == true) return
        pullToRefreshJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                checkForUpdates(force = true)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun scheduleBackgroundWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val updateRequest = PeriodicWorkRequestBuilder<AnimeUpdateWorker>(
            6, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AnimeUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                apiSearchError = null,
                apiSearchResults = if (query.isBlank()) persistentListOf() else it.apiSearchResults
            )
        }
        apiSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(apiSearchLoading = false, apiSearchResults = persistentListOf()) }
            return
        }
        apiSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            if (_uiState.value.searchQuery.trim() != trimmed) return@launch
            _uiState.update { it.copy(apiSearchLoading = true, apiSearchError = null) }
            val mediaType = _uiState.value.searchMediaTypeFilter
            val contentType = when (mediaType) {
                com.example.myapplication.data.models.MediaType.ANIME -> AppContentType.ANIME
                com.example.myapplication.data.models.MediaType.MANGA -> AppContentType.MANGA
                com.example.myapplication.data.models.MediaType.TV_SERIES -> AppContentType.SERIES
            }
            val language = uiLanguage.value
            repository.searchApi(trimmed, contentType, language)
                .fold(
                    onSuccess = { results ->
                        if (_uiState.value.searchQuery.trim() == trimmed) {
                            _uiState.update {
                                it.copy(
                                    apiSearchResults = results.toImmutableList(),
                                    apiSearchLoading = false,
                                    apiSearchError = null
                                )
                            }
                        }
                    },
                    onFailure = { e ->
                        if (_uiState.value.searchQuery.trim() == trimmed) {
                            _uiState.update {
                                it.copy(
                                    apiSearchLoading = false,
                                    apiSearchError = e.message ?: "Search failed"
                                )
                            }
                        }
                    }
                )
        }
    }

    private fun isAddedInMemory(
        result: ApiSearchResult,
        localList: List<Anime>
    ): Boolean {
        val q = result.title.normalizeForSearch()
        if (q.isEmpty()) return false
        return localList.any { anime ->
            // Enforce media type check (categoryType is ANIME, MANGA, TV_SERIES)
            if (anime.mediaType.name != result.categoryType && !(anime.mediaType.name == "TV_SERIES" && result.categoryType == "SERIES")) return@any false
            
            val keys = listOfNotNull(anime.title, anime.titleEn, anime.titleRu)
                .map { it.normalizeForSearch() }
                .filter { it.isNotEmpty() }
            keys.any { t -> t.contains(q) || q.contains(t) }
        }
    }

    fun addFromApi(result: ApiSearchResult) {
        val key = "${result.source}_${result.externalId ?: result.title}"
        viewModelScope.launch {
            _uiState.update { it.copy(addingFromApiId = key) }
            addFromApiUseCase(result)
                .fold(
                    onSuccess = {
                        _uiState.update { it.copy(addingFromApiId = null) }
                    },
                    onFailure = { e ->
                        e.printStackTrace()
                        _uiState.update { it.copy(addingFromApiId = null) }
                    }
                )
        }
    }

    fun applySort(option: SortOption, isAscending: Boolean) {
        _uiState.update { current ->
            current.copy(sortOption = option, sortAscending = isAscending)
        }
    }

    fun toggleGenreFilter() {
        _uiState.update { it.copy(isGenreFilterVisible = !it.isGenreFilterVisible) }
    }

    fun setGenreFilterVisible(visible: Boolean) {
        _uiState.update { it.copy(isGenreFilterVisible = visible) }
    }

    fun updateFilterTags(tags: List<String>, category: String) {
        _uiState.update {
            it.copy(filterTags = tags.toImmutableList(), filterCategory = category)
        }
    }

    fun setLibraryMediaTypeFilter(filter: com.example.myapplication.data.models.MediaType?) {
        _uiState.update { it.copy(libraryMediaTypeFilter = filter) }
    }

    fun setSearchMediaTypeFilter(filter: com.example.myapplication.data.models.MediaType) {
        _uiState.update { it.copy(searchMediaTypeFilter = filter) }
        updateSearchQuery(_uiState.value.searchQuery)
    }

    fun deleteAnime(id: String) {
        viewModelScope.launch {
            runCatching {
                val anime = localDataSource.getAnimeById(id) ?: return@launch
                anime.imageFileName?.let { imageStorage.deleteImage(it) }
                localDataSource.deleteAnime(id)
            }.onFailure { it.printStackTrace() }
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            runCatching {
                val anime = localDataSource.getAnimeById(id) ?: return@launch
                localDataSource.updateAnime(anime.copy(isFavorite = !anime.isFavorite))
            }.onFailure { it.printStackTrace() }
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        if (!force && hasCheckedForUpdatesThisSession) return
        if (_uiState.value.isCheckingUpdates) return
        hasCheckedForUpdatesThisSession = true
        _uiState.update { it.copy(isCheckingUpdates = true) }
        viewModelScope.launch {
            runCatching {
                ignoredUpdatesMap.clear()
                ignoredUpdatesMap.putAll(localDataSource.getIgnoredMap())
                val language = readLanguageFromSettings()
                val rawUpdates = batchEpisodeCheckUseCase(
                    animeList = localDataSource.getAllAnimeList(),
                    language = language
                )
                val newUpdates = rawUpdates.filter { update ->
                    ignoredUpdatesMap[update.animeId] != update.newEpisodes
                }.distinctBy { it.animeId to it.newEpisodes }
                localDataSource.setUpdates(newUpdates)
                _uiState.update { it.copy(isCheckingUpdates = false) }
                if (newUpdates.isNotEmpty()) {
                    newUpdates.forEach { update -> notifier.showUpdateNotification(update) }
                }
            }.onFailure {
                it.printStackTrace()
                _uiState.update { it.copy(isCheckingUpdates = false) }
            }
        }
    }

    private suspend fun readLanguageFromSettings(): AppLanguage {
        val prefs = settingsDataStore.data.first()
        val raw = prefs[KEY_LANG] ?: "EN"
        return runCatching { AppLanguage.valueOf(raw) }.getOrElse { AppLanguage.EN }
    }

    fun acceptUpdate(update: AnimeUpdate, ctx: Context) {
        val anime = getAnimeById(update.animeId) ?: return
        viewModelScope.launch {
            localDataSource.updateAnime(anime.copy(episodes = update.newEpisodes))
            localDataSource.removeUpdate(update.animeId)
            cancelAnimeUpdateNotification(ctx, update.animeId)
        }
    }

    fun dismissUpdate(update: AnimeUpdate, ctx: Context) {
        viewModelScope.launch {
            localDataSource.addIgnored(update.animeId, update.newEpisodes)
            ignoredUpdatesMap[update.animeId] = update.newEpisodes
            localDataSource.removeUpdate(update.animeId)
            cancelAnimeUpdateNotification(ctx, update.animeId)
        }
    }

    private fun cancelAnimeUpdateNotification(ctx: Context, animeId: String) {
        val nm = ctx.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(animeUpdateNotificationId(animeId))
    }

    fun getAnimeById(id: String): Anime? {
        return localDataSource.getAnimeById(id)
    }

    fun getImgPath(name: String?): String? {
        if (name == null) return null
        return imageStorage.getImageFilePath(name)
    }

    fun loadStatsAnimeList() {
        viewModelScope.launch {
            val list = localDataSource.getAllAnimeList().toImmutableList()
            val avgRating = if (list.isEmpty()) {
                0.0
            } else {
                list.map { it.rating.toDouble() }.average()
            }
            val totalEpisodes = list.sumOf { it.episodes }
            val language = uiLanguage.value
            val ratingFormatted = String.format(Locale.getDefault(), "%.1f", avgRating)
            val footer = statsFooterPhraseUseCase(
                language = language,
                avgRating = avgRating,
                totalEpisodes = totalEpisodes,
                ratingFormattedForUi = ratingFormatted
            )
            _uiState.update {
                it.copy(
                    statsAnimeList = list,
                    statsFooterPhrase = footer
                )
            }
        }
    }
}
