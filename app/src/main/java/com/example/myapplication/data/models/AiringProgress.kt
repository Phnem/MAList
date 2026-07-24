package com.example.myapplication.data.models

/**
 * Прогресс текущего выходящего сезона тайтла (результат батч-проверки серий).
 * Наличие записи = тайтл сейчас «в процессе»: карточка показывает прогресс-бар
 * вида «S3 2/12».
 */
data class AiringProgress(
    val animeId: String,
    /** Номер выходящего сезона в цепочке франшизы (1 = первый); null — источник без графа франшизы. */
    val seasonNumber: Int?,
    /** Сколько серий сезона уже вышло. */
    val airedEpisodes: Int,
    /** Заявленное число серий сезона; null — ещё не анонсировано. */
    val totalEpisodes: Int?,
    val updatedAt: Long,
)
