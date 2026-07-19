package com.example.myapplication.domain.titles

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Состояние фонового «Дубляжа названий». Живёт в singleton-координаторе (как [com.example.myapplication.domain.settings.RepairDbCoordinator]):
 * пользователь запускает и уходит — WorkManager доводит проход до конца (в т.ч. self-reschedule
 * большими партиями), а экран настроек просто отражает текущее состояние.
 */
sealed interface TitleDubbingState {
    data object Idle : TitleDubbingState

    /** total == 0 пока считаем объём. */
    data class Running(val processed: Int, val total: Int) : TitleDubbingState

    data class Finished(val message: String, val success: Boolean) : TitleDubbingState
}

class TitleDubbingCoordinator(private val context: Context) {

    private val _state = MutableStateFlow<TitleDubbingState>(TitleDubbingState.Idle)
    val state: StateFlow<TitleDubbingState> = _state.asStateFlow()

    /** Накопительный счётчик за весь проход (включая self-reschedule). */
    @Volatile
    private var sessionEnriched: Int = 0

    @Volatile
    private var sessionInitialTotal: Int = 0

    /** Ручной запуск. Повтор при живой работе игнорируется (KEEP). */
    fun start(language: AppLanguage, fullRescan: Boolean = false) {
        if (_state.value is TitleDubbingState.Running) return
        sessionEnriched = 0
        sessionInitialTotal = 0
        _state.value = TitleDubbingState.Running(processed = 0, total = 0)
        enqueue(language, fullRescan, ExistingWorkPolicy.KEEP, delayMs = 0L)
    }

    /** Self-reschedule остатка большого прохода из воркера (APPEND — после текущего). */
    internal fun scheduleContinuation(language: AppLanguage, delayMs: Long) {
        enqueue(language, fullRescan = false, ExistingWorkPolicy.APPEND, delayMs)
    }

    internal fun noteSessionStart(queued: Int) {
        if (sessionInitialTotal == 0) sessionInitialTotal = queued
    }

    internal fun noteEnriched(delta: Int) {
        if (delta > 0) sessionEnriched += delta
    }

    internal fun sessionEnrichedCount(): Int = sessionEnriched

    internal fun sessionInitialTotal(): Int = sessionInitialTotal

    private fun enqueue(
        language: AppLanguage,
        fullRescan: Boolean,
        policy: ExistingWorkPolicy,
        delayMs: Long,
    ) {
        val builder = OneTimeWorkRequestBuilder<TitleDubbingWorker>()
            .setInputData(
                workDataOf(
                    KEY_LANGUAGE to language.name,
                    KEY_FULL_RESCAN to fullRescan,
                ),
            )
        if (delayMs > 0L) builder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, builder.build())
    }

    fun acknowledgeResult() {
        if (_state.value is TitleDubbingState.Finished) {
            _state.value = TitleDubbingState.Idle
        }
    }

    internal fun onProgress(processed: Int, total: Int) {
        _state.value = TitleDubbingState.Running(processed = processed, total = total)
    }

    internal fun onFinished(message: String, success: Boolean) {
        _state.value = TitleDubbingState.Finished(message = message, success = success)
    }

    companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_FULL_RESCAN = "fullRescan"
        private const val UNIQUE_WORK_NAME = "TitleDubbing"
    }
}
