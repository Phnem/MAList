package com.example.myapplication.manga.domain

import com.example.myapplication.manga.data.ChapterReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Прогресс чтения манги на карточке главного экрана.
 *
 * Отрисовку не проверить, а вот «сколько прочитано из скольких» и «вышли ли новые главы» —
 * детерминированный счёт, и ошибка здесь либо врёт пользователю о его прогрессе, либо зажигает
 * метку новых глав на тайтле, где ничего не выходило.
 */
class MangaReadingSummaryTest {

    private fun chapter(
        key: String,
        number: Double,
        publishedAt: Long = 0L,
        language: String? = null,
    ) = MangaChapter(
        sourceId = MangaSourceId("src"),
        mangaKey = "manga",
        key = key,
        number = number,
        publishedAt = publishedAt,
        language = language,
    )

    private fun read(at: Long) = ChapterReadingProgress(
        pageIndex = 9,
        pageCount = 10,
        read = true,
        updatedAt = at,
    )

    private fun opened(at: Long) = ChapterReadingProgress(
        pageIndex = 3,
        pageCount = 10,
        read = false,
        updatedAt = at,
    )

    @Test
    fun unknown_table_of_contents_has_no_denominator() {
        // Оглавление не кэшировано (тайтл не привязан или кэш вычищен). Прочитанные главы всё
        // равно известны — показываем их без знаменателя, а не «5 / 0».
        val summary = summarizeMangaReading(
            chapters = emptyList(),
            progress = mapOf("c1" to read(100), "c2" to read(200)),
        )
        assertEquals(2, summary.readChapters)
        assertNull(summary.totalChapters)
        assertNull(summary.fraction)
    }

    @Test
    fun nothing_read_and_nothing_cached_is_not_a_progress_at_all() {
        val summary = summarizeMangaReading(chapters = emptyList(), progress = emptyMap())
        assertEquals(0, summary.readChapters)
        assertNull(summary.totalChapters)
        assertFalse(summary.hasProgress)
    }

    @Test
    fun partially_read_title_counts_only_finished_chapters() {
        val chapters = (1..10).map { chapter("c$it", it.toDouble()) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            // Открытая, но не дочитанная глава прочитанной не считается.
            progress = mapOf("c1" to read(10), "c2" to read(20), "c3" to opened(30)),
        )
        assertEquals(2, summary.readChapters)
        assertEquals(10, summary.totalChapters)
        assertEquals(0.2f, summary.fraction!!, 0.001f)
    }

    @Test
    fun fully_read_title_is_exactly_full() {
        val chapters = (1..3).map { chapter("c$it", it.toDouble()) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = chapters.associate { it.key to read(100) },
        )
        assertEquals(3, summary.readChapters)
        assertEquals(1f, summary.fraction!!, 0.001f)
        assertFalse(summary.hasNewChapters)
    }

    @Test
    fun marks_left_from_another_source_do_not_inflate_the_counter() {
        // Пользователь сменил источник или язык: в прогрессе остались ключи глав, которых в
        // текущем оглавлении нет. Прочитанных не может быть больше, чем глав.
        val chapters = (1..3).map { chapter("c$it", it.toDouble()) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c1" to read(10), "old-1" to read(20), "old-2" to read(30)),
        )
        assertEquals(1, summary.readChapters)
        assertEquals(3, summary.totalChapters)
    }

    @Test
    fun chapters_published_after_the_last_read_one_count_as_new() {
        val chapters = listOf(
            chapter("c1", 1.0, publishedAt = 100),
            chapter("c2", 2.0, publishedAt = 200),
            chapter("c3", 3.0, publishedAt = 5_000),
            chapter("c4", 4.0, publishedAt = 6_000),
        )
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c1" to read(1_000), "c2" to read(2_000)),
        )
        assertEquals(2, summary.newChapters)
        assertTrue(summary.hasNewChapters)
    }

    @Test
    fun chapters_that_were_already_there_are_not_new_merely_because_they_are_unread() {
        // Пользователь бросил чтение на середине давно вышедшего тайтла — это не «вышли новые».
        val chapters = (1..10).map { chapter("c$it", it.toDouble(), publishedAt = it * 100L) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c1" to read(5_000), "c2" to read(6_000)),
        )
        assertEquals(0, summary.newChapters)
        assertFalse(summary.hasNewChapters)
    }

    @Test
    fun nothing_read_yet_means_nothing_is_new() {
        // Иначе метка «новые главы» горела бы на каждом непрочитанном тайтле коллекции.
        val chapters = (1..5).map { chapter("c$it", it.toDouble(), publishedAt = it * 1_000L) }
        val summary = summarizeMangaReading(chapters = chapters, progress = emptyMap())
        assertEquals(0, summary.newChapters)
    }

    @Test
    fun chapter_without_a_publication_date_is_never_reported_as_new() {
        // publishedAt = 0 значит «источник даты не отдал», а не «вышла в 1970-м».
        val chapters = listOf(
            chapter("c1", 1.0, publishedAt = 100),
            chapter("c2", 2.0, publishedAt = 0),
        )
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c1" to read(1_000)),
        )
        assertEquals(0, summary.newChapters)
    }

    @Test
    fun preferred_language_narrows_the_table_of_contents_but_never_empties_it() {
        val chapters = listOf(
            chapter("ru1", 1.0, language = "ru"),
            chapter("en1", 1.0, language = "en"),
            chapter("en2", 2.0, language = "en"),
        )
        assertEquals(2, chaptersForLanguage(chapters, "en").size)
        // Языка нет ни у одной главы — отдаём всё, а не пустой список: иначе оглавление пропало бы.
        assertEquals(3, chaptersForLanguage(chapters, "de").size)
        assertEquals(3, chaptersForLanguage(chapters, null).size)
    }
}
