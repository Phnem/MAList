package com.example.myapplication.domain.stats

import android.content.Context
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

private const val STATS_PHRASES_ASSET = "stats_phrases.txt"

/**
 * Каталог фраз из assets; чтение и парсинг только на [Dispatchers.IO], однократно.
 */
class StatsPhraseCatalog(
    appContext: Context
) {
    private val assets = appContext.applicationContext.assets
    private val mutex = Mutex()
    @Volatile
    private var linesByKey: Map<StatsPhraseGroupKey, List<StatsPhraseLine>>? = null

    suspend fun ensureLoaded() {
        mutex.withLock {
            if (linesByKey != null) return@withLock
            linesByKey = withContext(Dispatchers.IO) {
                assets.open(STATS_PHRASES_ASSET).use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                    val sequence = generateSequence { reader.readLine() }
                    StatsPhraseLineParser.parseLines(sequence)
                }
            }
        }
    }

    suspend fun phrasesFor(language: AppLanguage, bucketTag: String): List<StatsPhraseLine> {
        ensureLoaded()
        val key = StatsPhraseGroupKey(language, bucketTag)
        return linesByKey!!.get(key).orEmpty()
    }
}
