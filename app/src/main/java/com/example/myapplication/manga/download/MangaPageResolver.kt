package com.example.myapplication.manga.download

import com.example.myapplication.manga.data.MangaDownloadStore
import com.example.myapplication.manga.domain.MangaChapter
import com.example.myapplication.manga.domain.MangaPage
import com.example.myapplication.manga.source.MangaSourceEngine

/**
 * Откуда ридер берёт страницы: сначала диск, потом источник.
 *
 * Отдельный класс, а не ветка внутри ридера, потому что «скачано» — это свойство хранилища, а не
 * состояние экрана: скачанная глава читается офлайн одинаково, откуда бы её ни открыли.
 */
class MangaPageResolver(
    private val engine: MangaSourceEngine,
    private val downloadStore: MangaDownloadStore,
) {
    suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        downloadStore.ensureLoaded()
        return downloadStore.localPages(chapter) ?: engine.pages(chapter)
    }
}
