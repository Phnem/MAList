package com.example.myapplication.domain.settings

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage

/**
 * Состояние фонового «Исправления БД». Живёт в singleton-координаторе, а не во ViewModel:
 * пользователь жмёт кнопку и уходит куда угодно — WorkManager доводит работу до конца,
 * а экран настроек (когда/если открыт) просто отражает текущее состояние.
 */
sealed interface RepairDbState {
    data object Idle : RepairDbState

    /** total == 0 пока считаем объём работы. */
    data class Running(val processed: Int, val total: Int) : RepairDbState

    data class Finished(
        val message: String,
        val log: String,
        val success: Boolean,
    ) : RepairDbState
}

class RepairDbCoordinator(private val context: Context) {

    private val _state = MutableStateFlow<RepairDbState>(RepairDbState.Idle)
    val state: StateFlow<RepairDbState> = _state.asStateFlow()

    /** Ставит фоновую задачу. Повторное нажатие при живой работе игнорируется (KEEP). */
    fun start(language: AppLanguage, contentType: AppContentType) {
        if (_state.value is RepairDbState.Running) return
        _state.value = RepairDbState.Running(processed = 0, total = 0)

        val request = OneTimeWorkRequestBuilder<RepairDbWorker>()
            .setInputData(
                workDataOf(
                    RepairDbWorker.KEY_LANGUAGE to language.name,
                    RepairDbWorker.KEY_CONTENT_TYPE to contentType.name,
                ),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Пользователь закрыл диалог результата — возвращаемся в Idle. */
    fun acknowledgeResult() {
        if (_state.value is RepairDbState.Finished) {
            _state.value = RepairDbState.Idle
        }
    }

    internal fun onProgress(processed: Int, total: Int) {
        _state.value = RepairDbState.Running(processed = processed, total = total)
    }

    internal fun onFinished(message: String, log: String, success: Boolean) {
        _state.value = RepairDbState.Finished(message = message, log = log, success = success)
    }

    private companion object {
        private const val UNIQUE_WORK_NAME = "RepairAnimeDb"
    }
}
