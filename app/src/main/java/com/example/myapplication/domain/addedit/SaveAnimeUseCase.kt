package com.example.myapplication.domain.addedit

import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.domain.IdGenerator
import kotlinx.collections.immutable.toImmutableList

class SaveAnimeUseCase(
    private val localDataSource: AnimeLocalDataSource,
    private val imageStorage: ImageStorageRepository,
    private val idGenerator: IdGenerator
) {
    suspend operator fun invoke(params: SaveAnimeParams): Result<Unit> = runCatching {
        val animeId = params.animeId ?: idGenerator.generateUuid()
        val imageFileName = params.imageUri?.let { uri ->
            imageStorage.saveImage(uri, animeId).getOrThrow()
        } ?: params.currentImageFileName

        val isNew = params.animeId == null
        val anime = Anime(
            id = animeId,
            title = params.title,
            episodes = params.episodes,
            rating = params.rating,
            imageFileName = imageFileName,
            orderIndex = if (isNew) localDataSource.getMaxOrderIndex() + 1 else params.orderIndex,
            dateAdded = if (isNew) idGenerator.currentTimeMillis() else params.dateAdded,
            isFavorite = params.isFavorite,
            tags = params.selectedTags.toImmutableList(),
            categoryType = params.categoryType,
            comment = params.comment
        )

        if (isNew) {
            localDataSource.insertAnime(anime)
        } else {
            localDataSource.updateAnime(anime)
        }
    }
}
