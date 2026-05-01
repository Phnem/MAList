package com.example.myapplication.domain.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ResolveStatsFooterPhraseUseCase(
    private val catalog: StatsPhraseCatalog,
    private val settingsDataStore: DataStore<Preferences>
) {
    /** Прогрев без выбора фразы (фон при старте Home). */
    suspend fun warmCatalog() {
        catalog.ensureLoaded()
    }

    suspend operator fun invoke(
        language: AppLanguage,
        avgRating: Double,
        totalEpisodes: Int,
        ratingFormattedForUi: String
    ): String = withContext(Dispatchers.IO) {
        val bucketTag = StatsRatingBucket.tagForAverage(avgRating)
        val phrases = catalog.phrasesFor(language, bucketTag)
        if (phrases.isEmpty()) return@withContext ""

        val prefKey = footerNextIndexKey(language, bucketTag)
        val prefs = settingsDataStore.data.first()
        val currentIndex = prefs[prefKey] ?: 0
        val phrase = phrases[currentIndex % phrases.size]
        val result = phrase.format(ratingFormattedForUi, totalEpisodes)
        val nextIndex = (currentIndex + 1) % phrases.size
        settingsDataStore.edit { it[prefKey] = nextIndex }
        result
    }
}

private fun footerNextIndexKey(language: AppLanguage, bucketTag: String) =
    intPreferencesKey("stats_footer_next_${language.name}_$bucketTag")
