package com.example.myapplication.data.models

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Единая точка правды о шкале рейтинга.
 *
 * Отображение/домен: Float 0.0..10.0 с одной цифрой после запятой (0 = не оценено).
 * Хранение (SQLite и Supabase, колонка INTEGER): значение × 10, т.е. 0..100 — схемы БД
 * менять не пришлось.
 *
 * Легаси: до перехода приложение хранило 5-звёздочный Int 1..5. Локальная БД
 * гарантированно переведена миграцией 8.sqm, поэтому обычное чтение ([storedToDisplay])
 * чистое — иначе честные оценки 0.1..0.9 (хранятся как 1..9) удваивались бы.
 * Эвристика «1..9 = легаси-звёзды» осталась только на границах, где легаси реально
 * встречается: импорт старых бэкапов и pull устаревших облачных строк
 * ([fromImportedStored], normalizeRemoteRating в SyncRepository).
 */
object RatingScale {
    const val MAX_DISPLAY = 10f

    /** Хранимое значение (0..100) → отображаемое 0.0..10.0. Без легаси-эвристик. */
    fun storedToDisplay(stored: Int): Float =
        if (stored <= 0) 0f else (stored / 10f).coerceAtMost(MAX_DISPLAY)

    fun storedToDisplay(stored: Long): Float = storedToDisplay(stored.toInt())

    /**
     * Импорт внешних данных (старые .db-бэкапы, легаси-JSON): значения 1..9 считаем
     * 5-звёздочными (старые форматы дробных не писали) и конвертируем ×2.
     */
    fun fromImportedStored(stored: Int): Float = when {
        stored <= 0 -> 0f
        stored < 10 -> (stored * 2f).coerceAtMost(MAX_DISPLAY)
        else -> (stored / 10f).coerceAtMost(MAX_DISPLAY)
    }

    /** Отображаемое 0.0..10.0 → хранимое 0..100 (одна десятая = 1 единица). */
    fun displayToStored(value: Float): Int =
        (value.coerceIn(0f, MAX_DISPLAY) * 10).roundToInt()

    /**
     * Рейтинг из внешних API → 10-балльная шкала.
     * AniList отдаёт 0..100, Shikimori/Jikan — 0..10 (целое).
     */
    fun fromApi(apiRating: Int?): Float {
        val r = apiRating ?: return 0f
        if (r <= 0) return 0f
        return if (r > 10) (r / 10f).coerceAtMost(MAX_DISPLAY) else r.toFloat()
    }

    /** «7.3» / "7.3" — единый формат вывода с одной цифрой после запятой. */
    fun format(value: Float): String = String.format(Locale.US, "%.1f", value)
}
