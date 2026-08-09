package com.example.myapplication.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Знак смещения страницы. Тест написан после того, как перепутанный знак съел весь «наезд»:
 * при сложении с `currentPageOffsetFraction` обе видимые страницы оказывались «наезжающими»,
 * и трансформация выходила нулевой.
 */
class WorkspacePageOffsetTest {

    private val eps = 0.0001f

    @Test
    fun `settled page sits at zero and its neighbours a page away`() {
        assertEquals(0f, workspacePageOffset(1, 1, 0f), eps)
        assertEquals(1f, workspacePageOffset(2, 1, 0f), eps)
        assertEquals(-1f, workspacePageOffset(0, 1, 0f), eps)
    }

    @Test
    fun `dragging forward moves pages left`() {
        // Пейджер уехал на 30 % вперёд: текущая страница ушла влево, следующая подъезжает справа.
        val current = workspacePageOffset(1, 1, 0.3f)
        val next = workspacePageOffset(2, 1, 0.3f)

        assertEquals(-0.3f, current, eps)
        assertEquals(0.7f, next, eps)
        assertTrue("текущая обязана быть слева от наезжающей", current < next)
    }

    @Test
    fun `dragging back moves pages right`() {
        assertEquals(0.3f, workspacePageOffset(1, 1, -0.3f), eps)
        assertEquals(-0.7f, workspacePageOffset(0, 1, -0.3f), eps)
    }

    @Test
    fun `the two visible pages never share a role`() {
        // Главный инвариант: на переходе одна страница наезжает (offset >= 0), другая уходит
        // под неё (offset < 0). Если обе попали в одну ветку — наезда не видно.
        listOf(0.1f, 0.25f, 0.49f).forEach { fraction ->
            val current = workspacePageTransform(workspacePageOffset(1, 1, fraction))
            val next = workspacePageTransform(workspacePageOffset(2, 1, fraction))

            assertTrue("уходящая при $fraction обязана углубиться", current.scale < 1f)
            assertEquals("наезжающая при $fraction идёт без масштаба", 1f, next.scale, eps)
            assertTrue("уходящая при $fraction обязана притухнуть", current.dimAlpha > 0f)
        }
    }
}
