package com.example.myapplication.domain.seasons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonIndexTranslationTest {

    private fun spans(vararg episodes: Int): List<SeasonSpan> =
        episodes.mapIndexed { index, count -> SeasonSpan(index + 1, count) }

    /**
     * Реальный расклад «Повар-боец Сома»: каталог считает Totsuki Train Arc отдельным сезоном
     * (S3=12 + S4=12), сайты держат оба кора внутри своего третьего сезона (24).
     */
    private val foodWarsCatalogue = spans(24, 13, 12, 12, 12, 13)
    private val foodWarsSource = spans(24, 13, 24, 12, 13)

    @Test
    fun `six catalogue seasons align onto five source seasons`() {
        val alignment = requireNotNull(alignSeasonScales(foodWarsCatalogue, foodWarsSource))

        assertEquals(
            mapOf(1 to 1, 2 to 2, 3 to 3, 4 to 3, 5 to 4, 6 to 5),
            alignment.catalogueToSource,
        )
    }

    @Test
    fun `last catalogue season translates to the last source season`() {
        assertEquals(
            5,
            translateCatalogueSeason(foodWarsCatalogue, foodWarsSource, catalogueSeason = 6),
        )
    }

    /** Ключевой случай: S4 каталога — это Totsuki Train Arc, а не четвёртый сезон сайта. */
    @Test
    fun `split cour season does not translate to the same ordinal`() {
        assertEquals(
            3,
            translateCatalogueSeason(foodWarsCatalogue, foodWarsSource, catalogueSeason = 4),
        )
    }

    @Test
    fun `glued source season is not one to one`() {
        val alignment = requireNotNull(alignSeasonScales(foodWarsCatalogue, foodWarsSource))

        assertEquals(setOf(1, 2, 4, 5), alignment.oneToOneSourceSeasons)
    }

    @Test
    fun `equal chains translate identically`() {
        val chain = spans(12, 12, 12)

        assertEquals(2, translateCatalogueSeason(chain, chain, catalogueSeason = 2))
    }

    @Test
    fun `equal length chains fall back to identity when counts disagree`() {
        assertEquals(
            3,
            translateCatalogueSeason(spans(12, 13, 12), spans(12, 12, 11), catalogueSeason = 3),
        )
    }

    @Test
    fun `unprovable alignment refuses instead of guessing`() {
        assertNull(alignSeasonScales(spans(24, 13), spans(25, 13)))
        assertNull(translateCatalogueSeason(spans(24, 13, 12), spans(25, 13), catalogueSeason = 3))
    }

    @Test
    fun `zero and empty counts refuse`() {
        assertNull(alignSeasonScales(spans(24, 0), spans(24)))
        assertNull(alignSeasonScales(emptyList(), spans(24)))
        assertNull(alignSeasonScales(spans(24), emptyList()))
    }

    @Test
    fun `source longer than catalogue refuses`() {
        assertNull(alignSeasonScales(spans(24), spans(24, 13)))
    }

    @Test
    fun `catalogue tail left uncovered refuses`() {
        assertNull(alignSeasonScales(spans(24, 13, 12), spans(24, 13)))
    }
}
