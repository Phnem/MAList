package com.example.myapplication.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.updates.BatchEpisodeCheckUseCase
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.notifications.AnimeNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class AnimeUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val localDataSource: AnimeLocalDataSource by inject()
    private val notifier: AnimeNotifier by inject()
    private val batchEpisodeCheckUseCase: BatchEpisodeCheckUseCase by inject()
    private val settingsDataStore: DataStore<Preferences> by inject(named("settings"))

    private val langKey = stringPreferencesKey("lang")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val langStr = settingsDataStore.data.first()[langKey] ?: "EN"
            val language = try {
                AppLanguage.valueOf(langStr)
            } catch (e: Exception) {
                AppLanguage.EN
            }
            val animeList = localDataSource.getAllAnimeList()
            if (animeList.isEmpty()) return@withContext Result.success()
            val ignoredMap = localDataSource.getIgnoredMap()
            val existingUpdates = localDataSource.getUpdates().toMutableList()
            val rawUpdates = batchEpisodeCheckUseCase(animeList = animeList, language = language)
            val candidateUpdates = rawUpdates.filter { ignoredMap[it.animeId] != it.newEpisodes }
            val newlyDetected = mutableListOf<com.example.myapplication.data.models.AnimeUpdate>()
            candidateUpdates.forEach { updateObj ->
                if (existingUpdates.none { it.animeId == updateObj.animeId && it.newEpisodes == updateObj.newEpisodes }) {
                    existingUpdates.removeAll { it.animeId == updateObj.animeId }
                    existingUpdates.add(updateObj)
                    newlyDetected += updateObj
                }
            }
            if (newlyDetected.isNotEmpty()) {
                localDataSource.setUpdates(existingUpdates)
                newlyDetected.forEach { update -> notifier.showUpdateNotification(update) }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
