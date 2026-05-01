package com.example.myapplication.domain.stats

enum class StatsSubstitutionKind {
    Rating,
    Series
}

data class StatsPhraseLine(
    val template: String,
    val substitutionKind: StatsSubstitutionKind
) {
    fun format(ratingFormatted: String, totalEpisodes: Int): String {
        val value = when (substitutionKind) {
            StatsSubstitutionKind.Rating -> ratingFormatted
            StatsSubstitutionKind.Series -> totalEpisodes.toString()
        }
        return template.replace("{_}", value)
    }
}
