package com.example.myapplication.sync.supabase

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
