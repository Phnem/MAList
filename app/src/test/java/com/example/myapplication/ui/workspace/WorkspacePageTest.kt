package com.example.myapplication.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspacePageTest {

    @Test
    fun `order is Home Settings Add Frame`() {
        // Порядок слева направо — решение пользователя, а не деталь реализации: он задаёт и
        // позицию в доке, и позицию страницы в пейджере.
        assertEquals(
            listOf(
                WorkspacePage.HOME,
                WorkspacePage.SETTINGS,
                WorkspacePage.ADD,
                WorkspacePage.FRAME,
            ),
            WorkspacePage.Ordered,
        )
        assertEquals(4, WorkspacePage.PageCount)
    }

    @Test
    fun `stats is not a page - it stays a bottom sheet`() {
        assert(WorkspacePage.Ordered.none { it.name == "STATS" }) {
            "Статистика вернулась в страницы, хотя должна открываться шторкой из верхнего дока"
        }
    }

    @Test
    fun `home is the leftmost page and the starting one`() {
        assertEquals(WorkspacePage.HOME, WorkspacePage.Start)
        assertEquals(0, WorkspacePage.HOME.index)
    }

    @Test
    fun `index round trip`() {
        WorkspacePage.Ordered.forEach { page ->
            assertEquals(page, WorkspacePage.ofIndex(page.index))
        }
    }

    @Test
    fun `out of range index falls back to the start page`() {
        assertEquals(WorkspacePage.Start, WorkspacePage.ofIndex(-1))
        assertEquals(WorkspacePage.Start, WorkspacePage.ofIndex(WorkspacePage.PageCount))
        assertEquals(WorkspacePage.Start, WorkspacePage.ofIndex(Int.MAX_VALUE))
    }

    @Test
    fun `back from any section returns to home`() {
        listOf(
            WorkspacePage.FRAME,
            WorkspacePage.ADD,
            WorkspacePage.SETTINGS,
        ).forEach { page ->
            assertEquals("back from $page", WorkspacePage.HOME, WorkspacePage.backTargetFrom(page))
        }
    }

    @Test
    fun `back from home is not intercepted so the system closes the app`() {
        assertNull(WorkspacePage.backTargetFrom(WorkspacePage.HOME))
    }
}
