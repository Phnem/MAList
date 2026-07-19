package com.example.myapplication.domain.addedit

/**
 * Параметры сохранения аниме. Domain-слой не зависит от UI (AddEditUiState).
 */
data class SaveAnimeParams(
    val animeId: String?,
    val title: String,
    /** Английское название (вариация). null = нет. */
    val titleEn: String? = null,
    /** Русское название (обратное обогащение). Пробрасывается, чтобы редактирование его не затирало. */
    val titleRu: String? = null,
    val episodes: Int,
    /** 10-балльная шкала, одна цифра после запятой (0 = не оценено). */
    val rating: Float,
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
