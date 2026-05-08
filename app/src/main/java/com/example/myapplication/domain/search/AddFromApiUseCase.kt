package com.example.myapplication.domain.search

import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.domain.IdGenerator
import com.example.myapplication.domain.addedit.SaveAnimeParams
import com.example.myapplication.domain.addedit.SaveAnimeUseCase
import com.example.myapplication.network.ApiSearchResult

/** Конвертация рейтинга из шкалы 1–10 в 5 звёзд: rating5 = min(5, floor(rating10/2) + 1). */
fun rating10To5(rating10: Int): Int = (rating10 / 2 + 1).coerceIn(1, 5)

/**
 * Adds a media item from API search result to the local database.
 * Downloads poster from URL, maps genres to app genre IDs, converts rating to 5-star, saves via SaveAnimeUseCase.
 */
class AddFromApiUseCase(
    private val saveAnimeUseCase: SaveAnimeUseCase,
    private val imageStorage: ImageStorageRepository,
    private val idGenerator: IdGenerator,
    private val genreRepository: GenreRepository
) {
    suspend operator fun invoke(
        result: ApiSearchResult
    ): Result<Unit> = runCatching {
        val animeId = idGenerator.generateUuid()
        val imageFileName = result.posterUrl?.let { url ->
            imageStorage.saveImageFromUrl(url, animeId).getOrNull()
        }

        val rating10 = (result.rating ?: 0).let { if (it > 10) it / 10 else it }
        val rating5 = rating10To5(rating10)
        val selectedTags = result.genres.mapNotNull { apiGenre ->
            val normalized = apiGenre.trim()
            if (normalized.isBlank()) return@mapNotNull null
            genreRepository.allGenres.find { def ->
                def.id.equals(normalized, ignoreCase = true) ||
                    def.ru.equals(normalized, ignoreCase = true) ||
                    def.en.equals(normalized, ignoreCase = true) ||
                    def.id.replace("-", " ").equals(normalized.replace("-", " "), ignoreCase = true)
            }?.id
        }.distinct().take(5)

        val params = SaveAnimeParams(
            animeId = null,
            title = result.title,
            episodes = result.episodes.coerceAtLeast(1),
            rating = rating5,
            imageUri = null,
            currentImageFileName = imageFileName,
            orderIndex = 0,
            dateAdded = idGenerator.currentTimeMillis(),
            isFavorite = false,
            selectedTags = selectedTags,
            categoryType = result.categoryType,
            comment = "",
            anilistId = result.externalId?.toIntOrNull().takeIf { result.source.equals("AniList", ignoreCase = true) },
            malId = result.externalId?.toIntOrNull().takeIf {
                result.source.equals("MAL", ignoreCase = true) || result.source.equals("Jikan", ignoreCase = true)
            },
            shikimoriId = result.externalId?.toIntOrNull().takeIf { result.source.equals("Shikimori", ignoreCase = true) }
        )
        saveAnimeUseCase(params)
    }
}
