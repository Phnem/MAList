package com.example.myapplication.domain.titles

import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.network.EnrichedTitles
import com.example.myapplication.sync.TitleMatcher

/**
 * Обогащение английского названия одной записи через внешние API.
 *
 * Каскад: AniList по id/idMal → MAL/Jikan по id → AniList по поиску (с матчингом названия) →
 * MAL/Jikan по поиску. Первый явный english побеждает. Попутно бэкфилл внешних id.
 * Результат пишется в БД: [AnimeLocalDataSource.setTitleEn] (нашли) или
 * [AnimeLocalDataSource.markTitleEnChecked] (не нашли — чтобы не перепроверять бесконечно;
 * дальше подхватит AI-дубляж на Stage 8).
 */
class TitleEnrichmentUseCase(
    private val repository: AnimeRepository,
    private val localDataSource: AnimeLocalDataSource,
) {
    sealed interface Outcome {
        data class Enriched(val english: String) : Outcome
        data object NoEnglishFound : Outcome
        data object Skipped : Outcome
    }

    /**
     * @param markOnFailure когда english не найден, помечать запись как «проверено»
     *  ([AnimeLocalDataSource.markTitleEnChecked]). Дубляж (Stage 8) вызывает с `false`,
     *  чтобы затем попробовать AI до выхода записи из очереди.
     */
    suspend fun enrich(anime: Anime, markOnFailure: Boolean = true): Outcome {
        if (anime.mediaType != MediaType.ANIME) return Outcome.Skipped
        if (!anime.titleEn.isNullOrBlank()) return Outcome.Skipped

        val resolved = resolve(anime)
        if (resolved != null) persistIds(anime, resolved)

        val english = resolved?.englishOrNull
        return if (english != null) {
            localDataSource.setTitleEn(anime.id, english)
            Outcome.Enriched(english)
        } else {
            if (markOnFailure) localDataSource.markTitleEnChecked(anime.id)
            Outcome.NoEnglishFound
        }
    }

    private suspend fun resolve(anime: Anime): EnrichedTitles? {
        // Поиск english строго по внешним id (shikimori_id != mal_id, поэтому подмену не делаем —
        // реальный mal_id проставляет «Исправить БД» через Shikimori detail (myanimelist_id)).
        val idAttempts = buildList {
            if (anime.anilistId != null || anime.malId != null) {
                repository.enrichTitlesByIds(anime.anilistId, anime.malId).getOrNull()?.let { add(it) }
            }
            if (anime.malId != null) {
                repository.malTitlesById(anime.malId).getOrNull()?.let { add(it) }
            }
        }
        idAttempts.firstOrNull { it.englishOrNull != null }?.let { return it }

        val aniMatch = pickBestMatch(anime.title, repository.enrichTitlesBySearch(anime.title).getOrNull().orEmpty())
        if (aniMatch?.englishOrNull != null) return aniMatch

        val malMatch = pickBestMatch(anime.title, repository.malTitlesBySearch(anime.title).getOrNull().orEmpty())
        if (malMatch?.englishOrNull != null) return malMatch

        // English не найден нигде — вернём кандидата с внешними id (для бэкфилла), если он есть.
        return idAttempts.firstOrNull() ?: aniMatch ?: malMatch
    }

    private suspend fun persistIds(anime: Anime, resolved: EnrichedTitles) {
        val resolvedAnilistId = resolved.anilistId
        if (anime.anilistId == null && resolvedAnilistId != null) {
            localDataSource.setAnilistId(anime.id, resolvedAnilistId)
        }
        val resolvedMalId = resolved.malId
        if (anime.malId == null && resolvedMalId != null) {
            localDataSource.setMalId(anime.id, resolvedMalId)
        }
    }

    private fun pickBestMatch(localTitle: String, candidates: List<EnrichedTitles>): EnrichedTitles? {
        if (candidates.isEmpty()) return null
        val byScore = candidates
            .map { it to TitleMatcher.bestScore(localTitle, it.matchCandidates()) }
            .filter { it.second >= TitleMatcher.MATCH_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
        if (byScore != null) return byScore

        // Кросс-скрипт: локальный RU-title vs english/romaji кандидаты даёт score ≈ 0.
        // Берём лучший ответ API с непустым english (ранг поиска уже релевантен запросу).
        if (containsCyrillic(localTitle)) {
            return candidates.firstOrNull { it.englishOrNull != null }
                ?: candidates.firstOrNull()
        }
        return null
    }

    private fun containsCyrillic(text: String): Boolean =
        text.any { it in '\u0400'..'\u04FF' }
}
