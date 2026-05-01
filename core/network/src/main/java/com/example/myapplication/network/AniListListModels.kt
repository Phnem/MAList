package com.example.myapplication.network

/**
 * Плоская запись списка AniList после [flatMap] `lists` → `entries` в [AniListRemoteDataSource].
 */
data class AniListFlatListEntry(
    val entryId: Int?,
    val mediaId: Int,
    val romaji: String,
    val english: String,
    val progress: Int
)
