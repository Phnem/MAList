package com.example.myapplication.data.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class Anime(
    val id: String,
    val title: String,
    val episodes: Int,
    val rating: Int,
    val imageFileName: String?,
    val orderIndex: Int,
    val dateAdded: Long,
    val isFavorite: Boolean = false,
    val tags: ImmutableList<String> = persistentListOf(),
    val categoryType: String = "",
    val comment: String = ""
)
