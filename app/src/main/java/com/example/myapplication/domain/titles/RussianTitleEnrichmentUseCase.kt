package com.example.myapplication.domain.titles

import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.network.EnrichedTitles
import com.example.myapplication.sync.TitleMatcher

/**
 * Обратное обогащение русского названия через Shikimori.
 *
 * Каскад: по [Anime.shikimoriId] → поиск по [Anime.titleEn] (предпочтительно) /
 * [Anime.title] с матчингом. Первый непустой russian побеждает. Попутно бэкфилл
 * `shikimori_id`. Результат: [AnimeLocalDataSource.setTitleRu] или
 * [AnimeLocalDataSource.markTitleRuChecked].
 */
class RussianTitleEnrichmentUseCase(
    private val repository: AnimeRepository,
    private val localDataSource: AnimeLocalDataSource,
) {
    sealed interface Outcome {
        data class Enriched(val russian: String) : Outcome
        data object NoRussianFound : Outcome
        data object Skipped : Outcome
    }

    suspend fun enrich(anime: Anime, markOnFailure: Boolean = true): Outcome {
        if (anime.mediaType != MediaType.ANIME) return Outcome.Skipped
        if (!anime.titleRu.isNullOrBlank()) return Outcome.Skipped

        // Локальный title уже на кириллице — принимаем его без сети (типичный RU-каталог).
        val localCyrillic = anime.title.takeIf { containsCyrillic(it) }?.trim()
        if (!localCyrillic.isNullOrEmpty()) {
            localDataSource.setTitleRu(anime.id, localCyrillic)
            return Outcome.Enriched(localCyrillic)
        }

        val resolved = resolve(anime)
        if (resolved != null) persistShikimoriId(anime, resolved)

        val russian = resolved?.russianOrNull
        return if (russian != null) {
            localDataSource.setTitleRu(anime.id, russian)
            Outcome.Enriched(russian)
        } else {
            if (markOnFailure) localDataSource.markTitleRuChecked(anime.id)
            Outcome.NoRussianFound
        }
    }

    private suspend fun resolve(anime: Anime): EnrichedTitles? {
        anime.shikimoriId?.let { id ->
            repository.russianTitleByShikimoriId(id).getOrNull()?.let { hit ->
                if (hit.russianOrNull != null) return hit
            }
        }

        val queries = listOfNotNull(
            anime.titleEn?.takeIf { it.isNotBlank() },
            anime.title.takeIf { it.isNotBlank() },
        ).distinct()

        for (query in queries) {
            val match = pickBestMatch(
                localTitle = query,
                candidates = repository.russianTitlesBySearch(query).getOrNull().orEmpty(),
            )
            if (match?.russianOrNull != null) return match
            if (match != null) return match // id-бэкфилл без russian
        }
        return null
    }

    private suspend fun persistShikimoriId(anime: Anime, resolved: EnrichedTitles) {
        val id = resolved.shikimoriId ?: return
        if (anime.shikimoriId == null) {
            localDataSource.setShikimoriId(anime.id, id)
        }
    }

    private fun pickBestMatch(localTitle: String, candidates: List<EnrichedTitles>): EnrichedTitles? =
        candidates
            .map { it to TitleMatcher.bestScore(localTitle, it.matchCandidates()) }
            .filter { it.second >= TitleMatcher.MATCH_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first

    private fun containsCyrillic(text: String): Boolean =
        text.any { it in '\u0400'..'\u04FF' }
}
