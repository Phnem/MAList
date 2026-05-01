package com.example.myapplication.domain

private val TITLE_NORMALIZE_REGEX = Regex("[^\\p{L}\\p{N}]")

/**
 * Сопоставление тайтлов (поиск, дубликаты, AI-блэклист) — детерминированно с главным списком.
 */
fun String.normalizeForSearch(): String =
    lowercase().replace(TITLE_NORMALIZE_REGEX, "")
