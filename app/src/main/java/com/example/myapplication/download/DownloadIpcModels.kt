package com.example.myapplication.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Контракты File-based IPC между приложением и внешним локальным демоном-воркером (обработка
 * видео). Приложение пишет [InputTask] → `input.json`, воркер обновляет `output.json` → [OutputTask].
 * Поля именуются snake_case под схему воркера (см. [SerialName]).
 */

/** Задача на скачивание, которую приложение отправляет воркеру (`input.json`). */
@Serializable
data class InputTask(
    @SerialName("task_id") val taskId: String,
    @SerialName("anime_id") val animeId: String,
    /** Название тайтла — воркер ищет по нему на источниках (anime_id внутренний, не ищется). */
    @SerialName("search_query") val searchQuery: String = "",
    /** Регион/озвучка ("RU", "EN", …). */
    val region: String,
    /** Номера серий через запятую, например "1,2,3". */
    @SerialName("episode_range") val episodeRange: String,
    @SerialName("season_number") val seasonNumber: Int,
    /** "480p" | "720p" | "1080p". */
    val quality: String,
    /** Абсолютный путь папки, куда воркер кладёт готовые файлы. */
    @SerialName("output_dir") val outputDir: String,
    @SerialName("is_debug") val isDebug: Boolean = false,
    @SerialName("ffmpeg_path") val ffmpegPath: String? = null,
)

/** Снимок прогресса задачи, который воркер пишет в `output.json`. */
@Serializable
data class OutputTask(
    @SerialName("task_id") val taskId: String,
    /** См. [DownloadStatus]: pending | in_progress | success | partial_success | failed. */
    val status: String,
    @SerialName("progress_pct") val progressPct: Int = 0,
    val episodes: List<EpisodeProgress> = emptyList(),
    val summary: TaskSummary? = null,
    val error: String? = null,
) {
    /** Задача дошла до конца — поллинг можно останавливать. */
    val isTerminal: Boolean get() = status in DownloadStatus.TERMINAL
}

/** Прогресс одной серии внутри задачи. */
@Serializable
data class EpisodeProgress(
    val number: Int,
    /** Напр. "success" | "downloading" | "failed" | "skipped". */
    val status: String,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("progress_pct") val progressPct: Int = 0,
)

/** Итоговая сводка задачи. */
@Serializable
data class TaskSummary(
    val total: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
)

/** Статусы задачи и множество терминальных (на которых Flow завершается). */
object DownloadStatus {
    const val PENDING = "pending"
    const val IN_PROGRESS = "in_progress"
    const val SUCCESS = "success"
    const val PARTIAL_SUCCESS = "partial_success"
    const val FAILED = "failed"

    val TERMINAL = setOf(SUCCESS, PARTIAL_SUCCESS, FAILED)
}
