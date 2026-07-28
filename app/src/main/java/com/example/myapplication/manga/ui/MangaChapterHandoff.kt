package com.example.myapplication.manga.ui

import com.example.myapplication.manga.domain.MangaChapter

/**
 * Передача уже загруженного списка глав из Details в ридер, минуя Intent.
 *
 * Список глав легко переваливает за сотню записей — в Intent это и лишний риск
 * TransactionTooLargeException, и повторный запрос к источнику ради «следующей главы».
 * Тот же приём, что `VetroVideoCache` для потокового плеера: память процесса, короткая жизнь.
 */
object MangaChapterHandoff {

    @Volatile private var entry: Entry? = null

    private data class Entry(val animeId: String, val chapters: List<MangaChapter>)

    fun put(animeId: String, chapters: List<MangaChapter>) {
        entry = Entry(animeId, chapters)
    }

    fun chapters(animeId: String): List<MangaChapter> =
        entry?.takeIf { it.animeId == animeId }?.chapters.orEmpty()

    fun clear() {
        entry = null
    }
}
