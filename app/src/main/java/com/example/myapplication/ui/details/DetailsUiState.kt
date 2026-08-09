package com.example.myapplication.ui.details

import com.example.myapplication.network.AnimeDetails

/**
 * UI state для DetailsScreen (sealed class)
 */
sealed class DetailsUiState {
    object Idle : DetailsUiState()
    object Loading : DetailsUiState()
    data class Success(val details: AnimeDetails) : DetailsUiState()
    object Error : DetailsUiState()
    /** EN-аниме: нет внешнего anime-id и titleEn — описание не запрашиваем. */
    object MissingEnglishTitle : DetailsUiState()
}
