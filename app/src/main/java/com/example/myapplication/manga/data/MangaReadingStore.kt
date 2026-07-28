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

/**
 * Как листается ридер. Настройка **на тайтл**: в одной коллекции соседствуют вебтуны и обычная
 * манга, и один глобальный переключатель заставлял бы менять режим при каждом открытии.
 */
@Serializable
enum class MangaReaderMode {
    /** Постранично, свайпом влево/вправо — обычная манга. */
    Paged,

    /** Непрерывная вертикальная лента — вебтуны и длинные полосы. */
    Webtoon,
}

/**
 * Направление листания страниц — отдельное от [MangaReaderMode] измерение: осмысленно только в
 * [MangaReaderMode.Paged], вертикальной ленте направление не нужно.
 */
@Serializable
enum class PageDirection {
    /** «Классика»: справа налево, как в японской манге. */
    Rtl,

    /** «Комикс»: слева направо, как в западных комиксах и манхве в переводе. */
    Ltr,
}

@Serializable
private data class ReadingEntry(val chapterKey: String, val value: ChapterReadingProgress)

@Serializable
private data class ReadingSnapshot(
    val entries: List<ReadingEntry> = emptyList(),
    /** `null` = per-title значение не выбрано, берём глобальный легаси-дефолт. */
    val mode: MangaReaderMode? = null,
    /** `null` = направление не выбрано, берём [DEFAULT_DIRECTION]. */
    val direction: PageDirection? = null,
) {
    fun progress(): Map<String, ChapterReadingProgress> = entries.associate { it.chapterKey to it.value }

    fun withProgress(value: Map<String, ChapterReadingProgress>): ReadingSnapshot = copy(
        entries = value.entries.sortedBy { it.key }.map { ReadingEntry(it.key, it.value) },
    )
}

/**
 * Дефолт направления: автодетект невозможен — в модели тайтла нет ни страны, ни языка оригинала,
 * так что берём самый частый для манги вариант, а дальше читатель переключит вручную.
 */
private val DEFAULT_DIRECTION = PageDirection.Rtl

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

    /**
     * Режим тайтла: сначала per-title снимок, иначе старая глобальная настройка, иначе Paged.
     * Легаси-ключ читается (но больше не пишется), чтобы тайтлы, открытые до разделения настройки,
     * не перескочили на другой режим.
     */
    fun readerModeFlow(animeId: String): Flow<MangaReaderMode> {
        val key = progressKey(animeId)
        return dataStore.data
            .map { preferences -> decodeSnapshot(preferences[key]).mode ?: legacyMode(preferences) }
            .distinctUntilChanged()
    }

    /**
     * Выбран ли режим у ЭТОГО тайтла. По [readerModeFlow] это не видно: он уже подмешивает дефолт,
     * и «пользователь выбрал Paged» неотличимо от «никто ничего не выбирал». Нужно автодетекту —
     * он имеет право высказаться только там, где выбора ещё не было.
     */
    suspend fun hasExplicitMode(animeId: String): Boolean =
        decodeSnapshot(dataStore.data.first()[progressKey(animeId)]).mode != null

    suspend fun setReaderMode(animeId: String, mode: MangaReaderMode) {
        updateSnapshot(animeId) { it.copy(mode = mode) }
    }

    fun directionFlow(animeId: String): Flow<PageDirection> {
        val key = progressKey(animeId)
        return dataStore.data
            .map { preferences -> decodeSnapshot(preferences[key]).direction ?: DEFAULT_DIRECTION }
            .distinctUntilChanged()
    }

    suspend fun setDirection(animeId: String, direction: PageDirection) {
        updateSnapshot(animeId) { it.copy(direction = direction) }
    }

    suspend fun saveProgress(
        animeId: String,
        chapterKey: String,
        pageIndex: Int,
        pageCount: Int,
    ) {
        if (pageCount <= 0) return
        val page = pageIndex.coerceIn(0, pageCount - 1)
        updateSnapshot(animeId) { snapshot ->
            val current = snapshot.progress().toMutableMap()
            current[chapterKey] = ChapterReadingProgress(
                pageIndex = page,
                pageCount = pageCount,
                read = current[chapterKey]?.read == true || page >= pageCount - 1,
                updatedAt = System.currentTimeMillis(),
            )
            snapshot.withProgress(current)
        }
    }

    /** Явная отметка «прочитано/не прочитано» из списка глав. */
    suspend fun setRead(animeId: String, chapterKey: String, read: Boolean, pageCount: Int) {
        updateSnapshot(animeId) { snapshot ->
            val current = snapshot.progress().toMutableMap()
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
            snapshot.withProgress(current)
        }
    }

    /**
     * Единственная точка записи снимка: прогресс и настройки ридера лежат в одном JSON, поэтому
     * любая правка обязана читать снимок целиком — иначе сохранение страницы стирало бы режим.
     */
    private suspend fun updateSnapshot(animeId: String, transform: (ReadingSnapshot) -> ReadingSnapshot) {
        val preferenceKey = progressKey(animeId)
        writeMutex.withLock {
            dataStore.edit { preferences ->
                val snapshot = decodeSnapshot(preferences[preferenceKey])
                preferences[preferenceKey] = json.encodeToString(transform(snapshot))
            }
        }
    }

    private fun legacyMode(preferences: Preferences): MangaReaderMode =
        when (preferences[READER_MODE_KEY]) {
            MangaReaderMode.Webtoon.name -> MangaReaderMode.Webtoon
            else -> MangaReaderMode.Paged
        }

    private fun decodeSnapshot(raw: String?): ReadingSnapshot {
        if (raw.isNullOrBlank()) return ReadingSnapshot()
        return runCatching { json.decodeFromString<ReadingSnapshot>(raw) }.getOrElse { ReadingSnapshot() }
    }

    private fun decode(raw: String?): Map<String, ChapterReadingProgress> = decodeSnapshot(raw).progress()

    private fun progressKey(animeId: String) =
        stringPreferencesKey("manga_progress_${stableSuffix(animeId)}")

    private companion object {
        /**
         * Легаси-настройка «режим на всё приложение». Только чтение: она остаётся дефолтом для
         * тайтлов, у которых per-title режим ещё не выбран, поэтому удалять ключ нельзя.
         */
        val READER_MODE_KEY = stringPreferencesKey("manga_reader_mode")
    }

    private fun stableSuffix(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
}
