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
    TV_SERIES;

    companion object {
        /**
         * `categoryType` (строка раздела поиска / источника) → тип записи.
         *
         * Источники своему типу не свидетели: Shikimori отдаёт `"ANIME"` и для манги, потому что
         * `/api/mangas` мапится тем же `toApiSearchResult()`. Единственный надёжный признак —
         * раздел, в котором тайтл нашли; он и проставляется в `categoryType` на выходе
         * `ApiService.searchApi`. Здесь только перевод строки в enum.
         *
         * null = строка про тип ничего не говорит (пустая, неизвестная) — решает вызывающий.
         */
        fun fromCategoryType(raw: String?): MediaType? = when (raw?.trim()?.uppercase()) {
            "MANGA" -> MANGA
            "ANIME" -> ANIME
            "SERIES", "TV_SERIES", "TV", "MOVIE" -> TV_SERIES
            else -> null
        }
    }
}
