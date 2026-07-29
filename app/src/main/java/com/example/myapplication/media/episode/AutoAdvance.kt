package com.example.myapplication.media.episode

/**
 * Условие автоперехода на следующую серию.
 *
 * Вынесено из слушателя плеера чистой функцией: `STATE_ENDED` прилетает не только после честного
 * досмотра — пустой или сорвавшийся источник даёт его же, и без проверок автопереход утаскивал бы
 * пользователя вперёд по серии на каждой ошибке.
 *
 * @param enabled настройка «включать следующую серию автоматически».
 * @param durationMs длительность завершившейся серии; `0` = плеер так и не узнал, что играл, —
 *   значит, серия не досмотрена, а не доиграна.
 * @param hasNext есть ли следующая серия ([EpisodeRange.hasNext]).
 * @param switching уже идёт переключение (ручное или предыдущее автоматическое).
 */
fun shouldAutoAdvance(
    enabled: Boolean,
    durationMs: Long,
    hasNext: Boolean,
    switching: Boolean,
): Boolean = enabled && durationMs > 0L && hasNext && !switching
