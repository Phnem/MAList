package com.example.myapplication.domain.stats

import com.example.myapplication.network.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsRatingBucketTest {
    @Test
    fun buckets_continuous_noGaps() {
        assertEquals(StatsRatingBucket.TAG_02, StatsRatingBucket.tagForAverage(0.0))
        assertEquals(StatsRatingBucket.TAG_02, StatsRatingBucket.tagForAverage(2.0))
        assertEquals(StatsRatingBucket.TAG_24, StatsRatingBucket.tagForAverage(2.01))
        assertEquals(StatsRatingBucket.TAG_24, StatsRatingBucket.tagForAverage(4.0))
        assertEquals(StatsRatingBucket.TAG_45, StatsRatingBucket.tagForAverage(4.01))
        assertEquals(StatsRatingBucket.TAG_45, StatsRatingBucket.tagForAverage(5.0))
    }
}

class StatsPhraseLineParserTest {
    @Test
    fun parses_valid_line_into_bucket() {
        val lines = sequenceOf("(RU)(24)(S) {_} серий текст.")
        val map = StatsPhraseLineParser.parseLines(lines)
        val key = StatsPhraseGroupKey(AppLanguage.RU, StatsRatingBucket.TAG_24)
        val list = map[key].orEmpty()
        assertEquals(1, list.size)
        assertEquals(StatsSubstitutionKind.Series, list[0].substitutionKind)
        assertEquals("{_} серий текст.", list[0].template)
    }

    @Test
    fun skips_malformed_lines() {
        val lines = sequenceOf(
            "not a phrase",
            "(RU)(02)(R) {_} ок",
            ""
        )
        val map = StatsPhraseLineParser.parseLines(lines)
        assertEquals(1, map.values.sumOf { it.size })
    }

    @Test
    fun preserves_file_order_within_bucket() {
        val lines = sequenceOf(
            "(EN)(45)(R) first",
            "(EN)(45)(S) second"
        )
        val map = StatsPhraseLineParser.parseLines(lines)
        val key = StatsPhraseGroupKey(AppLanguage.EN, StatsRatingBucket.TAG_45)
        val list = map[key].orEmpty()
        assertEquals(StatsSubstitutionKind.Rating, list[0].substitutionKind)
        assertEquals(StatsSubstitutionKind.Series, list[1].substitutionKind)
        assertEquals("first", list[0].template)
        assertEquals("second", list[1].template)
    }
}

/** Логика `(current + 1) % size` совпадает с use case для ротации фраз. */
class StatsPhraseRotationLogicTest {
    @Test
    fun advances_and_wraps() {
        val size = 5
        assertEquals(1, nextIndex(0, size))
        assertEquals(4, nextIndex(3, size))
        assertEquals(0, nextIndex(4, size))
    }

    private fun nextIndex(current: Int, poolSize: Int): Int = (current + 1) % poolSize
}

class StatsPhraseLineFormatTest {
    @Test
    fun replaces_placeholder_globally() {
        val line = StatsPhraseLine(
            template = "A {_} B {_}",
            substitutionKind = StatsSubstitutionKind.Series
        )
        assertEquals("A 6154 B 6154", line.format("ignored", 6154))
    }

    @Test
    fun uses_rating_substitution() {
        val line = StatsPhraseLine(
            template = "Rating {_}",
            substitutionKind = StatsSubstitutionKind.Rating
        )
        assertEquals("Rating 3,2", line.format("3,2", 0))
    }
}
