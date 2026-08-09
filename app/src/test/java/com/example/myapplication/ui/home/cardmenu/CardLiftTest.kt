package com.example.myapplication.ui.home.cardmenu

import org.junit.Assert.assertEquals
import org.junit.Test

class CardLiftTest {

    private fun lift(
        cardTop: Float,
        cardHeight: Float = CARD,
        menuHeight: Float = MENU,
        gap: Float = GAP,
        viewportHeight: Float = VIEWPORT,
        topInset: Float = TOP_INSET,
        bottomInset: Float = BOTTOM_INSET,
    ) = cardLiftFor(
        cardTop = cardTop,
        cardHeight = cardHeight,
        menuHeight = menuHeight,
        gap = gap,
        viewportHeight = viewportHeight,
        topInset = topInset,
        bottomInset = bottomInset,
    )

    @Test
    fun `card in the middle of the list does not move`() {
        assertEquals(0f, lift(cardTop = 600f), 0f)
    }

    @Test
    fun `card that exactly fits does not move`() {
        // Низ ряда кнопок ровно на границе: 1300 + 480 + 40 + 160 = 1980 = 2200 - 220
        assertEquals(0f, lift(cardTop = 1300f), 0f)
    }

    @Test
    fun `card near the bottom rises by exactly the overflow`() {
        // 1500 + 480 + 40 + 160 = 2180; граница 1980 → нехватка 200
        assertEquals(200f, lift(cardTop = 1500f), 0f)
    }

    @Test
    fun `rise is clamped so the card never goes under the status bar`() {
        // Нехватка 700, но над карточкой всего 100 свободных пикселей
        assertEquals(100f, lift(cardTop = 200f, cardHeight = 1800f), 0f)
    }

    @Test
    fun `card already above the top inset is not pushed further up`() {
        assertEquals(0f, lift(cardTop = 40f, cardHeight = 2000f), 0f)
    }

    @Test
    fun `taller action row pushes the card higher`() {
        val small = lift(cardTop = 1500f, menuHeight = 160f)
        val tall = lift(cardTop = 1500f, menuHeight = 260f)
        assertEquals(200f, small, 0f)
        assertEquals(300f, tall, 0f)
    }

    private companion object {
        const val VIEWPORT = 2200f
        const val TOP_INSET = 100f
        const val BOTTOM_INSET = 220f
        const val CARD = 480f
        const val MENU = 160f
        const val GAP = 40f
    }
}
