package com.example.myapplication.domain.addedit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.local.DevPreferencesKeys
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.domain.IdGenerator
import com.example.myapplication.domain.enrichment.CollectionEnrichmentCoordinator
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first

class SaveAnimeUseCase(
    private val localDataSource: AnimeLocalDataSource,
    private val imageStorage: ImageStorageRepository,
    private val idGenerator: IdGenerator,
    private val settingsDataStore: DataStore<Preferences>,
    private val enrichmentCoordinator: CollectionEnrichmentCoordinator,
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
            titleEn = params.titleEn,
            titleRu = params.titleRu,
            episodes = params.episodes,
            rating = params.rating,
            imageFileName = imageFileName,
            orderIndex = if (isNew) localDataSource.getMaxOrderIndex() + 1 else params.orderIndex,
            dateAdded = if (isNew) idGenerator.currentTimeMillis() else params.dateAdded,
            isFavorite = params.isFavorite,
            tags = params.selectedTags.toImmutableList(),
            categoryType = params.categoryType,
            comment = params.comment,
            anilistId = params.anilistId,
            malId = params.malId,
            shikimoriId = params.shikimoriId,
            anilistNotFoundAt = params.anilistNotFoundAt,
            malNotFoundAt = params.malNotFoundAt,
            shikimoriNotFoundAt = params.shikimoriNotFoundAt
        )

        if (isNew) {
            localDataSource.insertAnime(anime)
            maybeScheduleLiveMaintenance()
        } else {
            localDataSource.updateAnime(anime)
        }
    }

    /** Мгновенный догон Live Maintenance при добавлении записи: фоновый воркер подтянет
     *  недостающие поля/названия (идемпотентно — KEEP). Включено по умолчанию. */
    private suspend fun maybeScheduleLiveMaintenance() {
        val prefs = runCatching { settingsDataStore.data.first() }.getOrNull()
        // Отсутствие ключа = ВКЛ по умолчанию.
        if (prefs?.get(DevPreferencesKeys.LIVE_MAINTENANCE_ENABLED) == false) return
        enrichmentCoordinator.enqueueImmediateScan()
    }
}
