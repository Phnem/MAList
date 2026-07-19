package com.example.myapplication.ui.shared

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * ЕДИНОЕ правило против «плоского» дока после закрытия меню.
 *
 * Проблема (kyant `layerBackdrop`): любой оверлей, который блюрит / вдавливает / перекрывает
 * home-контент, при закрытии структурно инвалидирует запись backdrop у списка, но НЕ форсит её
 * перезапись. Запись остаётся пустой → док и все стеклянные карточки заливаются сплошным
 * `bgColor` вместо стекла до первого ручного скролла. Раньше это чинилось точечным
 * `LaunchedEffect` на КАЖДОЕ меню отдельно — поэтому каждое новое меню воспроизводило баг заново.
 *
 * Здесь это одно централизованное восстановление: когда [overlayActive] уходит из true в false
 * (закрылся последний оверлей над доком), мы по кадрам перемонтируем запись ([onRemount]
 * инкрементит `key(...)` над `LazyColumn`) и «пинаем» скролл на пиксель туда-обратно — это и есть
 * та самая реальная инвалидация, что заставляет `layerBackdrop` перезаписаться.
 *
 * **Правило для новых меню:** не пиши свой фикс. Просто добавь флаг нового оверлея в общий
 * `overlayActive` на стороне вызова (там же, где `shouldBlur` / `anyHomeSheetOpen`) — и стекло
 * восстановится само. См. [[layerbackdrop-invalidation-gotcha]].
 */
@Composable
fun GlassBackdropRecovery(
    overlayActive: Boolean,
    listState: LazyListState,
    onRemount: () -> Unit,
) {
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(overlayActive) {
        if (overlayActive) {
            wasActive = true
            return@LaunchedEffect
        }
        if (!wasActive) return@LaunchedEffect
        wasActive = false
        // Ждём, пока анимация закрытия отпустит предков backdrop, затем форсим перезапись.
        repeat(2) { withFrameNanos { } }
        onRemount()
        withFrameNanos { }
        listState.scrollBy(1f)
        withFrameNanos { }
        listState.scrollBy(-1f)
    }
}
