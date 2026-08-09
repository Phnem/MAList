package com.example.myapplication.ui.home.cardmenu

/**
 * На сколько пикселей поднять выделенную карточку, чтобы ряд действий под ней поместился целиком.
 *
 * Правило (решение D7): пока места снизу хватает — карточка стоит там же, где стояла в списке.
 * Не хватает — поднимаем ровно на нехватку, но не выше верхней безопасной границы: карточка
 * не должна уехать под статус-бар.
 *
 * Все величины — в пикселях одной системы координат (корень окна).
 *
 * @param cardTop верх карточки
 * @param cardHeight высота карточки (уже с учётом увеличения при выделении)
 * @param menuHeight высота ряда кнопок
 * @param gap зазор между карточкой и рядом кнопок
 * @param viewportHeight высота окна
 * @param topInset верхняя безопасная граница (статус-бар и отступ)
 * @param bottomInset нижняя безопасная граница (док, нав-бар и отступ)
 * @return сдвиг ВВЕРХ, всегда >= 0
 */
fun cardLiftFor(
    cardTop: Float,
    cardHeight: Float,
    menuHeight: Float,
    gap: Float,
    viewportHeight: Float,
    topInset: Float,
    bottomInset: Float,
): Float {
    val menuBottom = cardTop + cardHeight + gap + menuHeight
    val allowedBottom = viewportHeight - bottomInset
    val overflow = menuBottom - allowedBottom
    if (overflow <= 0f) return 0f

    // Выше верхней границы не поднимаемся: лучше показать ряд кнопок частично прижатым,
    // чем спрятать половину карточки под статус-бар.
    val headroom = (cardTop - topInset).coerceAtLeast(0f)
    return overflow.coerceAtMost(headroom)
}
