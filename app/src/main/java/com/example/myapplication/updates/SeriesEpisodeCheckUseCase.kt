package com.example.myapplication.updates

import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.ExternalIds
import com.example.myapplication.network.LookupResult
import com.example.myapplication.network.movie.MovieSeriesRepository
import com.example.myapplication.network.tmdb.SeriesEpisodeState
import java.time.Clock

/** Tracks released TMDB episodes for SERIES without ever copying the known/planned count. */
class SeriesEpisodeCheckUseCase internal constructor(
    private val store: SeriesEpisodeStore,
    private val source: SeriesEpisodeSource,
    private val clock: Clock,
) {
    constructor(
        repository: MovieSeriesRepository,
        localDataSource: AnimeLocalDataSource,
        clock: Clock = Clock.systemUTC(),
    ) : this(
        store = LocalSeriesEpisodeStore(localDataSource),
        source = MovieSeriesEpisodeSource(repository),
        clock = clock,
    )

    /** Detects, auto-applies and merges new SERIES events into the shared anime_update feed. */
    suspend fun detectAndStore(): List<AnimeUpdate> {
        val series = store.getAllSeries()
        if (series.isEmpty()) return emptyList()

        val detected = buildList {
            for (stored in series) {
                val tmdbId = source.resolveTmdbId(stored) ?: continue
                val anime = if (tmdbId != stored.tmdbId) {
                    store.setTmdbId(stored.id, tmdbId)
                    stored.copy(tmdbId = tmdbId)
                } else {
                    stored
                }
                val state = when (val result = source.episodeState(tmdbId, clock)) {
                    is LookupResult.Found -> result.value
                    else -> continue
                }

                if (!store.isNormalized(anime.id)) {
                    store.normalize(anime, state.releasedEpisodes)
                    continue
                }
                if (state.releasedEpisodes > anime.episodes) {
                    add(
                        AnimeUpdate(
                            animeId = anime.id,
                            title = anime.title,
                            currentEpisodes = anime.episodes,
                            newEpisodes = state.releasedEpisodes,
                            source = "TMDB",
                        )
                    )
                }
            }
        }

        return publishEpisodeUpdates(
            detected = detected,
            getExisting = store::getUpdates,
            applyEpisodes = { update -> store.applyEpisodes(update.animeId, update.newEpisodes) },
            setUpdates = store::setUpdates,
        )
    }

}

internal interface SeriesEpisodeStore {
    fun getAllSeries(): List<Anime>
    fun isNormalized(animeId: String): Boolean
    fun getUpdates(): List<AnimeUpdate>
    suspend fun normalize(anime: Anime, releasedEpisodes: Int)
    suspend fun setTmdbId(animeId: String, tmdbId: Int)
    suspend fun applyEpisodes(animeId: String, releasedEpisodes: Int)
    suspend fun setUpdates(updates: List<AnimeUpdate>)
}

internal interface SeriesEpisodeSource {
    suspend fun resolveTmdbId(anime: Anime): Int?
    suspend fun episodeState(tmdbId: Int, clock: Clock): LookupResult<SeriesEpisodeState>
}

private class LocalSeriesEpisodeStore(
    private val localDataSource: AnimeLocalDataSource,
) : SeriesEpisodeStore {
    override fun getAllSeries(): List<Anime> =
        localDataSource.getAllAnimeList().filter { it.mediaType == MediaType.SERIES }

    override fun isNormalized(animeId: String): Boolean =
        localDataSource.isSeriesEpisodesNormalized(animeId)

    override fun getUpdates(): List<AnimeUpdate> = localDataSource.getUpdates()

    override suspend fun normalize(anime: Anime, releasedEpisodes: Int) {
        localDataSource.updateAnime(anime.copy(episodes = releasedEpisodes))
        localDataSource.markSeriesEpisodesNormalized(anime.id)
    }

    override suspend fun setTmdbId(animeId: String, tmdbId: Int) {
        localDataSource.setTmdbId(animeId, tmdbId)
    }

    override suspend fun applyEpisodes(animeId: String, releasedEpisodes: Int) {
        val anime = localDataSource.getAnimeById(animeId) ?: return
        if (releasedEpisodes > anime.episodes) {
            localDataSource.updateAnime(anime.copy(episodes = releasedEpisodes))
        }
    }

    override suspend fun setUpdates(updates: List<AnimeUpdate>) {
        localDataSource.setUpdates(updates)
    }
}

private class MovieSeriesEpisodeSource(
    private val repository: MovieSeriesRepository,
) : SeriesEpisodeSource {
    override suspend fun resolveTmdbId(anime: Anime): Int? = repository.resolveTmdbId(
        externalIds = ExternalIds(tmdb = anime.tmdbId, kinopoisk = anime.kinopoiskId),
        title = anime.title,
        contentType = AppContentType.SERIES,
        year = null,
    )

    override suspend fun episodeState(
        tmdbId: Int,
        clock: Clock,
    ): LookupResult<SeriesEpisodeState> = repository.episodeState(tmdbId, clock)
}
