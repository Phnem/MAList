package com.example.myapplication.media.player

import com.example.myapplication.media.source.VetroVideo
import java.util.concurrent.ConcurrentHashMap

/** In-memory TTL cache for resolved stream URLs (~4h). */
object VetroVideoCache {
    private const val TTL_MS = 4L * 60 * 60 * 1000

    private data class Entry(val video: VetroVideo, val expiresAt: Long)

    private val map = ConcurrentHashMap<String, Entry>()

    fun key(animeId: String, episode: Int, hoster: String, label: String): String =
        "$animeId|$episode|$hoster|$label"

    fun get(key: String): VetroVideo? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() > e.expiresAt) {
            map.remove(key)
            return null
        }
        return e.video
    }

    fun put(key: String, video: VetroVideo) {
        map[key] = Entry(video, System.currentTimeMillis() + TTL_MS)
    }

    fun clear() = map.clear()
}
