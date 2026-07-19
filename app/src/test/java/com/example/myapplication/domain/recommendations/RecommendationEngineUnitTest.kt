package com.example.myapplication.domain.recommendations

import com.example.myapplication.data.models.Anime
import com.example.myapplication.network.ApiSearchResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun anime(
    id: String,
    title: String,
    /** 10-Р±Р°Р»Р»СЊРЅР°СЏ С€РєР°Р»Р° (РєР°Рє РІ [Anime.rating]). РўРµСЃС‚С‹ РїРёС€СѓС‚ 0..10. */
    rating: Float = 0f,
    tags: List<String> = emptyList(),
    isFavorite: Boolean = false,
    anilistId: Int? = null,
    titleEn: String? = null,
    titleRu: String? = null,
) = Anime(
    id = id,
    title = title,
    titleEn = titleEn,
    titleRu = titleRu,
    episodes = 12,
    rating = rating,
    imageFileName = null,
    orderIndex = 0,
    dateAdded = 0L,
    isFavorite = isFavorite,
    tags = tags.toImmutableList(),
    anilistId = anilistId,
)

private fun candidate(
    title: String,
    source: String = "AniList",
    externalId: String? = null,
    genres: List<String> = emptyList(),
    rating: Int? = null,
    posterUrl: String? = "https://img/x.jpg",
    altTitle: String? = null,
) = ApiSearchResult(
    title = title,
    altTitle = altTitle,
    posterUrl = posterUrl,
    episodes = 12,
    description = "",
    type = "ANIME",
    genres = genres,
    rating = rating,
    source = source,
    categoryType = "ANIME",
    externalId = externalId,
)

class GenreAffinityCalculatorTest {

    private val calculator = GenreAffinityCalculator()

    @Test
    fun high_rating_pulls_genre_up_low_rating_down() {
        val affinity = calculator.calculate(
            listOf(
                anime("1", "A", rating = 9f, tags = listOf("Action")),
                anime("2", "B", rating = 2f, tags = listOf("Romance")),
            )
        )
        assertTrue(affinity.getValue("Action") > 0f)
        assertTrue(affinity.getValue("Romance") < 0f)
    }

    @Test
    fun unrated_non_favorite_titles_do_not_contribute() {
        val affinity = calculator.calculate(
            listOf(anime("1", "A", rating = 0f, tags = listOf("Action")))
        )
        assertTrue(affinity.isEmpty())
    }

    @Test
    fun favorite_adds_bonus_weight() {
        val affinity = calculator.calculate(
            listOf(anime("1", "A", rating = 0f, tags = listOf("Comedy"), isFavorite = true))
        )
        assertTrue(affinity.getValue("Comedy") > 0f)
    }

    @Test
    fun values_are_normalized_to_unit_range() {
        val affinity = calculator.calculate(
            listOf(
                anime("1", "A", rating = 10f, tags = listOf("Action")),
                anime("2", "B", rating = 10f, tags = listOf("Action")),
                anime("3", "C", rating = 8f, tags = listOf("Drama")),
            )
        )
        assertTrue(affinity.values.all { it in -1f..1f })
        assertEquals(1f, affinity.getValue("Action"), 0.0001f)
    }
}

class RecommendationScorerTest {

    private fun scorer(affinity: Map<String, Float> = emptyMap()) =
        RecommendationScorer(affinity = affinity, genreToTagId = { it })

    @Test
    fun co_occurrence_across_seeds_outranks_single_seed() {
        val pool = listOf(
            PoolEntry(candidate("Twice Recommended"), "Seed1", 5f),
            PoolEntry(candidate("Twice Recommended"), "Seed2", 5f),
            PoolEntry(candidate("Once Recommended"), "Seed1", 5f),
        )
        val ranked = scorer().scoreAndRank(pool, limit = 10)
        assertEquals("Twice Recommended", ranked.first().title)
    }

    @Test
    fun genre_affinity_ranks_matching_candidate_higher() {
        val pool = listOf(
            PoolEntry(candidate("Action Pick", genres = listOf("Action")), "S", 4f),
            PoolEntry(candidate("Romance Pick", genres = listOf("Romance")), "S", 4f),
        )
        val ranked = scorer(mapOf("Action" to 1f, "Romance" to -1f)).scoreAndRank(pool, limit = 10)
        assertEquals("Action Pick", ranked.first().title)
    }

