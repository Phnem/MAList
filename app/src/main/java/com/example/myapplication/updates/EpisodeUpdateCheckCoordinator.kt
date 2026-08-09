package com.example.myapplication.updates

import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.network.AppLanguage

/** One serialized collection workflow for all episode providers sharing anime_update. */
class EpisodeUpdateCheckCoordinator(
    private val animeCheck: BatchEpisodeCheckUseCase,
    private val seriesCheck: SeriesEpisodeCheckUseCase,
) {
    suspend fun detectAndStore(language: AppLanguage): List<AnimeUpdate> =
        animeCheck.detectAndStore(language) + seriesCheck.detectAndStore()
}
