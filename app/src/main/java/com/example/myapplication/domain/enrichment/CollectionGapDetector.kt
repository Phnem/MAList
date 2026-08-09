package com.example.myapplication.domain.enrichment

import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.domain.settings.RepairAnimeDbUseCase

/**
 * Единый «что не хватает по коллекции». Полевые пробелы берёт из
 * [RepairAnimeDbUseCase.detectGaps] (тот же код, что и полный проход) и вычитает уже помеченные
 * неразрешимыми в [EnrichmentGapJournal] (с учётом TTL). Пробелы названий — из БД-запросов
 * `selectNeedingTitleEn/Ru`, которые сами исключают записи с выставленным `title_*_checked_at`.
 *
 * Возвращает по записи на аниме с непустым набором активных пробелов; `size` списка — метрика
 * «сколько записей требует внимания» (порог перегрузки Live Maintenance).
 */
class CollectionGapDetector(
    private val localDataSource: AnimeLocalDataSource,
    private val repairUseCase: RepairAnimeDbUseCase,
    private val journal: EnrichmentGapJournal,
) {
    suspend fun scan(now: Long = System.currentTimeMillis()): List<AnimeGap> {
        val all = localDataSource.getAllAnimeList().filter { it.mediaType.isSupportedByFieldRepair() }
        if (all.isEmpty()) return emptyList()

        val needTitleEn = localDataSource.getAnimeNeedingTitleEn(Int.MAX_VALUE).mapTo(HashSet()) { it.id }
        val needTitleRu = localDataSource.getAnimeNeedingTitleRu(Int.MAX_VALUE).mapTo(HashSet()) { it.id }

        val result = ArrayList<AnimeGap>()
        for (anime in all) {
            val gaps = repairUseCase.detectGaps(anime, now)
            val fieldKinds = gaps.fieldKinds()
            val journaled = if (fieldKinds.isEmpty()) emptySet() else journal.activeFieldGaps(anime.id, now)
            val kinds = LinkedHashSet<GapKind>()
            kinds += fieldKinds - (journaled intersect gaps.journalFieldKinds())
            if (anime.id in needTitleEn) kinds += GapKind.TITLE_EN
            if (anime.id in needTitleRu) kinds += GapKind.TITLE_RU
            if (kinds.isNotEmpty()) result += AnimeGap(anime.id, kinds)
        }
        return result
    }

}

internal fun MediaType.isSupportedByFieldRepair(): Boolean =
    this == MediaType.ANIME || this == MediaType.MOVIE || this == MediaType.SERIES

internal fun RepairAnimeDbUseCase.FieldGaps.fieldKinds(): Set<GapKind> = buildSet {
    addAll(journalFieldKinds())
    if (missingTmdb || (missingKinopoisk && kinopoiskRetryable)) {
        add(GapKind.EXTERNAL_ID)
    }
}

/** Provider ids use DB-backed LookupResult timestamps; only legacy field gaps enter the file journal. */
internal fun RepairAnimeDbUseCase.FieldGaps.journalFieldKinds(): Set<GapKind> = buildSet {
    if (missingImage) add(GapKind.IMAGE)
    if (missingTags) add(GapKind.TAGS)
    if (missingRating) add(GapKind.RATING)
    if (missingAnimeExternalId) add(GapKind.EXTERNAL_ID)
    if (missingCategoryType) add(GapKind.CATEGORY_TYPE)
    if (missingEpisodes) add(GapKind.EPISODES)
}
