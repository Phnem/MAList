package com.example.myapplication.domain.search

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило дубликата и схлопывание списка.
 *
 * Схлопывание **удаляет пользовательские записи**, поэтому здесь проверяется не только «дубликаты
 * находятся», но и «не-дубликаты не трогаются» и «выживает более заполненная запись».
 */
class DuplicateTitleRuleTest {

    private fun anime(
        id: String,
        title: String,
        titleEn: String? = null,
        titleRu: String? = null,
        mediaType: MediaType = MediaType.ANIME,
        anilistId: Int? = null,
        malId: Int? = null,
        shikimoriId: Int? = null,
        tmdbId: Int? = null,
        kinopoiskId: Int? = null,
        rating: Float = 0f,
        isFavorite: Boolean = false,
        comment: String = "",
        tags: List<String> = emptyList(),
        imageFileName: String? = null,
        episodes: Int = 0,
        dateAdded: Long = 0L,
    ) = Anime(
        id = id,
        title = title,
        titleEn = titleEn,
        titleRu = titleRu,
        episodes = episodes,
        rating = rating,
        imageFileName = imageFileName,
        orderIndex = 0,
        dateAdded = dateAdded,
        isFavorite = isFavorite,
        tags = persistentListOf<String>().addAll(tags),
        comment = comment,
        anilistId = anilistId,
        malId = malId,
        shikimoriId = shikimoriId,
        tmdbId = tmdbId,
        kinopoiskId = kinopoiskId,
        mediaType = mediaType,
    )

    // ---- Правило ----

    @Test
    fun same_external_id_and_type_is_duplicate() {
        val a = anime("1", "Наруто", anilistId = 20)
        val b = anime("2", "Naruto", anilistId = 20)
        assertTrue(isDuplicate(a, b))
    }

    @Test
    fun each_external_id_counts() {
        assertTrue(isDuplicate(anime("1", "A", malId = 5), anime("2", "B", malId = 5)))
        assertTrue(isDuplicate(anime("1", "A", shikimoriId = 7), anime("2", "B", shikimoriId = 7)))
    }

    @Test
    fun same_normalized_title_is_duplicate() {
        val a = anime("1", "Attack on Titan")
        val b = anime("2", "  attack   on titan!  ")
        assertTrue(isDuplicate(a, b))
    }

    @Test
    fun title_matches_across_translation_fields() {
        val a = anime("1", "Наруто", titleEn = "Naruto")
        val b = anime("2", "Naruto")
        assertTrue(isDuplicate(a, b))
    }

    @Test
    fun different_media_type_is_never_duplicate() {
        val anime = anime("1", "Berserk", mediaType = MediaType.ANIME, malId = 33)
        val manga = anime("2", "Berserk", mediaType = MediaType.MANGA, malId = 33)
        assertFalse(
            "Аниме и манга с одним названием и даже одним id — разные записи",
            isDuplicate(anime, manga),
        )
    }

    @Test
    fun similar_but_distinct_titles_are_not_duplicates() {
        // Подстрочное сравнение схлопнуло бы их в одну запись и стёрло бы отдельный тайтл.
        assertFalse(isDuplicate(anime("1", "Naruto"), anime("2", "Naruto Shippuden")))
        assertFalse(isDuplicate(anime("1", "Fate/Zero"), anime("2", "Fate/Apocrypha")))
    }

    @Test
    fun different_ids_and_titles_are_not_duplicates() {
        assertFalse(isDuplicate(anime("1", "Bleach", malId = 1), anime("2", "Gintama", malId = 2)))
    }

    @Test
    fun movie_ids_are_used_for_duplicate_detection() {
        assertTrue(
            isDuplicate(
                anime("1", "Дюна", mediaType = MediaType.MOVIE, tmdbId = 438631),
                anime("2", "Dune", mediaType = MediaType.MOVIE, tmdbId = 438631),
            )
        )
        assertTrue(
            isDuplicate(
                anime("1", "Шоу", mediaType = MediaType.SERIES, kinopoiskId = 10),
                anime("2", "Show", mediaType = MediaType.SERIES, kinopoiskId = 10),
            )
        )
    }

