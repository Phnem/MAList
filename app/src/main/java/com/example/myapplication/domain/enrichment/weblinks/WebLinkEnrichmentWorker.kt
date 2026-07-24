package com.example.myapplication.domain.enrichment.weblinks

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.domain.enrichment.CollectionEnrichmentCoordinator
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Независимый воркер резолва ссылок «Где смотреть». Специально ОТДЕЛЁН от [FullEnrichmentWorker]:
 * тот длинный (поля+названия) и его переживает перезапуски WorkManager, теряя фазу ссылок.
 * Этот делает только ссылки, чанком [CHUNK], пишет инкрементально (NonCancellable в use case),
 * и если осталось — сам себя перепланирует (APPEND) с короткой паузой. Так даже при агрессивных
 * убийствах воркеров прогресс копится и иконки появляются в первые же секунды.
 */
class WebLinkEnrichmentWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val useCase: WebLinkEnrichmentUseCase by inject()
    private val localDataSource: AnimeLocalDataSource by inject()
    private val coordinator: CollectionEnrichmentCoordinator by inject()
    private val settingsDataStore: DataStore<Preferences> by inject(named("settings"))

    override suspend fun doWork(): Result {
        return try {
            val all = localDataSource.getAllAnimeList()
            Log.i(TAG, "doWork START collection=${all.size} languages=${AppLanguage.entries}")
            if (all.isEmpty()) return Result.success()

            var processed = 0
            for (language in AppLanguage.entries) {
                if (isStopped) break
                processed += useCase.enrichBatch(
                    allAnime = all,
                    language = language,
                    limit = CHUNK_PER_LANGUAGE,
                    shouldStop = { isStopped },
                )
            }
            val remaining = AppLanguage.entries.sumOf { useCase.countStale(all, it) }
            Log.i(TAG, "doWork END processed=$processed remaining=$remaining stopped=$isStopped")

            if (!isStopped && remaining > 0) {
                coordinator.scheduleWebLinkContinuation(CONTINUATION_DELAY_MS)
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "WebLinkEnrichmentWorker failed", e)
            Result.retry()
        }
    }

    private suspend fun readLanguage(): AppLanguage {
        val langStr = runCatching { settingsDataStore.data.first()[LANG_KEY] }.getOrNull() ?: "EN"
        return runCatching { AppLanguage.valueOf(langStr) }.getOrDefault(AppLanguage.EN)
    }

    companion object {
        private const val TAG = "WebLinkWorker"
        private val LANG_KEY = stringPreferencesKey("lang")
        /** Сколько тайтлов за один заход, затем self-reschedule. */
        private const val CHUNK_PER_LANGUAGE = 10
        private const val CONTINUATION_DELAY_MS = 20_000L
    }
}
