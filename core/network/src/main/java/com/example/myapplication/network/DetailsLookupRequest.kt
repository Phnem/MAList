package com.example.myapplication.network

/** Единый details lookup seam для UI/repository/network без растущей цепочки параметров. */
data class DetailsLookupRequest(
    val title: String,
    val language: AppLanguage,
    val isManga: Boolean = false,
    /** Id ReManga; у остальных источников используются типизированные поля ниже. */
    val remangaId: String? = null,
    val malId: Int? = null,
    val anilistId: Int? = null,
    val titleEn: String? = null,
    val shikimoriId: Int? = null,
    val externalIds: ExternalIds = ExternalIds(),
    val appContentType: AppContentType? = null,
)
