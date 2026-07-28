package com.example.myapplication.manga.domain

/**
 * Единственная граница между доменом манги и любым конкретным поставщиком глав/страниц.
 *
 * Всё, что выше этого интерфейса (ридер, привязки, прогресс чтения), не знает, откуда пришли
 * страницы: нативный клиент MangaDex, скрейпер или — если до этого дойдёт — адаптер над
 * расширением Mihon. Поэтому здесь нет ни `Observable`, ни okhttp-типов, ни nullable-мутабельных
 * моделей: только suspend и неизменяемые данные.
 */
interface VetroMangaSource {
    val id: MangaSourceId
    val name: String

    /** `ru`, `en` или `multi` для агрегаторов с несколькими языками перевода. */
    val language: String

    suspend fun search(query: String, page: Int = 1): MangaSearchPage

    suspend fun details(manga: MangaItem): MangaDetails

    /**
     * Все главы тайтла, включая переводы на разные языки и дубли от разных команд.
     * Фильтрация и выбор — задача слоя выше, источник ничего за пользователя не решает.
     */
    suspend fun chapters(manga: MangaItem): List<MangaChapter>

    suspend fun pages(chapter: MangaChapter): List<MangaPage>
}
