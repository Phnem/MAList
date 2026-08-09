package com.example.myapplication.domain.enrichment

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.domain.settings.RepairAnimeDbUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionGapDetectorPolicyTest {
    @Test
    fun `field repair scan supports anime movie and series but not manga`() {
        assertTrue(MediaType.ANIME.isSupportedByFieldRepair())
        assertTrue(MediaType.MOVIE.isSupportedByFieldRepair())
        assertTrue(MediaType.SERIES.isSupportedByFieldRepair())
        assertFalse(MediaType.MANGA.isSupportedByFieldRepair())
    }

    @Test
    fun `provider id gap is visible to detector but never suppressed by generic journal`() {
        val gaps = RepairAnimeDbUseCase.FieldGaps(
            missingImage = false,
            missingTags = false,
            missingRating = false,
            missingAnimeExternalId = false,
            missingTmdb = true,
            missingKinopoisk = false,
            kinopoiskRetryable = false,
            missingCategoryType = false,
            missingEpisodes = false,
        )

        assertEquals(setOf(GapKind.EXTERNAL_ID), gaps.fieldKinds())
        assertEquals(emptySet<GapKind>(), gaps.journalFieldKinds())
    }
}
