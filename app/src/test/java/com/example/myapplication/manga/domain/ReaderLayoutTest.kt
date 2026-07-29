package com.example.myapplication.manga.domain

import com.example.myapplication.manga.data.MangaReaderMode
import com.example.myapplication.manga.data.PageDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Порядок обхода раскладок ридера.
 *
 * Тривиальная арифметика, но именно она разошлась с ожиданием пользователя: цикл начинался с
 * классической манги, а он читает вертикальным свайпом.
 */
class ReaderLayoutTest {

    @Test
    fun vertical_goes_to_the_left_to_right_pages() {
        assertEquals(
            MangaReaderMode.Paged to PageDirection.Ltr,
            nextReaderLayout(MangaReaderMode.Webtoon, PageDirection.Rtl),
        )
    }

    @Test
    fun left_to_right_goes_to_right_to_left() {
        assertEquals(
            MangaReaderMode.Paged to PageDirection.Rtl,
            nextReaderLayout(MangaReaderMode.Paged, PageDirection.Ltr),
        )
    }

    @Test
    fun right_to_left_returns_to_vertical() {
        assertEquals(
            MangaReaderMode.Webtoon to PageDirection.Rtl,
            nextReaderLayout(MangaReaderMode.Paged, PageDirection.Rtl),
        )
    }

    @Test
    fun three_taps_return_to_the_starting_layout() {
        var layout = MangaReaderMode.Webtoon to PageDirection.Rtl
        repeat(3) { layout = nextReaderLayout(layout.first, layout.second) }
        assertEquals(MangaReaderMode.Webtoon to PageDirection.Rtl, layout)
    }

    @Test
    fun the_cycle_visits_every_layout_exactly_once() {
        var layout = MangaReaderMode.Webtoon to PageDirection.Ltr
        val seen = buildList {
            repeat(3) {
                add(layout)
                layout = nextReaderLayout(layout.first, layout.second)
            }
        }
        assertEquals(3, seen.distinct().size)
    }
}
