package com.example.myapplication.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Источники (чаще всего Shikimori) на неизвестном количестве серий или глав отдают 0. Печатать
 * «0 eps» нельзя: пользователь читает это как «серий нет», хотя источник просто не знает их числа.
 */
class SearchResultCountTextTest {

    @Test
    fun zero_means_unknown_not_none() {
        assertEquals("N/A", searchResultCountText(0, "eps"))
        assertEquals("N/A", searchResultCountText(0, "ch."))
    }

    @Test
    fun missing_count_is_unknown() {
        assertEquals("N/A", searchResultCountText(null, "eps"))
    }

    @Test
    fun negative_count_is_unknown() {
        // Отрицательного числа серий не бывает; если источник прислал такое, это тоже «не знаю»,
        // а не значение, которое стоит показывать.
        assertEquals("N/A", searchResultCountText(-1, "eps"))
    }

    @Test
    fun real_counts_are_printed_with_their_unit() {
        assertEquals("1 eps", searchResultCountText(1, "eps"))
        assertEquals("12 eps", searchResultCountText(12, "eps"))
        assertEquals("1128 ch.", searchResultCountText(1128, "ch."))
    }
}
