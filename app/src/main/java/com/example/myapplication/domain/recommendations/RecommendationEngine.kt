package com.example.myapplication.domain.recommendations

import android.util.Log
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.local.RecommendationCacheStore
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.domain.search.mapApiGenresToTagIds
import com.example.myapplication.network.ApiService
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.supervisorScope

private const val TAG = "RecommendationEngine"

/**
 * Оркестратор рекомендаций без ИИ: related-графы источников + взвешенный жанровый вектор.
 *
 * Оптимизации:
 *  - AniList: related для всех сидов ОДНИМ батч-запросом (id_in) — одна сетевая поездка
 *    покрывает большинство сидов;
 *  - fallback-источники (Shikimori similar / Jikan recommendations) — параллельно
 *    в supervisorScope: падение одного источника не валит пересчёт;
 *  - single-flight: конкурирующие вызовы refresh() схлопываются в один пересчёт;
 *  - stale-while-revalidate: cached() отдаёт снапшот мгновенно (даже протухший),
 *    вызывающий решает, запускать ли фоновый refresh().
 */
class RecommendationEngine(
    private val apiService: ApiService,
    private val localDataSource: AnimeLocalDataSource,
    private val genreRepository: GenreRepository,
    private val cache: RecommendationCacheStore,
    private val affinityCalculator: GenreAffinityCalculator = GenreAffinityCalculator(),
    private val filter: RecommendationFilter = RecommendationFilter(),
) {

    private val refreshMutex = Mutex()

    /** Мгновенное чтение кэша — БЕЗ сети. null, если пересчёта ещё не было. */
    suspend fun cached(): RecommendationsSnapshot? = cache.read()

    /** Кэш валиден: не протух по TTL, библиотека не менялась и язык совпадает. */
    fun isFresh(snapshot: RecommendationsSnapshot?, library: List<Anime>, language: AppLanguage): Boolean =
        snapshot != null && !cache.isStale(snapshot) &&
            snapshot.librarySignature == librarySignature(library) &&
            snapshot.language == language.name

    /**
     * Пересчёт (если нужен). Возвращает актуальный снапшот либо null, когда все
     * источники недоступны и старого кэша нет. При полном отказе сети старый
     * кэш НЕ затирается.
     */
    suspend fun refresh(language: AppLanguage, force: Boolean = false): RecommendationsSnapshot? =
        refreshMutex.withLock {
            val library = localDataSource.getAllAnimeList()
            val existing = cache.read()
            if (!force && isFresh(existing, library, language)) return@withLock existing

            val computed = runCatching { compute(library, language) }
                .onFailure { Log.w(TAG, "Recommendations compute failed", it) }
                .getOrNull()

            if (computed != null && computed.items.isNotEmpty()) {
                cache.write(computed)
                computed
            } else {
                existing
            }
        }

    private suspend fun compute(library: List<Anime>, language: AppLanguage): RecommendationsSnapshot {
        val strategy = resolveStrategy(library)
        val pool = when (strategy) {
            is RecommendationStrategy.Onboarding -> buildTrendingPool()
            is RecommendationStrategy.PopularitySort,
            is RecommendationStrategy.WeightedVector -> buildRelatedPool(library, language)
                .ifEmpty { buildTrendingPool() }
        }

        val filtered = filter.filter(pool, library)
        val affinity = affinityCalculator.calculate(library)
        val scorer = RecommendationScorer(
            affinity = affinity,
            genreToTagId = { genre -> mapApiGenresToTagIds(listOf(genre), genreRepository).firstOrNull() },
            preferredSource = if (language == AppLanguage.RU) "Shikimori" else "AniList",
        )
        val items = scorer.scoreAndRank(filtered, limit = MAX_RESULTS)

        return RecommendationsSnapshot(
            items = items,
            computedAtMillis = System.currentTimeMillis(),
            librarySignature = librarySignature(library),
            confidence = when (strategy) {
                is RecommendationStrategy.WeightedVector -> strategy.confidence
                else -> 0f
            },
            isColdStart = strategy is RecommendationStrategy.Onboarding,
            language = language.name,
        )
    }

    // ---- Сиды и стратегия ----------------------------------------------------

    fun resolveStrategy(library: List<Anime>): RecommendationStrategy {
        val seedable = library.filter { it.hasExternalId() }
        val ratedCount = library.count { it.rating > 0 }
        return when {
            seedable.isEmpty() -> RecommendationStrategy.Onboarding
            ratedCount == 0 -> RecommendationStrategy.PopularitySort
            else -> RecommendationStrategy.WeightedVector(
                confidence = (ratedCount / 10f).coerceIn(0f, 1f)
            )
        }
    }

    /**
     * Сиды: лучшие по пользовательскому рейтингу тайтлы с внешними id
     * (избранное — выше при равном рейтинге, свежие — выше старых).
     */
    private fun resolveSeeds(library: List<Anime>): List<Anime> =
        library.asSequence()
            .filter { it.hasExternalId() }
            .sortedWith(
                compareByDescending<Anime> { it.rating }
                    .thenByDescending { it.isFavorite }
                    .thenByDescending { it.dateAdded }
            )
            .take(MAX_SEEDS)
            .toList()

    private fun Anime.hasExternalId(): Boolean =
        (anilistId != null || shikimoriId != null || malId != null) && mediaType == MediaType.ANIME

    // ---- Сбор пула -----------------------------------------------------------

    private suspend fun buildRelatedPool(library: List<Anime>, language: AppLanguage): List<PoolEntry> {
        val seeds = resolveSeeds(library)
        if (seeds.isEmpty()) return emptyList()

        // RU: первичный источник — Shikimori (русские тайтлы/жанры), AniList добирает
        // co-occurrence и сиды без shikimoriId. EN: наоборот — AniList батч первичен.
        val shikimoriSeeds: List<Anime>
        val anilistSeeds: List<Anime>
        if (language == AppLanguage.RU) {
            shikimoriSeeds = seeds.filter { it.shikimoriId != null }
            anilistSeeds = seeds.filter { it.anilistId != null }
        } else {
            anilistSeeds = seeds.filter { it.anilistId != null }
            shikimoriSeeds = seeds.filter { it.anilistId == null && it.shikimoriId != null }.take(MAX_FALLBACK_SEEDS)
        }
        val malSeeds = seeds
            .filter { it.anilistId == null && it.shikimoriId == null && it.malId != null }
            .take(MAX_FALLBACK_SEEDS)

        return supervisorScope {
            val anilistDeferred = async {
                if (anilistSeeds.isEmpty()) return@async emptyList()
                val byId = anilistSeeds.associateBy { it.anilistId!! }
                apiService.anilistRecommendationsBatch(byId.keys.toList(), perSeed = PER_SEED)
                    .getOrElse {
                        Log.w(TAG, "AniList batch failed", it)
                        emptyMap()
                    }
                    .flatMap { (seedId, results) ->
                        val seed = byId[seedId] ?: return@flatMap emptyList()
                        results.map { PoolEntry(it, seed.title, seed.rating) }
                    }
            }

            val shikimoriDeferred = shikimoriSeeds.map { seed ->
                async {
                    apiService.shikimoriSimilar(seed.shikimoriId!!, language)
                        .getOrElse { emptyList() }
                        .take(PER_SEED)
                        .map { PoolEntry(it, seed.title, seed.rating) }
                }
            }

            val malDeferred = malSeeds.map { seed ->
                async {
                    apiService.malRecommendations(seed.malId!!)
                        .getOrElse { emptyList() }
                        .take(PER_SEED)
                        .map { PoolEntry(it, seed.title, seed.rating) }
                }
            }

            val pool = mutableListOf<PoolEntry>()
            pool += runCatching { anilistDeferred.await() }.getOrElse { emptyList() }
            shikimoriDeferred.forEach { d -> pool += runCatching { d.await() }.getOrElse { emptyList() } }
            malDeferred.forEach { d -> pool += runCatching { d.await() }.getOrElse { emptyList() } }
            pool
        }
    }

    private suspend fun buildTrendingPool(): List<PoolEntry> =
        apiService.anilistTrending(limit = TRENDING_LIMIT)
            .getOrElse { emptyList() }
            .map { PoolEntry(it, seedTitle = "", seedRating = 0f) }

    // ---- Сигнатура библиотеки --------------------------------------------------

    /** Дёшево и детерминированно: меняется при добавлении/удалении/оценке/избранном. */
    fun librarySignature(library: List<Anime>): String {
        val joined = library.asSequence()
            .map { "${it.id}:${it.rating}:${if (it.isFavorite) 1 else 0}" }
            .sorted()
            .joinToString("|")
        return "${library.size}#${joined.hashCode()}"
    }

    companion object {
        const val MAX_SEEDS = 6
        const val MAX_FALLBACK_SEEDS = 3
        const val PER_SEED = 12
        const val TRENDING_LIMIT = 24

        /**
         * Пул кэша шире, чем показывается за раз (см. сессионный сэмплинг во ViewModel):
         * каждая новая сессия достаёт из кэша другой срез — «что-то новое при каждом входе»
         * без лишней сети.
         */
        const val MAX_RESULTS = 40
    }
}