    @Test
    fun `conflicting canonical tmdb ids protect same title remakes`() {
        val original = anime("1", "The Office", mediaType = MediaType.SERIES, tmdbId = 2996)
        val remake = anime("2", "The Office", mediaType = MediaType.SERIES, tmdbId = 2316)
        assertFalse(isDuplicate(original, remake))
    }

    // ---- Схлопывание ----

    @Test
    fun collapse_leaves_unique_records_untouched() {
        val list = listOf(anime("1", "Bleach"), anime("2", "Gintama"), anime("3", "Naruto"))
        assertEquals(list, collapseDuplicates(list))
    }

    @Test
    fun collapse_keeps_one_of_each_duplicate_group() {
        val list = listOf(anime("1", "Naruto"), anime("2", "naruto"), anime("3", "НАРУТО", titleEn = "Naruto"))
        assertEquals(1, collapseDuplicates(list).size)
    }

    @Test
    fun favourite_survives_over_empty_copy() {
        val empty = anime("1", "Naruto", dateAdded = 1)
        val favourite = anime("2", "Naruto", isFavorite = true, dateAdded = 2)
        val survivors = collapseDuplicates(listOf(empty, favourite))
        assertEquals(1, survivors.size)
        assertEquals("2", survivors.single().id)
    }

    @Test
    fun rated_record_survives_over_unrated() {
        val unrated = anime("1", "Naruto")
        val rated = anime("2", "Naruto", rating = 8.5f)
        assertEquals("2", collapseDuplicates(listOf(unrated, rated)).single().id)
    }

    @Test
    fun commented_record_survives_over_blank() {
        val blank = anime("1", "Naruto")
        val commented = anime("2", "Naruto", comment = "любимое")
        assertEquals("2", collapseDuplicates(listOf(blank, commented)).single().id)
    }

    @Test
    fun survivor_absorbs_ids_and_titles_it_was_missing() {
        // Иначе схлопывание теряло бы id, по которым запись потом обогащается.
        val rich = anime("1", "Naruto", rating = 9f, anilistId = 20)
        val poor = anime("2", "Naruto", malId = 3, shikimoriId = 4, titleRu = "Наруто")
        val survivor = collapseDuplicates(listOf(rich, poor)).single()
        assertEquals("1", survivor.id)
        assertEquals(20, survivor.anilistId)
        assertEquals(3, survivor.malId)
        assertEquals(4, survivor.shikimoriId)
        assertEquals("Наруто", survivor.titleRu)
    }

    @Test
    fun `movie survivor absorbs missing catalog ids`() {
        val rich = anime("1", "Dune", mediaType = MediaType.MOVIE, rating = 9f, tmdbId = 438631)
        val poor = anime("2", "Dune", mediaType = MediaType.MOVIE, kinopoiskId = 409118)
        val survivor = collapseDuplicates(listOf(rich, poor)).single()
        assertEquals(438631, survivor.tmdbId)
        assertEquals(409118, survivor.kinopoiskId)
    }

    @Test
    fun original_record_wins_a_tie() {
        val older = anime("1", "Naruto", dateAdded = 100)
        val newer = anime("2", "Naruto", dateAdded = 200)
        assertEquals("1", collapseDuplicates(listOf(newer, older)).single().id)
    }

    @Test
    fun anime_and_manga_both_survive_collapse() {
        val list = listOf(
            anime("1", "Berserk", mediaType = MediaType.ANIME),
            anime("2", "Berserk", mediaType = MediaType.MANGA),
        )
        assertEquals(2, collapseDuplicates(list).size)
    }

    @Test
    fun collapse_preserves_first_seen_order() {
        val list = listOf(anime("1", "Bleach"), anime("2", "Naruto"), anime("3", "naruto"), anime("4", "Gintama"))
        assertEquals(listOf("1", "2", "4"), collapseDuplicates(list).map { it.id })
    }

    @Test
    fun empty_list_collapses_to_empty() {
        assertEquals(emptyList<Anime>(), collapseDuplicates(emptyList()))
    }
}
