package com.example.myapplication.domain.recommendations

import com.example.myapplication.data.models.Anime
import kotlin.math.abs

/**
 * Вектор «жанр → сила предпочтения» по коллекции пользователя.
 *
 * Оценки выше нейтральной (3 из 5) тянут жанры тайтла вверх, ниже — гасят.
 * Избранное добавляет фиксированный бонус. Результат нормализован в [-1, 1].
 * Ключи — теги приложения (id из GenreRepository), чистый Kotlin без Android.
 */
class GenreAffinityCalculator {

    fun calculate(library: List<Anime>): Map<String, Float> {
        val raw = HashMap<String, Float>()
        for (anime in library) {
            if (anime.tags.isEmpty()) continue
            var weight = 0f
            if (anime.rating > 0) weight += anime.rating - NEUTRAL_RATING
            if (anime.isFavorite) weight += FAVORITE_BONUS
            if (weight == 0f) continue
            for (tag in anime.tags) {
                raw[tag] = (raw[tag] ?: 0f) + weight
            }
        }
        if (raw.isEmpty()) return emptyMap()
        val maxAbs = raw.values.maxOf { abs(it) }.takeIf { it > 0f } ?: return emptyMap()
        return raw.mapValues { (_, v) -> (v / maxAbs).coerceIn(-1f, 1f) }
    }

    companion object {
        /** Нейтральная точка 10-балльной шкалы: ниже — антипатия, выше — симпатия. */
        const val NEUTRAL_RATING = 6f
        /** Бонус избранного в единицах 10-балльной шкалы (прежний 1★ ≈ 2 балла). */
        const val FAVORITE_BONUS = 2f
    }
}
