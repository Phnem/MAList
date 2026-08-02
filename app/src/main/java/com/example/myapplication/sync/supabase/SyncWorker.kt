package com.example.myapplication.sync.supabase

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val syncRepository: SyncRepository by inject()
    private val attachmentSyncManager: AttachmentSyncManager by inject()

    override suspend fun doWork(): Result {
        return try {
            syncRepository.pushPendingChanges()
            syncRepository.pullRemoteChanges()
            // Отметка времени последней успешной синхронизации — читается панелью синхронизации
            // (nottif.kt). Фоновый воркер не проходит через SupabaseSyncCoordinator.syncNow,
            // поэтому пишем метку и здесь.
            applicationContext
                .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_sync_time", System.currentTimeMillis())
                .apply()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Background sync failed: ${safeSyncError(e)}")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}
