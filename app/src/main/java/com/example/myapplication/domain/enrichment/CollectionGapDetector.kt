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
        val all = localDataSource.getAllAnimeList().filter { it.mediaType == MediaType.ANIME }
        if (all.isEmpty()) return emptyList()

        val needTitleEn = localDataSource.getAnimeNeedingTitleEn(Int.MAX_VALUE).mapTo(HashSet()) { it.id }
        val needTitleRu = localDataSource.getAnimeNeedingTitleRu(Int.MAX_VALUE).mapTo(HashSet()) { it.id }

        val result = ArrayList<AnimeGap>()
        for (anime in all) {
            val fieldKinds = repairUseCase.detectGaps(anime).toGapKinds()
            val journaled = if (fieldKinds.isEmpty()) emptySet() else journal.activeFieldGaps(anime.id, now)
            val kinds = LinkedHashSet<GapKind>()
            kinds += fieldKinds - journaled
            if (anime.id in needTitleEn) kinds += GapKind.TITLE_EN
            if (anime.id in needTitleRu) kinds += GapKind.TITLE_RU
            if (kinds.isNotEmpty()) result += AnimeGap(anime.id, kinds)
        }
        return result
    }

    private fun RepairAnimeDbUseCase.FieldGaps.toGapKinds(): Set<GapKind> = buildSet {
        if (missingImage) add(GapKind.IMAGE)
        if (missingTags) add(GapKind.TAGS)
        if (missingRating) add(GapKind.RATING)
        if (missingExternalId) add(GapKind.EXTERNAL_ID)
        if (missingCategoryType) add(GapKind.CATEGORY_TYPE)
        if (missingEpisodes) add(GapKind.EPISODES)
    }
}
