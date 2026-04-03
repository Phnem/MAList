package com.example.myapplication.ui.home

import androidx.compose.runtime.Immutable
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.data.models.SortOption
import com.example.myapplication.network.ApiSearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class ApiSearchUiModel(
    val result: ApiSearchResult,
    val isAdded: Boolean
)

/**
 * Immutable UI state для HomeScreen.
 * Список аниме реактивно берётся из animeListFlow (БД + фильтр/сортировка в памяти).
 * ImmutableList даёт Compose стабильность и убирает микрофризы при скролле.
 */
data class HomeUiState(
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.RATING,
    val sortAscending: Boolean = false,
    val filterTags: ImmutableList<String> = persistentListOf(),
    val filterCategory: String = "",
    val isGenreFilterVisible: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val updates: ImmutableList<AnimeUpdate> = persistentListOf(),
    val statsAnimeList: ImmutableList<Anime> = persistentListOf(),
    val isRestoringFromCloud: Boolean = false,
    val isListLoaded: Boolean = false,
    val apiSearchResults: ImmutableList<ApiSearchResult> = persistentListOf(),
    val apiSearchLoading: Boolean = false,
    val apiSearchError: String? = null,
    val addingFromApiId: String? = null
)
