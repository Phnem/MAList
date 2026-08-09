package com.example.myapplication.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

// ==========================================
// Автоскрытие дока по направлению скролла (TICKET-04, решение D9).
//
// Раньше это жило анонимным объектом внутри HomeScreen. С появлением второй страницы со
// списком (настройки) порог и правило разъехались бы по экранам, а док у них общий — поэтому
// правило одно и здесь.
// ==========================================

/** Мёртвая зона: ниже этого дрожание пальца не считается направлением. */
private const val DIRECTION_THRESHOLD_PX = 10f

@Stable
class DockAutoHideState internal constructor() {
    /** Док показан. Скролл контента вниз опускает его, скролл вверх возвращает. */
    var visible: Boolean by mutableStateOf(true)
        internal set

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -DIRECTION_THRESHOLD_PX) {
                visible = false
            } else if (available.y > DIRECTION_THRESHOLD_PX) {
                visible = true
            }
            // Жест не потребляем: сам список должен проскроллиться на ту же величину.
            return Offset.Zero
        }
    }
}

/** Состояние автоскрытия; повесьте [DockAutoHideState.connection] на скроллящийся контейнер. */
@Composable
fun rememberDockAutoHide(): DockAutoHideState = remember { DockAutoHideState() }
