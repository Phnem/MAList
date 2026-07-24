package com.example.myapplication.localplayer.model

import kotlinx.serialization.Serializable

/**
 * Один локальный видеофайл-эпизод из выбранной пользователем папки.
 *
 * [documentUri] — SAF `content://`-URI конкретного файла (получен через дерево [LocalSource.treeUri],
 * поэтому воспроизводится под тем же persistable-разрешением). [episodeNumber] `null` = ни алгоритм,
 * ни ИИ не смогли определить номер (файл всё равно показываем, просто в конце списка).
 */
@Serializable
data class LocalEpisode(
    val documentUri: String,
    val originalName: String,
    val displayName: String,
    val episodeNumber: Int? = null,
    /** Сезон из имени файла/метаданных (S02E05 и т.п.). null = не определён. */
    val season: Int? = null,
    val sizeBytes: Long = 0L,
)

/**
 * Привязка одного тайтла коллекции к локальной папке с эпизодами. Хранится в [ ... ]
 * `LocalSourceStore` (файловый кэш filesDir, по образцу WebLinksStore) — без миграций БД,
 * чтобы фичу можно было удалить целиком, не трогая схему.
 */
@Serializable
data class LocalSource(
    val animeId: String,
    val treeUri: String,
    val folderLabel: String,
    val episodes: List<LocalEpisode>,
    val updatedAt: Long,
)
