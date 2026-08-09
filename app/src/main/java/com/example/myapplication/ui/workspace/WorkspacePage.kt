package com.example.myapplication.ui.workspace

/**
 * Раздел корневой рабочей области. Порядок значений — ЧАСТЬ КОНТРАКТА: индекс задаёт и позицию
 * страницы в пейджере, и позицию сегмента в доке.
 *
 * Статистики среди страниц нет: она осталась bottom sheet и открывается средней кнопкой
 * ВЕРХНЕГО дока (там, где раньше была панель подключения). Панель подключения переехала
 * отдельным пунктом в настройки.
 *
 * Логика здесь намеренно чистая (без Compose) — она покрыта unit-тестами, см. WorkspacePageTest.
 */
enum class WorkspacePage {
    // Порядок слева направо задан пользователем (2026-08-08): Главная первой, дальше по
    // убыванию частоты обращения. Кадр — самый редкий, поэтому крайний.
    HOME,
    SETTINGS,
    ADD,
    FRAME;

    val index: Int get() = ordinal

    companion object {
        val Ordered: List<WorkspacePage> = entries

        val PageCount: Int = entries.size

        /** Стартовый раздел рабочей области. */
        val Start: WorkspacePage = HOME

        /** Страница по индексу пейджера; выход за границы схлопывается к стартовой. */
        fun ofIndex(index: Int): WorkspacePage = entries.getOrElse(index) { Start }

        /**
         * Куда ведёт системное «Назад».
         *
         * @return страница для перелистывания либо null, если перехватывать не нужно —
         * тогда Back доходит до системы и закрывает приложение (правило D2).
         */
        fun backTargetFrom(page: WorkspacePage): WorkspacePage? =
            if (page == Start) null else Start
    }
}
