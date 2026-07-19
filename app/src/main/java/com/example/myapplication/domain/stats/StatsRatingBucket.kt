package com.example.myapplication.domain.stats

/**
 * Тег корзины по среднему рейтингу (звёзды 0…5): ≤2 → 02, (2;4] → 24, >4 → 45.
 */
object StatsRatingBucket {
    const val TAG_02 = "02"
    const val TAG_24 = "24"
    const val TAG_45 = "45"

    // Пороги в 10-балльной шкале (исторические тэги 0-2/2-4/4-5 звёзд → ×2).
    fun tagForAverage(avgRating: Double): String = when {
        avgRating <= 4.0 -> TAG_02
        avgRating <= 8.0 -> TAG_24
        else -> TAG_45
    }
}
