package com.example.myapplication.localplayer.domain

import android.net.Uri

/**
 * Не даём выбрать «общую» папку (Download, Documents, DCIM, корень хранилища и т.п.): смотреть аниме
 * из свалки всех файлов бессмысленно, а рекурсивный скан такой папки — дорого и опасно по мусору.
 * Требуем конкретную папку тайтла, например `Download/Jujutsu Kaisen`.
 *
 * SAF не позволяет ограничить сам системный пикер, поэтому проверяем уже выбранное дерево:
 * по последнему сегменту document-id (то, что после `:`) — это относительный путь внутри тома.
 */
class GeneralFolderGuard {

    /** true = папка слишком общая, выбор нужно отклонить. */
    fun isTooGeneral(treeUri: Uri): Boolean {
        if (treeUri.scheme == "file") return false // Внутренние файлы приложения безопасны
        val docId = lastPathSegmentAfterColon(treeUri) ?: return true // корень тома / непонятно — отклоняем
        val normalized = docId.trim().trim('/').lowercase()
        if (normalized.isEmpty()) return true // сам корень (primary:)
        // Ровно одна из зарезервированных общих папок в корне — отклоняем.
        // Вложенная (например "download/jujutsu kaisen") — это уже конкретный тайтл, разрешаем.
        if (normalized in RESERVED_ROOTS) return true
        if (normalized.startsWith("android/")) return true
        return false
    }

    private fun lastPathSegmentAfterColon(treeUri: Uri): String? {
        // tree uri: .../tree/primary%3ADownload%2FJujutsu%20Kaisen
        val decoded = Uri.decode(treeUri.toString())
        val treeMarker = decoded.substringAfterLast("/tree/", "")
        if (treeMarker.isEmpty()) return null
        val colon = treeMarker.indexOf(':')
        return if (colon == -1) "" else treeMarker.substring(colon + 1)
    }

    private companion object {
        val RESERVED_ROOTS = setOf(
            "download", "downloads", "documents", "dcim", "pictures", "movies",
            "music", "media", "podcasts", "ringtones", "alarms", "notifications",
            "audiobooks", "recordings", "screenshots",
        )
    }
}
