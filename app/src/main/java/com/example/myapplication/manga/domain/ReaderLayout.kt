package com.example.myapplication.manga.domain

import com.example.myapplication.manga.data.MangaReaderMode
import com.example.myapplication.manga.data.PageDirection

/**
 * Раскладка ридера как одно из трёх положений кнопки.
 *
 * В хранилище это два независимых измерения ([MangaReaderMode] и [PageDirection]), но кнопка
 * ходит по трём осмысленным комбинациям, и порядок обхода — правило, а не деталь отрисовки:
 * пользователь уже один раз получил не тот цикл, который ожидал.
 */

/**
 * Следующее положение: **вертикальный свайп → вправо (LTR) → влево (RTL) → вертикальный**.
 *
 * «Вправо» — это сторона, с которой приходит следующая страница ([PageDirection.Ltr]), а не
 * сторона, куда ведёт палец.
 *
 * Из «влево» в вертикальный уходим, не сбрасывая направление: в ленте оно всё равно не видно, а
 * лишняя запись зря пересобрала бы состояние.
 */
fun nextReaderLayout(
    mode: MangaReaderMode,
    direction: PageDirection,
): Pair<MangaReaderMode, PageDirection> = when {
    mode == MangaReaderMode.Webtoon -> MangaReaderMode.Paged to PageDirection.Ltr
    direction == PageDirection.Ltr -> MangaReaderMode.Paged to PageDirection.Rtl
    else -> MangaReaderMode.Webtoon to direction
}
