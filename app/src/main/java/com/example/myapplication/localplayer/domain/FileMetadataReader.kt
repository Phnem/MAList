package com.example.myapplication.localplayer.domain

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Читает встроенные метаданные контейнера (mkv/mp4-теги) как дополнительный сигнал, когда имя файла
 * неинформативно. Даёт название (`title`) и по возможности сезон/эпизод из тегов сезона/эпизода.
 *
 * `MediaMetadataRetriever` открывает каждый файл, поэтому вызываем ТОЛЬКО для файлов, которым имя
 * не дало номер, — чтобы не платить IO за всю папку.
 */
class FileMetadataReader(private val context: Context) {

    data class Meta(val title: String?, val episode: Int?, val season: Int?)

    suspend fun read(uri: Uri): Meta = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val title = mmr.extract(MediaMetadataRetriever.METADATA_KEY_TITLE)
            // Ключей сезона/эпизода нет в публичном API MMR, но у mp4/itunes бывают номера трека/диска,
            // которые в аниме-рипах иногда несут номер эпизода/сезона — берём как слабый сигнал.
            val episode = mmr.extract(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()
            val season = mmr.extract(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()
            Meta(title = title, episode = episode?.takeIf { it in 1..3000 }, season = season?.takeIf { it in 1..99 })
        } catch (_: Exception) {
            Meta(null, null, null)
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun MediaMetadataRetriever.extract(key: Int): String? =
        runCatching { extractMetadata(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
