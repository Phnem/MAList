package com.example.myapplication.media.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class MediaJobProgress(
    val jobId: String,
    val status: String, // queued | downloading | success | failed | cancelled
    val progressPct: Int = 0,
    val filePath: String? = null,
    val error: String? = null,
)

/** In-memory job progress bus for MediaDownloadWorker ↔ UI. */
object MediaJobBus {
    private val flows = ConcurrentHashMap<String, MutableStateFlow<MediaJobProgress>>()
    private val cancelSet = ConcurrentHashMap.newKeySet<String>()

    fun flow(jobId: String): StateFlow<MediaJobProgress> =
        flows.getOrPut(jobId) {
            MutableStateFlow(MediaJobProgress(jobId = jobId, status = "queued"))
        }.asStateFlow()

    fun update(progress: MediaJobProgress) {
        val state = flows.getOrPut(progress.jobId) {
            MutableStateFlow(progress)
        }
        state.value = progress
    }

    fun isCancelRequested(jobId: String): Boolean = cancelSet.contains(jobId)

    fun requestCancel(jobId: String) {
        cancelSet.add(jobId)
        val prev = flows[jobId]?.value ?: MediaJobProgress(jobId, "cancelled")
        update(prev.copy(status = "cancelled"))
    }

    fun clearCancel(jobId: String) {
        cancelSet.remove(jobId)
    }
}
