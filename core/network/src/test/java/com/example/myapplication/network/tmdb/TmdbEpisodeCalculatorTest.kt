package com.example.myapplication.network.tmdb

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Главный инвариант всей MOVIE/SERIES-фичи (см. .scratch/movie-series-infra/spec.md):
 * released ≠ known. Season air_date — дата премьеры, не завершения; сама по себе она не
 * доказывает, что сезон закрыт. Эти тесты явно бьют по двум некорректным вариантам, отклонённым
 * в трёх раундах архитектурной критики плана.
 */
class TmdbEpisodeCalculatorTest {

    private val today = LocalDate.of(2026, 8, 9)

    @Test
    fun `known episodes sums all real seasons, ignores season 0 specials`() {
        val seasons = listOf(
            TmdbSeasonSummary(seasonNumber = 0, episodeCount = 3, airDate = null),
            TmdbSeasonSummary(seasonNumber = 1, episodeCount = 12, airDate = today.minusYears(2)),
            TmdbSeasonSummary(seasonNumber = 2, episodeCount = 10, airDate = today.minusMonths(1)),
        )
        assertEquals(22, TmdbEpisodeCalculator.knownEpisodes(seasons))
    }

    @Test
    fun `no started season released is zero`() {
        val seasons = listOf(
            TmdbSeasonSummary(seasonNumber = 1, episodeCount = 12, airDate = today.plusMonths(1)),
        )
        assertEquals(0, TmdbEpisodeCalculator.releasedEpisodes(seasons, null, today))
    }

    @Test
    fun `single started season without episode details is not counted at all`() {
        // Регресс-тест на главный баг черновиков плана: season air_date в прошлом сама по себе
        // НЕ доказывает, что сезон закрыт — без seasonDetails текущий сезон не досчитывается.
        val seasons = listOf(
            TmdbSeasonSummary(seasonNumber = 1, episodeCount = 12, airDate = today.minusMonths(2)),
        )
        assertEquals(0, TmdbEpisodeCalculator.releasedEpisodes(seasons, episodesOfLatestStartedSeason = null, today))
    }

    @Test
    fun `earlier seasons proven closed by a later started season count in full`() {
        val seasons = listOf(
            TmdbSeasonSummary(seasonNumber = 1, episodeCount = 12, airDate = today.minusYears(2)),
            TmdbSeasonSummary(seasonNumber = 2, episodeCount = 10, airDate = today.minusMonths(1)),
        )
        // Сезон 2 уже начал выходить -> сезон 1 доказанно закрыт, считается целиком (12).
        // Про сезон 2 (текущий) деталей нет -> он сам не досчитывается.
        assertEquals(12, TmdbEpisodeCalculator.releasedEpisodes(seasons, episodesOfLatestStartedSeason = null, today))
    }

    @Test
    fun `current season counts only episodes aired on or before today`() {
        val seasons = listOf(
            TmdbSeasonSummary(seasonNumber = 1, episodeCount = 12, airDate = today.minusYears(2)),
            TmdbSeasonSummary(seasonNumber = 2, episodeCount = 10, airDate = today.minusMonths(1)),
        )
        val season2Episodes = listOf(
            TmdbEpisodeAirDate(1, today.minusDays(21)),
            TmdbEpisodeAirDate(2, today.minusDays(14)),
            TmdbEpisodeAirDate(3, today.minusDays(7)),
            TmdbEpisodeAirDate(4, today.plusDays(7)), // ещё не вышла
        )
        // Сезон 1 закрыт (12) + сезон 2: 3 вышедших эпизода из 4 заявленных.
        assertEquals(15, TmdbEpisodeCalculator.releasedEpisodes(seasons, season2Episodes, today))
    }

    @Test
    fun `future season known but not started contributes nothing`() {
        val seasons = listOf(
            TmdbSeasonSummary(seasonNumber = 1, episodeCount = 12, airDate = today.minusYears(1)),
            TmdbSeasonSummary(seasonNumber = 2, episodeCount = 10, airDate = today.plusMonths(3)),
        )
        // Сезон 2 объявлен (known=22), но ещё не начался -> released считает только сезон 1.
        assertEquals(22, TmdbEpisodeCalculator.knownEpisodes(seasons))
        assertEquals(0, TmdbEpisodeCalculator.releasedEpisodes(seasons, null, today))
    }

    @Test
    fun `status maps known TMDB strings`() {
        assertEquals(SeriesStatus.ENDED, TmdbEpisodeCalculator.status("Ended", inProduction = false))
        assertEquals(SeriesStatus.CANCELLED, TmdbEpisodeCalculator.status("Canceled", inProduction = false))
        assertEquals(SeriesStatus.ONGOING, TmdbEpisodeCalculator.status("Returning Series", inProduction = true))
        assertEquals(SeriesStatus.UPCOMING, TmdbEpisodeCalculator.status("Planned", inProduction = false))
        assertEquals(SeriesStatus.UNKNOWN, TmdbEpisodeCalculator.status(null, inProduction = false))
    }

    @Test
    fun `in production status does not by itself mean ongoing`() {
        // Между сезонами TMDB может держать in_production=true без status=Returning Series —
        // не должно ложно классифицироваться как ENDED/CANCELLED.
        assertEquals(SeriesStatus.UPCOMING, TmdbEpisodeCalculator.status("In Production", inProduction = true))
    }

    @Test
    fun `details card never exposes known episode count as released`() {
        val details = TmdbTvDetails(
            id = 1,
            name = "Ongoing",
            overview = "",
            posterUrl = null,
            voteAverage = 80,
            genres = emptyList(),
            status = SeriesStatus.ONGOING,
            seasons = listOf(TmdbSeasonSummary(1, episodeCount = 24, airDate = today.minusDays(7))),
        ).toAnimeDetails()

        assertEquals(0, details.episodesAired)
        assertNull(details.episodesTotal)
    }
}
