package com.example.myapplication.data.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class Anime(
    val id: String,
    val title: String,
    /** Английское название (автообогащение). null = ещё не заполнено. */
    val titleEn: String? = null,
    /** Русское название (обратное обогащение через Shikimori). null = ещё не заполнено. */
    val titleRu: String? = null,
    val episodes: Int,
    /** 10-балльная шкала с одной цифрой после запятой (0 = не оценено). См. [RatingScale]. */
    val rating: Float,
    val imageFileName: String?,
    val orderIndex: Int,
    val dateAdded: Long,
    val isFavorite: Boolean = false,
    val tags: ImmutableList<String> = persistentListOf(),
    val categoryType: String = "",
    val comment: String = "",
    val anilistId: Int? = null,
    val malId: Int? = null,
    val shikimoriId: Int? = null,
    val anilistNotFoundAt: Long? = null,
    val malNotFoundAt: Long? = null,
    val shikimoriNotFoundAt: Long? = null,
    val mediaType: MediaType = MediaType.ANIME
)

enum class MediaType {
    ANIME,
    MANGA,
    TV_SERIES
}
