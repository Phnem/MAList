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
        number: Double?,
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
    fun partially_read_title_counts_the_segment_up_to_the_furthest_chapter() {
        val chapters = (1..10).map { chapter("c$it", it.toDouble()) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            // Открытая, но не дочитанная глава границу не двигает.
            progress = mapOf("c1" to read(10), "c2" to read(20), "c3" to opened(30)),
        )
        assertEquals(2, summary.readChapters)
        assertEquals(10, summary.totalChapters)
        assertEquals(0.2f, summary.fraction!!, 0.001f)
    }

    @Test
    fun sixteenth_chapter_of_ninety_two_means_sixteen_are_read() {
        // Дефект, ради которого правило и переписано: отметка стоит на одной главе, а прочитаны
        // все до неё. Раньше здесь получалось «1 / 92».
        val chapters = (1..92).map { chapter("c$it", it.toDouble()) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c16" to read(1_000)),
        )
        assertEquals(16, summary.readChapters)
        assertEquals(92, summary.totalChapters)
        assertEquals(16f / 92f, summary.fraction!!, 0.001f)
    }

    @Test
    fun gaps_between_marks_do_not_lower_the_count() {
        val chapters = (1..20).map { chapter("c$it", it.toDouble()) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c2" to read(10), "c7" to read(20), "c12" to read(30)),
        )
        assertEquals(12, summary.readChapters)
    }

    @Test
    fun the_last_chapter_read_fills_the_whole_bar() {
        val chapters = (1..40).map { chapter("c$it", it.toDouble()) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c40" to read(10)),
        )
        assertEquals(40, summary.readChapters)
        assertEquals(1f, summary.fraction!!, 0.001f)
    }

    @Test
    fun the_first_chapter_read_counts_as_one() {
        val chapters = (1..40).map { chapter("c$it", it.toDouble()) }
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c1" to read(10)),
        )
        assertEquals(1, summary.readChapters)
    }

    @Test
    fun fractional_chapter_numbers_stay_inside_the_segment() {
        // Гл. 12.5 выходит между 12-й и 13-й — прочитав 13-ю, читатель прошёл и её.
        val chapters = listOf(
            chapter("c12", 12.0),
            chapter("c125", 12.5),
            chapter("c13", 13.0),
            chapter("c14", 14.0),
        )
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c13" to read(10)),
        )
        assertEquals(3, summary.readChapters)
    }

    @Test
    fun unnumbered_extras_count_only_by_their_own_mark() {
        // У пролога/экстры нет номера, и «всё до неё» для неё не определено: её место в оглавлении
        // задаёт дата. Отрезок она не удлиняет и в него не попадает.
        val chapters = listOf(
            chapter("c1", 1.0),
            chapter("c2", 2.0),
            chapter("extra", number = null, publishedAt = 500),
        )
        val onlyNumbered = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c2" to read(10)),
        )
        assertEquals(2, onlyNumbered.readChapters)

        val withExtra = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("c2" to read(10), "extra" to read(20)),
        )
        assertEquals(3, withExtra.readChapters)
    }

    @Test
    fun a_read_extra_alone_does_not_fill_the_whole_title() {
        // Границы по номеру нет — считать «прочитано всё» было бы враньём.
        val chapters = listOf(
            chapter("c1", 1.0),
            chapter("c2", 2.0),
            chapter("extra", number = null, publishedAt = 500),
        )
        val summary = summarizeMangaReading(
            chapters = chapters,
            progress = mapOf("extra" to read(10)),
        )
        assertEquals(1, summary.readChapters)
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
    fun the_count_does_not_depend_on_the_order_the_source_returned() {
        // Список приходит от источника; завязка на его порядок дала бы разный ответ на одном и том
        // же прогрессе, стоит оглавлению прийти перевёрнутым.
        val chapters = (1..20).map { chapter("c$it", it.toDouble()) }
        val progress = mapOf("c9" to read(10))
        assertEquals(
            summarizeMangaReading(chapters, progress).readChapters,
            summarizeMangaReading(chapters.reversed(), progress).readChapters,
        )
    }

    @Test
    fun chapters_before_the_frontier_are_offered_for_marking() {
        val chapters = (1..92).map { chapter("c$it", it.toDouble()) }
        val toMark = chaptersToMarkRead(chapters, mapOf("c16" to read(1_000)))
        assertEquals(15, toMark.size)
        assertTrue(toMark.containsAll((1..15).map { "c$it" }))
        // Сама отмеченная глава и всё, что после неё, не трогается.
        assertFalse(toMark.contains("c16"))
        assertFalse(toMark.contains("c17"))
    }

    @Test
    fun nothing_to_mark_when_the_marks_are_already_complete() {
        // Второй заход по тому же тайтлу обязан вернуть пустоту, иначе хранилище переписывалось бы
        // на каждом открытии вкладки.
        val chapters = (1..5).map { chapter("c$it", it.toDouble()) }
        val progress = chapters.associate { it.key to read(100) }
        assertTrue(chaptersToMarkRead(chapters, progress).isEmpty())
    }

    @Test
    fun nothing_to_mark_without_any_read_chapter() {
        val chapters = (1..5).map { chapter("c$it", it.toDouble()) }
        assertTrue(chaptersToMarkRead(chapters, emptyMap()).isEmpty())
        assertTrue(chaptersToMarkRead(chapters, mapOf("c3" to opened(10))).isEmpty())
    }

    @Test
    fun unknown_table_of_contents_marks_nothing() {
        // Границу не по чему считать — трогать отметки нельзя: операция необратима.
        assertTrue(chaptersToMarkRead(emptyList(), mapOf("c1" to read(10))).isEmpty())
    }

    @Test
    fun unnumbered_extras_are_never_marked_in_bulk() {
        val chapters = listOf(
            chapter("c1", 1.0),
            chapter("extra", number = null, publishedAt = 500),
            chapter("c2", 2.0),
            chapter("c3", 3.0),
        )
        val toMark = chaptersToMarkRead(chapters, mapOf("c3" to read(10)))
        assertEquals(listOf("c1", "c2"), toMark)
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
