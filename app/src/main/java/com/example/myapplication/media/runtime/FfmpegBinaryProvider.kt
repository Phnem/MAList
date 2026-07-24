package com.example.myapplication.media.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Locates a single LGPL-oriented ffmpeg binary for stream-copy remux.
 * Prefers `nativeLibraryDir/libffmpeg.so` (already present for arm64 in this project).
 */
class FfmpegBinaryProvider(
    private val context: Context,
) {
    fun resolveExecutable(): File? {
        val native = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (native.exists() && native.canExecute()) {
            Log.i(TAG, "Using bundled ffmpeg: ${native.absolutePath} abi=${primaryAbi()}")
            return native
        }
        // Some OEMs strip execute bit on .so — still try as process path
        if (native.exists()) {
            runCatching { native.setExecutable(true) }
            if (native.exists()) return native
        }
        Log.w(TAG, "ffmpeg binary not found in ${context.applicationInfo.nativeLibraryDir}")
        return null
    }

    fun isAvailable(): Boolean = resolveExecutable() != null

    private fun primaryAbi(): String =
        Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    companion object {
        private const val TAG = "FfmpegBinaryProvider"
    }
}
