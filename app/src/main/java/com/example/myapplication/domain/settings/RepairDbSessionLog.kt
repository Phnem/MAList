package com.example.myapplication.domain.settings

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory log for one «Исправить БД» session; discarded unless exported. */
class RepairDbSessionLog {
    private val lines = mutableListOf<String>()

    fun info(message: String) = append("I", message)

    fun debug(message: String) = append("D", message)

    fun warn(message: String, throwable: Throwable? = null) = append("W", message, throwable)

    fun error(message: String, throwable: Throwable? = null) = append("E", message, throwable)

    fun asText(): String = lines.joinToString("\n")

    private fun append(level: String, message: String, throwable: Throwable? = null) {
        val line = "${timestamp()} $level/$LOG_TAG: $message"
        lines += line
        when (level) {
            "I" -> Log.i(LOG_TAG, message)
            "D" -> Log.d(LOG_TAG, message)
            "W" -> if (throwable != null) Log.w(LOG_TAG, message, throwable) else Log.w(LOG_TAG, message)
            "E" -> if (throwable != null) Log.e(LOG_TAG, message, throwable) else Log.e(LOG_TAG, message)
        }
        throwable?.let { lines += it.stackTraceToString() }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private companion object {
        private const val LOG_TAG = "RepairAnimeDb"
    }
}
