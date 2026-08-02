package com.example.myapplication.domain.seasons

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingSeasonRefreshTest {

    @Test
    fun `repeated find more replaces stale seasons instead of preserving them`() {
        val stale = listOf(
            SeasonInfo(seasonNumber = 1, episodes = 24, source = "AniList"),
            SeasonInfo(seasonNumber = 2, episodes = 13, source = "AniList"),
            SeasonInfo(seasonNumber = 3, episodes = 12, source = "Kodik"),
        )
        val freshCatalogue = listOf(
            SeasonInfo(
                seasonNumber = 1,
                episodes = 24,
                source = "AniList",
                title = "Food Wars!",
            ),
            SeasonInfo(
                seasonNumber = 2,
                episodes = 13,
                source = "AniList",
                title = "Food Wars! The Second Plate",
            ),
        )
        val freshlyDiscovered = listOf(
            DiscoveredSeason(seasonNumber = 1, episodes = 24, source = "Kodik"),
            DiscoveredSeason(seasonNumber = 2, episodes = 13, source = "Kodik"),
        )

        val refreshed = refreshSeasonDiscovery(stale, freshCatalogue, freshlyDiscovered)

        assertEquals(listOf(1, 2), refreshed.seasons.map { it.seasonNumber })
        assertEquals(1, refreshed.removedSeasons)
        assertEquals(
            "Food Wars! The Second Plate",
            refreshed.seasons.first { it.seasonNumber == 2 }.title,
        )
    }

    @Test
    fun `existing cached list makes find more a full refresh`() {
        val cached = listOf(
            SeasonInfo(seasonNumber = 1, episodes = 24, source = "AniList"),
            SeasonInfo(seasonNumber = 2, episodes = 13, source = "Kodik"),
        )

        assertEquals(
            true,
            shouldRefreshSeasonDiscovery(explicitlyRequested = false, known = cached),
        )
        assertEquals(
            true,
            shouldRefreshSeasonDiscovery(explicitlyRequested = true, known = emptyList()),
        )
        assertEquals(
            true,
            shouldRefreshSeasonDiscovery(
                explicitlyRequested = false,
                known = listOf(
                    SeasonInfo(seasonNumber = 1, episodes = 24, source = "AniList"),
                ),
            ),
        )
    }
}
