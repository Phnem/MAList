package com.example.myapplication.localplayer.domain

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Сырой видеофайл, найденный в дереве SAF (до разбора номеров эпизодов). */
data class ScannedFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
)

/**
 * Обход выбранного SAF-дерева и сбор видеофайлов. Идём вглубь с ограничением [MAX_DEPTH]
 * (учитываем папки сезонов), но не бесконечно — чтобы случайно выбранная большая папка не
 * подвешивала скан. Файлы фильтруем по расширению видео.
 */
class EpisodeScanner(private val context: Context) {

    suspend fun scan(treeUri: Uri): List<ScannedFile> = withContext(Dispatchers.IO) {
        val root = if (treeUri.scheme == "file") {
            val path = treeUri.path ?: return@withContext emptyList()
            DocumentFile.fromFile(java.io.File(path))
        } else {
            DocumentFile.fromTreeUri(context, treeUri)
        } ?: return@withContext emptyList()
        val out = ArrayList<ScannedFile>()
        collect(root, depth = 0, out = out)
        out.sortedBy { it.name.lowercase() }
    }

    private fun collect(dir: DocumentFile, depth: Int, out: MutableList<ScannedFile>) {
        if (out.size >= MAX_FILES) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (out.size >= MAX_FILES) return
            when {
                child.isDirectory -> if (depth < MAX_DEPTH) collect(child, depth + 1, out)
                child.isFile -> {
                    val name = child.name ?: continue
                    if (isVideo(name)) {
                        out += ScannedFile(child.uri, name, child.length())
                    }
                }
            }
        }
    }

    private fun isVideo(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    private companion object {
        const val MAX_DEPTH = 2
        const val MAX_FILES = 2000
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "webm", "mov", "m4v", "ts", "m2ts",
            "flv", "wmv", "mpg", "mpeg", "3gp", "ogv", "m3u8",
        )
    }
}
