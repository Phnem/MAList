package com.example.myapplication.manga.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class ChapterReadingProgress(
    /** Текущая страница, 0-based: по ней ридер продолжает чтение. */
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    /**
     * Липкая отметка: ставится при открытии последней страницы и не снимается перечитыванием
     * с начала — иначе список глав терял бы галочки от одного случайного возврата назад.
     */
    val read: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val fraction: Float
        get() = if (pageCount > 0) ((pageIndex + 1).toFloat() / pageCount).coerceIn(0f, 1f) else 0f
}

/** Как листается ридер. Настройка глобальная: смена режима на каждый тайтл никому не нужна. */
enum class MangaReaderMode {
    /** Постранично, свайпом влево/вправо — обычная манга. */
    Paged,

    /** Непрерывная вертикальная лента — вебтуны и длинные полосы. */
    Webtoon,
}

@Serializable
private data class ReadingEntry(val chapterKey: String, val value: ChapterReadingProgress)

@Serializable
private data class ReadingSnapshot(val entries: List<ReadingEntry> = emptyList())

/**
 * Прогресс чтения по главам, одним компактным JSON-снимком на тайтл в общем settings DataStore —
 * тем же приёмом, что [com.example.myapplication.media.progress.EpisodePlaybackStore]: список глав
 * подписывается на один Flow и получает разом все отметки.
 */
class MangaReadingStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writeMutex = Mutex()

    fun progressFlow(animeId: String): Flow<Map<String, ChapterReadingProgress>> {
        val key = progressKey(animeId)
        return dataStore.data.map { decode(it[key]) }.distinctUntilChanged()
    }

    fun chapterFlow(animeId: String, chapterKey: String): Flow<ChapterReadingProgress?> =
        progressFlow(animeId).map { it[chapterKey] }.distinctUntilChanged()

    /** Разовое чтение — ридеру нужна стартовая страница на момент открытия, а не подписка. */
    suspend fun chapterProgress(animeId: String, chapterKey: String): ChapterReadingProgress? =
        decode(dataStore.data.first()[progressKey(animeId)])[chapterKey]

    fun readerModeFlow(): Flow<MangaReaderMode> = dataStore.data
        .map { preferences ->
            when (preferences[READER_MODE_KEY]) {
                MangaReaderMode.Webtoon.name -> MangaReaderMode.Webtoon
                else -> MangaReaderMode.Paged
            }
        }
        .distinctUntilChanged()

    suspend fun setReaderMode(mode: MangaReaderMode) {
        dataStore.edit { it[READER_MODE_KEY] = mode.name }
    }

    suspend fun saveProgress(
        animeId: String,
        chapterKey: String,
        pageIndex: Int,
        pageCount: Int,
    ) {
        if (pageCount <= 0) return
        val page = pageIndex.coerceIn(0, pageCount - 1)
        val preferenceKey = progressKey(animeId)
        writeMutex.withLock {
            dataStore.edit { preferences ->
                val current = decode(preferences[preferenceKey]).toMutableMap()
                current[chapterKey] = ChapterReadingProgress(
                    pageIndex = page,
                    pageCount = pageCount,
                    read = current[chapterKey]?.read == true || page >= pageCount - 1,
                    updatedAt = System.currentTimeMillis(),
                )
                preferences[preferenceKey] = encode(current)
            }
        }
    }

    /** Явная отметка «прочитано/не прочитано» из списка глав. */
    suspend fun setRead(animeId: String, chapterKey: String, read: Boolean, pageCount: Int) {
        val preferenceKey = progressKey(animeId)
        writeMutex.withLock {
            dataStore.edit { preferences ->
                val current = decode(preferences[preferenceKey]).toMutableMap()
                val count = pageCount.takeIf { it > 0 } ?: current[chapterKey]?.pageCount ?: 0
                if (read) {
                    current[chapterKey] = ChapterReadingProgress(
                        pageIndex = (count - 1).coerceAtLeast(0),
                        pageCount = count,
                        read = true,
                        updatedAt = System.currentTimeMillis(),
                    )
                } else {
                    current.remove(chapterKey)
                }
                preferences[preferenceKey] = encode(current)
            }
        }
    }

    private fun decode(raw: String?): Map<String, ChapterReadingProgress> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<ReadingSnapshot>(raw).entries.associate { it.chapterKey to it.value }
        }.getOrElse { emptyMap() }
    }

    private fun encode(value: Map<String, ChapterReadingProgress>): String =
        json.encodeToString(
            ReadingSnapshot(
                entries = value.entries
                    .sortedBy { it.key }
                    .map { ReadingEntry(it.key, it.value) },
            ),
        )

    private fun progressKey(animeId: String) =
        stringPreferencesKey("manga_progress_${stableSuffix(animeId)}")

    private companion object {
        val READER_MODE_KEY = stringPreferencesKey("manga_reader_mode")
    }

    private fun stableSuffix(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
}
