package com.example.myapplication.localplayer.domain

/** Тип пропускаемого отрезка. Пока используем только опенинг, но модель общая на будущее. */
@kotlinx.serialization.Serializable
enum class SkipKind { OPENING, ENDING, RECAP, MIXED }

/**
 * Отрезок для пропуска (опенинг/эндинг), тайминги в миллисекундах относительно текущего эпизода.
 * [endMs] — точка, на которую перескакиваем.
 */
data class SkipSegment(
    val startMs: Long,
    val endMs: Long,
    val kind: SkipKind,
)

/**
 * Источник таймингов пропуска для конкретного эпизода. Абстракция специально отделена от плеера,
 * чтобы позже можно было подменить/дополнить источник (ручные тайминги, ИИ-детект тишины/титров)
 * без правок UI. Реализация по умолчанию — [AniSkipSegmentProvider].
 *
 * Возвращает пустой список, если таймингов нет (нет malId, эпизод не в базе, сеть недоступна) —
 * тогда кнопка/автоскип просто не появляются.
 */
interface SkipSegmentProvider {
    /**
     * [episodeNumber] — распознанный (возможно абсолютный/сквозной) номер серии из файла; реализация
     * при необходимости сама сопоставит его конкретному сезону франшизы (см. [FranchiseEpisodeMapper]).
     */
    suspend fun fetch(anilistId: Int?, malId: Int?, episodeNumber: Int?, durationMs: Long): List<SkipSegment>
}
