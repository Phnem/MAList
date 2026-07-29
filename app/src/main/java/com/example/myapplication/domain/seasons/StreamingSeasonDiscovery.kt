package com.example.myapplication.domain.seasons

import android.util.Log
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.local.SeasonEpisodesStore
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.media.source.AnimeHeavenSource
import com.example.myapplication.media.source.JutSuSource
import com.example.myapplication.media.source.KodikDirectSearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/** Сезон, найденный у источника просмотра: номер и сколько серий там реально лежит. */
data class DiscoveredSeason(
    val seasonNumber: Int,
    val episodes: Int,
    /** Имя источника — попадёт в [SeasonInfo.source] и в подпись сезона. */
    val source: String,
)

/**
 * «Найти ещё»: добрать сезоны из источников ПРОСМОТРА, когда каталожные API их не знают.
 *
 * [SeasonEpisodesResolver] строит цепочку сезонов по графу франшизы AniList — это точно и
 * аккуратно, но упирается в то, что AniList считает сезоном. Сплошь и рядом у тайтла в каталоге
 * два «сезона», а плееры раздают пять: продолжения, у которых своя запись в каталоге не связана
 * рёбрами PREQUEL/SEQUEL, вообще нет, или франшиза разложена иначе. Источники просмотра при этом
 * знают ровно то, что можно посмотреть, — поэтому здесь мы спрашиваем именно их.
 *
 * Запускается ТОЛЬКО по кнопке. Это несколько поисковых запросов и разбор HTML на каждый источник:
 * в фоновом проходе по всей коллекции такое стоило бы сотен запросов ради тайтлов, у которых и так
 * всё сошлось.
 *
 * Результат сливается с уже известным раскладом (см. [SeasonEpisodesResolver.mergeStreamingSeasons])
 * и переживает последующие фоновые перерезолвы: иначе первый же проход по API стёр бы найденное.
 */
class StreamingSeasonDiscovery(
    private val kodikDirectSearch: KodikDirectSearch,
    private val jutSuSource: JutSuSource,
    private val animeHeavenSource: AnimeHeavenSource,
    private val localDataSource: AnimeLocalDataSource,
    private val store: SeasonEpisodesStore,
) {

    /** Чем закончился поиск — ровно то, что нужно показать пользователю одной строкой. */
    sealed interface Outcome {
        /** Найдены новые сезоны и/или у известных прибавилось серий. */
        data class Updated(val seasons: List<SeasonInfo>, val addedSeasons: Int, val addedEpisodes: Int) : Outcome
        /** Источники ответили, но нового не нашлось. */
        data object NothingNew : Outcome
        /** Ни один источник не отдал ни одного сезона (тайтла у них нет либо сеть легла). */
        data object NotFound : Outcome
    }

    suspend fun discover(animeId: String): Outcome {
        store.ensureLoaded()
        val anime = localDataSource.getAnimeById(animeId) ?: return Outcome.NotFound
        if (anime.mediaType != MediaType.ANIME) return Outcome.NotFound

        val discovered = queryAllSources(anime)
        if (discovered.isEmpty()) {
            Log.i(TAG, "No seasons from streaming sources for \"${anime.title}\"")
            return Outcome.NotFound
        }

        val known = store.entryFor(animeId)?.seasons.orEmpty()
        val merged = merge(known, discovered)
        val addedSeasons = merged.size - known.size
        val addedEpisodes = merged.sumOf { it.episodes } - known.sumOf { it.episodes }
        if (addedSeasons <= 0 && addedEpisodes <= 0) {
            Log.i(TAG, "Streaming sources agree with the catalogue for \"${anime.title}\"")
            return Outcome.NothingNew
        }

        val previous = store.entryFor(animeId)
        store.put(
            SeasonEpisodesEntry(
                animeId = animeId,
                seasons = merged,
                // Найденное сверх каталога — не повод считать расклад окончательным: фоновый
                // проход должен и дальше дотягивать номера серий у онгоингов.
                complete = previous?.complete == true && merged.none { it.ongoing },
                resolvedAt = System.currentTimeMillis(),
                schema = SeasonEpisodesEntry.CURRENT_SCHEMA,
            )
        )
        Log.i(
            TAG,
            "\"${anime.title}\": +$addedSeasons season(s), +$addedEpisodes episode(s) " +
                "→ ${merged.map { "S${it.seasonNumber}=${it.episodes}(${it.source})" }}",
        )
        return Outcome.Updated(merged, addedSeasons, addedEpisodes)
    }

    /**
     * Источники опрашиваются параллельно и под общим таймаутом: они независимы, а самый медленный
     * из них не должен держать кнопку. Упавший просто выпадает из выдачи — как в `SourceEngine`.
     */
    private suspend fun queryAllSources(anime: Anime): List<DiscoveredSeason> = supervisorScope {
        listOf(
            "Kodik" to suspend { kodikDirectSearch.findSeasons(anime) },
            "jut.su" to suspend { jutSuSource.findSeasons(anime) },
            "AnimeHeaven" to suspend { animeHeavenSource.findSeasons(anime) },
        ).map { (label, block) ->
            async {
                runCatching { withTimeoutOrNull(SOURCE_TIMEOUT_MS) { block() } }
                    .onFailure { Log.i(TAG, "$label season discovery failed: ${it.message}") }
                    .getOrNull()
                    .orEmpty()
            }
        }.awaitAll().flatten()
    }

    /**
     * Слияние каталожного расклада с найденным у источников.
     *
     * Число серий берём МАКСИМАЛЬНОЕ: источники расходятся (у одного залита половина сезона, у
     * другого весь), и занизить его хуже, чем завысить, — недостающая серия в меню просто не
     * зарезолвится, а вот отсутствующая не покажется вовсе. Метаданные (id, ongoing, заявленный
     * итог) остаются каталожными: у источников просмотра их нет.
     */
    private fun merge(known: List<SeasonInfo>, discovered: List<DiscoveredSeason>): List<SeasonInfo> {
        val byNumber = known.associateByTo(LinkedHashMap()) { it.seasonNumber }
        for (season in discovered) {
            if (season.seasonNumber <= 0 || season.episodes <= 0) continue
            val existing = byNumber[season.seasonNumber]
            byNumber[season.seasonNumber] = when {
                existing == null -> SeasonInfo(
                    seasonNumber = season.seasonNumber,
                    episodes = season.episodes,
                    source = season.source,
                )
                season.episodes > existing.episodes ->
                    existing.copy(episodes = season.episodes, source = season.source)
                else -> existing
            }
        }
        return byNumber.values.sortedBy { it.seasonNumber }
    }

    companion object {
        private const val TAG = "SeasonDiscovery"
        private const val SOURCE_TIMEOUT_MS = 25_000L

        /**
         * Значения [SeasonInfo.source], означающие «сезон пришёл от источника просмотра».
         * По ним резолвер отличает найденное вручную от собственного результата и не затирает его.
         */
        val STREAMING_SOURCES = setOf("Kodik", "jut.su", "AnimeHeaven")
    }
}
