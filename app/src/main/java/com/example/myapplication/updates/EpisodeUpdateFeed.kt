package com.example.myapplication.updates

import android.util.Log
import com.example.myapplication.data.models.AnimeUpdate

/** Single read/merge/auto-apply/write policy for the shared anime_update feed. */
internal suspend fun publishEpisodeUpdates(
    detected: List<AnimeUpdate>,
    getExisting: () -> List<AnimeUpdate>,
    applyEpisodes: suspend (AnimeUpdate) -> Unit,
    setUpdates: suspend (List<AnimeUpdate>) -> Unit,
): List<AnimeUpdate> {
    val existing = getExisting()
    val fresh = detected
        .distinctBy { it.animeId }
        .filter { candidate ->
            existing.none { it.animeId == candidate.animeId && it.newEpisodes == candidate.newEpisodes }
        }
    fresh.forEach { update ->
        runCatching { applyEpisodes(update) }
            .onFailure { Log.w(TAG, "Auto-apply failed for ${update.animeId}: ${it.message}") }
    }
    val freshIds = fresh.mapTo(HashSet()) { it.animeId }
    val kept = existing.filter { it.animeId !in freshIds }
    setUpdates((fresh + kept).take(MAX_UPDATE_ROWS))
    return fresh
}

private const val TAG = "EpisodeUpdateFeed"
private const val MAX_UPDATE_ROWS = 30
