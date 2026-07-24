package com.example.myapplication.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Мост File-based IPC к встроенному Python-воркеру (Chaquopy). Общение — через общую папку
 * `Vetro_Queue`: приложение кладёт [InputTask] → `input.json`, воркер пушит снимки [OutputTask]
 * через Java-callback (+ пишет `output.json` как durability/debug).
 */
interface FileIpcManager {

    /** Атомарно отправить задачу воркеру (перезаписывает `input.json`). */
    suspend fun submitTask(task: InputTask)

    /**
     * Подписка на прогресс задачи: push из Python callback + редкий poll fallback.
     * Завершается на терминальном статусе.
     */
    fun observeTaskProgress(taskId: String): Flow<OutputTask>

    /** Кооперативная отмена текущего Python-скачивания (threading.Event на стороне Python). */
    fun cancelActiveTask()

    /** Папка обмена (создаётся при обращении). */
    fun queueDir(): File
}

/**
 * Java-visible callback для Chaquopy: Python вызывает [onProgress] с JSON-снимком OutputTask.
 */
class PythonProgressBridge {
    @Volatile
    var listener: ((String) -> Unit)? = null

    @Suppress("unused") // called from Python
    fun onProgress(json: String) {
        listener?.invoke(json)
    }
}

class FileIpcManagerImpl(
    private val context: Context,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : FileIpcManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val progressBridge = PythonProgressBridge()
    private val activeTaskId = AtomicReference<String?>(null)

    override fun queueDir(): File =
        File(context.getExternalFilesDir(QUEUE_DIR_NAME) ?: context.filesDir, "").apply { mkdirs() }

    override suspend fun submitTask(task: InputTask) = withContext(Dispatchers.IO) {
        val dir = queueDir()
        val target = File(dir, INPUT_FILE)
        val tmp = File(dir, "$INPUT_FILE.tmp")
        tmp.writeText(json.encodeToString(InputTask.serializer(), task))
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
        activeTaskId.set(task.taskId)
        Log.i(TAG, "Submitted legacy task ${task.taskId}; native media engine is active")
        val failure = OutputTask(
            taskId = task.taskId,
            status = DownloadStatus.FAILED,
            error = "Legacy Python downloader was removed; use the native episode menu.",
        )
        File(dir, OUTPUT_FILE).writeText(
            json.encodeToString(OutputTask.serializer(), failure)
        )
        Unit
    }

    override fun cancelActiveTask() {
        activeTaskId.set(null)
    }

    override fun observeTaskProgress(taskId: String): Flow<OutputTask> = callbackFlow {
        val outputFile = File(queueDir(), OUTPUT_FILE)
        var last: OutputTask? = null

        fun emitIfNew(snapshot: OutputTask?) {
            if (snapshot == null || snapshot.taskId != taskId) return
            if (snapshot == last) return
            last = snapshot
            trySend(snapshot)
            if (snapshot.isTerminal) {
                Log.i(TAG, "Task $taskId reached terminal status: ${snapshot.status}")
                close()
            }
        }

        progressBridge.listener = { raw ->
            val snapshot = runCatching {
                json.decodeFromString(OutputTask.serializer(), raw)
            }.getOrNull()
            emitIfNew(snapshot)
        }

        // Durability / race fallback: poll file rarely in case a tick was missed.
        val pollJob = launch(Dispatchers.IO) {
            while (isActive) {
                emitIfNew(readOutput(outputFile))
                delay(pollIntervalMs)
            }
        }

        awaitClose {
            progressBridge.listener = null
            pollJob.cancel()
            if (activeTaskId.get() == taskId) {
                activeTaskId.compareAndSet(taskId, null)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun readOutput(file: File): OutputTask? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(OutputTask.serializer(), file.readText()) }
            .getOrElse {
                Log.d(TAG, "Skip output.json read (will retry): ${it.message}")
                null
            }
    }

    private companion object {
        const val TAG = "FileIpcManager"
        const val QUEUE_DIR_NAME = "Vetro_Queue"
        const val INPUT_FILE = "input.json"
        const val OUTPUT_FILE = "output.json"
        /** Fallback poll; primary updates come via [PythonProgressBridge]. */
        const val DEFAULT_POLL_INTERVAL_MS = 2500L
    }
}
