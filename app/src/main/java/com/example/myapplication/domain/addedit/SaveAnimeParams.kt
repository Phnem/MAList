package com.example.myapplication.domain.addedit

/**
 * Параметры сохранения аниме. Domain-слой не зависит от UI (AddEditUiState).
 */
data class SaveAnimeParams(
    val animeId: String?,
    val title: String,
    val episodes: Int,
    val rating: Int,
    val imageUri: String?,
    val currentImageFileName: String?,
    val orderIndex: Int,
    val dateAdded: Long,
    val isFavorite: Boolean,
    val selectedTags: List<String>,
    val categoryType: String,
    val comment: String,
    val anilistId: Int? = null,
    val malId: Int? = null,
    val shikimoriId: Int? = null,
    val anilistNotFoundAt: Long? = null,
    val malNotFoundAt: Long? = null,
    val shikimoriNotFoundAt: Long? = null
)