    @Test
    fun duplicates_merge_across_sources_by_any_title_key() {
        // AniList: title=english, alt=romaji; Shikimori: title=romaji, alt=russian
        val pool = listOf(
            PoolEntry(candidate("Attack on Titan", source = "AniList", altTitle = "Shingeki no Kyojin"), "S1", 5f),
            PoolEntry(candidate("Shingeki no Kyojin", source = "Shikimori", altTitle = "Attack on Titan RU"), "S2", 5f),
        )
        val ranked = scorer().scoreAndRank(pool, limit = 10)
        assertEquals(1, ranked.size)
    }

    @Test
    fun preferred_source_wins_title_when_duplicates_merge() {
        val pool = listOf(
            PoolEntry(candidate("Attack on Titan", source = "AniList", altTitle = "Shingeki no Kyojin", genres = listOf("Action"), rating = 85), "S1", 5f),
            PoolEntry(candidate("Attack Titan RU", source = "Shikimori", altTitle = "Shingeki no Kyojin"), "S2", 5f),
        )
        val ranked = RecommendationScorer(
            affinity = emptyMap(),
            genreToTagId = { it },
            preferredSource = "Shikimori",
        ).scoreAndRank(pool, limit = 10)
        assertEquals(1, ranked.size)
        assertEquals("Attack Titan RU", ranked.first().title)
        // Metadata missing from preferred source is filled from duplicates
        assertEquals(listOf("Action"), ranked.first().genres)
    }

    @Test
    fun candidates_without_cover_are_dropped() {
        val pool = listOf(PoolEntry(candidate("No Cover", posterUrl = null), "S", 5f))
        assertTrue(scorer().scoreAndRank(pool, limit = 10).isEmpty())
    }

    @Test
    fun rating_scales_are_normalized_to_100() {
        val shikimori = listOf(PoolEntry(candidate("A", source = "Shikimori", rating = 8), "S", 5f))
        val anilist = listOf(PoolEntry(candidate("B", source = "AniList", rating = 80), "S", 5f))
        val a = scorer().scoreAndRank(shikimori, limit = 1).first()
        val b = scorer().scoreAndRank(anilist, limit = 1).first()
        assertEquals(80, a.externalRating)
        assertEquals(80, b.externalRating)
        assertEquals(a.score, b.score, 0.0001f)
    }

    @Test
    fun limit_is_respected_and_sorted_desc() {
        val pool = (1..30).map { i ->
            PoolEntry(candidate("Title $i", rating = i * 3), "S", 5f)
        }
        val ranked = scorer().scoreAndRank(pool, limit = 20)
        assertEquals(20, ranked.size)
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.score >= b.score })
    }
}

class RecommendationFilterTest {

    private val filter = RecommendationFilter()

    @Test
    fun excludes_titles_already_in_library_by_external_id() {
        val library = listOf(anime("1", "Mine", anilistId = 42))
        val pool = listOf(
            PoolEntry(candidate("Different Name", source = "AniList", externalId = "42"), "S", 5f),
            PoolEntry(candidate("New Title", source = "AniList", externalId = "43"), "S", 5f),
        )
        val result = filter.filter(pool, library)
        assertEquals(listOf("New Title"), result.map { it.result.title })
    }

    @Test
    fun excludes_titles_already_in_library_by_normalized_title() {
        val library = listOf(anime("1", "Attack on Titan!"))
        val pool = listOf(
            PoolEntry(candidate("attack ON titan", source = "Shikimori", externalId = "7"), "S", 5f),
        )
        assertTrue(filter.filter(pool, library).isEmpty())
    }

    @Test
    fun excludes_by_english_or_russian_library_title() {
        val library = listOf(
            anime("1", "Local Title", titleEn = "Attack on Titan", titleRu = "Ataka titanov"),
        )
        val byEn = listOf(
            PoolEntry(candidate("Attack on Titan", source = "AniList", externalId = "1"), "S", 5f),
        )
        val byRu = listOf(
            PoolEntry(candidate("Ataka titanov", source = "Shikimori", externalId = "2"), "S", 5f),
        )
        assertTrue(filter.filter(byEn, library).isEmpty())
        assertTrue(filter.filter(byRu, library).isEmpty())
    }

    @Test
    fun excludes_candidates_without_cover() {
        val pool = listOf(PoolEntry(candidate("X", posterUrl = " "), "S", 5f))
        assertTrue(filter.filter(pool, emptyList()).isEmpty())
    }
}
