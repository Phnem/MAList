package com.example.myapplication.domain.enrichment

/**
 * Разновидности «пробелов» записи, которые закрывает обогащение коллекции.
 *
 * Полевые (IMAGE…EPISODES) детектятся через
 * [com.example.myapplication.domain.settings.RepairAnimeDbUseCase.detectGaps] и, если закрыть не удалось,
 * пишутся в файловый [EnrichmentGapJournal] (чтобы не долбить API впустую до истечения TTL).
 * Названия (TITLE_EN/TITLE_RU) journal-семантику уже несут в БД через `title_*_checked_at`
 * (см. `selectNeedingTitleEn`/`selectNeedingTitleRu`) — их в файловый журнал не пишем.
 */
enum class GapKind {
    IMAGE,
    TAGS,
    RATING,
    EXTERNAL_ID,
    CATEGORY_TYPE,
    EPISODES,
    TITLE_EN,
    TITLE_RU;

    val isFieldGap: Boolean get() = this != TITLE_EN && this != TITLE_RU
}

/** Одна запись коллекции с непустым набором пробелов (после вычета журнала). */
data class AnimeGap(
    val animeId: String,
    val kinds: Set<GapKind>,
) {
    /** Требует ли остаток AI-перевода (титул EN не нашёлся бесплатным API). */
    val needsTitleEn: Boolean get() = GapKind.TITLE_EN in kinds
}
