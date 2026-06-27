package com.example.myapplication.sync.supabase

import android.util.Log
import com.example.myapplication.data.local.AnimeDatabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class AnimeRemoteDto(
    val id: String,
    val user_id: String,
    val title: String,
    val image_path: String?,
    val episodes: Int,
    val rating: Int,
    val status: String,
    val is_favorite: Boolean,
    val order_index: Int,
    val date_added: Long,
    val category_type: String,
    val comment: String,
    val is_ai_recommendation: Boolean,
    val anilist_id: Int?,
    val mal_id: Int?,
    val shikimori_id: Int?,
    val anilist_not_found_at: Long?,
    val mal_not_found_at: Long?,
    val shikimori_not_found_at: Long?,
    val is_private: Boolean,
    val encryption_iv: String?,
    val created_at: Long,
    val updated_at: String,
    val deleted_at: Long?,
    val media_type: String = "ANIME"
)

@Serializable
data class AnimeTagRemoteDto(
    val anime_id: String,
    val user_id: String,
    val tag: String,
    val updated_at: String,
    val is_deleted: Boolean = false
)


class SyncRepository(
    private val db: AnimeDatabase,
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val attachmentSyncManager: AttachmentSyncManager
) {
    private val syncPrefs =
        attachmentSyncManager.context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)

    suspend fun pushPendingChanges(): PushResult = withContext(Dispatchers.IO) {
        if (!syncPrefs.getBoolean("retroactive_upload_done_v5", false)) {
            attachmentSyncManager.retroactivelyUploadCachedAttachments()
            syncPrefs.edit().putBoolean("retroactive_upload_done_v5", true).apply()
        }

        val userId = authRepository.currentUserId ?: return@withContext PushResult(0, "Not signed in")
        Log.d(TAG, "Push for user_id=$userId, pending=${db.animeQueries.selectPendingSync().executeAsList().size}")
        ensureInitialLocalUpload(userId)

        val pendingAnime = db.animeQueries.selectPendingSync().executeAsList()
        if (pendingAnime.isEmpty()) return@withContext PushResult(0, null)

        val syncedIds = mutableListOf<String>()
        val dtos = pendingAnime.map { anime ->
            var finalImagePath = anime.imagePath
            
            if (finalImagePath != null &&
                !finalImagePath.startsWith("http") &&
                !finalImagePath.startsWith("collection-attachment://")
            ) {
                val cloudUrl = attachmentSyncManager.uploadLocalImageFile(anime.id, finalImagePath)
                if (cloudUrl != null) {
                    finalImagePath = cloudUrl
                    db.animeQueries.updateAnimeImagePath(imagePath = cloudUrl, id = anime.id)
                }
            }

            syncedIds.add(anime.id)

            AnimeRemoteDto(
                id = anime.id,
                user_id = userId,
                title = anime.title,
                image_path = finalImagePath,
                episodes = anime.episodes.toInt(),
                rating = anime.rating.toInt(),
                status = anime.status,
                is_favorite = anime.isFavorite > 0,
                order_index = anime.orderIndex.toInt(),
                date_added = anime.dateAdded,
                category_type = anime.categoryType,
                comment = anime.comment,
                is_ai_recommendation = anime.isAiRecommendation > 0,
                anilist_id = anime.anilist_id?.toInt(),
                mal_id = anime.mal_id?.toInt(),
                shikimori_id = anime.shikimori_id?.toInt(),
                anilist_not_found_at = anime.anilist_not_found_at,
                mal_not_found_at = anime.mal_not_found_at,
                shikimori_not_found_at = anime.shikimori_not_found_at,
                is_private = anime.isPrivate > 0,
                encryption_iv = anime.encryptionIv,
                created_at = anime.dateAdded,
                updated_at = java.time.Instant.ofEpochMilli(anime.updatedAt).toString(),
                deleted_at = anime.deletedAt,
                media_type = anime.mediaType
            )
        }

        if (dtos.isEmpty()) return@withContext PushResult(0, null)

        try {
            supabase.postgrest["anime"].upsert(dtos)
            db.transaction {
                syncedIds.forEach { id ->
                    db.animeQueries.markAnimeSynced(id)
                }
            }
            syncPrefs.edit().putBoolean(initialUploadKey(userId), true).apply()
            Log.i(TAG, "Pushed ${dtos.size} anime row(s) to cloud")

            pushTagsForAnime(userId, pendingAnime, syncedIds)
            return@withContext PushResult(dtos.size, null)
        } catch (e: Exception) {
            Log.e(TAG, "Push anime failed: ${e.message}", e)
            return@withContext PushResult(0, e.message ?: e.toString())
        }
    }

    private suspend fun pushTagsForAnime(
        userId: String,
        pendingAnime: List<com.example.myapplication.data.local.Anime>,
        syncedIds: List<String>,
    ) {
        val pendingTagsDtos = mutableListOf<AnimeTagRemoteDto>()
        pendingAnime.filter { it.id in syncedIds }.forEach { anime ->
            val tags = db.animeQueries.getAnimeTags(anime.id).executeAsList()
            tags.forEach { tag ->
                pendingTagsDtos.add(
                    AnimeTagRemoteDto(
                        anime_id = anime.id,
                        user_id = userId,
                        tag = tag,
                        updated_at = java.time.Instant.now().toString()
                    )
                )
            }
        }
        if (pendingTagsDtos.isEmpty()) return
        try {
            supabase.postgrest["anime_tags"].upsert(pendingTagsDtos)
        } catch (e: Exception) {
            Log.e(TAG, "Push tags failed: ${e.message}", e)
        }
    }

    data class PushResult(val pushedCount: Int, val error: String?)
    data class PullResult(val pulledCount: Int, val error: String?)

    suspend fun pullRemoteChanges(): PullResult = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUserId ?: return@withContext PullResult(0, "Not signed in")
        val cursorMs = db.syncQueries.getCursor("anime").executeAsOneOrNull() ?: 0L
        val cursorIso = java.time.Instant.ofEpochMilli(cursorMs).toString()

        try {
            val remoteChanges = supabase.postgrest["anime"]
                .select {
                    filter {
                        eq("user_id", userId)
                        gt("updated_at", cursorIso)
                    }
                }
                .decodeList<AnimeRemoteDto>()

            if (remoteChanges.isEmpty()) return@withContext PullResult(0, null)

            var maxUpdatedAt = cursorMs
            val attachmentsToDownload = mutableListOf<Pair<String, String>>()
            db.transaction {
                remoteChanges.forEach { remote ->
                    val remoteUpdatedMs = java.time.Instant.parse(remote.updated_at).toEpochMilli()
                    if (remoteUpdatedMs > maxUpdatedAt) {
                        maxUpdatedAt = remoteUpdatedMs
                    }

                    val local = db.animeQueries.getAnimeById(remote.id).executeAsOneOrNull()
                    
                    // LWW: local dirty wins
                    if (local != null && local.sync_status != "SYNCED") {
                        return@forEach
                    }

                    // Soft delete: only apply if remote is newer than local
                    if (remote.deleted_at != null) {
                        if (local == null || remoteUpdatedMs > local.updatedAt) {
                            db.animeQueries.deleteAnime(remote.id)
                        }
                        return@forEach
                    }

                    db.animeQueries.upsertFromSync(
                        id = remote.id,
                        title = remote.title,
                        imagePath = remote.image_path,
                        episodes = remote.episodes.toLong(),
                        rating = remote.rating.toLong(),
                        status = remote.status,
                        isFavorite = if (remote.is_favorite) 1L else 0L,
                        updatedAt = remoteUpdatedMs,
                        orderIndex = remote.order_index.toLong(),
                        dateAdded = remote.date_added,
                        categoryType = remote.category_type,
                        comment = remote.comment,
                        isAiRecommendation = if (remote.is_ai_recommendation) 1L else 0L,
                        anilist_id = remote.anilist_id?.toLong(),
                        mal_id = remote.mal_id?.toLong(),
                        shikimori_id = remote.shikimori_id?.toLong(),
                        anilist_not_found_at = remote.anilist_not_found_at,
                        mal_not_found_at = remote.mal_not_found_at,
                        shikimori_not_found_at = remote.shikimori_not_found_at,
                        isPrivate = if (remote.is_private) 1L else 0L,
                        encryptionIv = remote.encryption_iv,
                        deletedAt = remote.deleted_at,
                        mediaType = remote.media_type
                    )
                    
                    val imagePath = remote.image_path
                    if (imagePath?.startsWith("collection-attachment://") == true) {
                        if (!attachmentSyncManager.hasLocalCopy(remote.id, imagePath)) {
                            attachmentsToDownload.add(remote.id to imagePath)
                        }
                    }
                }
                db.syncQueries.setCursor("anime", maxUpdatedAt)
            }
            attachmentsToDownload.forEach { (animeId, cloudUrl) ->
                attachmentSyncManager.downloadAndImportToCollection(animeId, cloudUrl)
            }

            pullRemoteTags(userId)
            Log.i(TAG, "Pulled ${remoteChanges.size} anime row(s) from cloud")
            return@withContext PullResult(remoteChanges.size, null)
        } catch (e: Exception) {
            Log.e(TAG, "Pull failed: ${e.message}", e)
            return@withContext PullResult(0, e.message ?: e.toString())
        }
    }

    /**
     * Guest/local-only rows stay SYNCED until first cloud upload. Queue them once when cloud is empty.
     */
    private suspend fun ensureInitialLocalUpload(userId: String) {
        val flagKey = initialUploadKey(userId)
        if (syncPrefs.getBoolean(flagKey, false)) return

        val localCount = db.animeQueries.getAnimeCount().executeAsOne()
        if (localCount == 0L) {
            syncPrefs.edit().putBoolean(flagKey, true).apply()
            return
        }

        if (remoteHasAnime(userId)) {
            syncPrefs.edit().putBoolean(flagKey, true).apply()
            return
        }

        Log.i(TAG, "Queueing $localCount local anime for first cloud upload")
        db.animeQueries.markAllAnimePendingUpload()
    }

    fun resetInitialUploadFlag(userId: String) {
        syncPrefs.edit().remove(initialUploadKey(userId)).apply()
    }

    suspend fun requeueLocalUploadIfCloudEmpty(userId: String) {
        if (db.animeQueries.getAnimeCount().executeAsOne() == 0L) return
        if (remoteHasAnime(userId)) return
        resetInitialUploadFlag(userId)
        ensureInitialLocalUpload(userId)
    }

    private fun initialUploadKey(userId: String) = "initial_local_upload_v1_$userId"

    private suspend fun pullRemoteTags(userId: String) {
        try {
            val tagCursorMs = db.syncQueries.getCursor("anime_tags").executeAsOneOrNull() ?: 0L
            val tagCursorIso = java.time.Instant.ofEpochMilli(tagCursorMs).toString()
            val remoteTags = supabase.postgrest["anime_tags"]
                .select {
                    filter {
                        eq("user_id", userId)
                        gt("updated_at", tagCursorIso)
                    }
                }
                .decodeList<AnimeTagRemoteDto>()

            if (remoteTags.isEmpty()) return

            var tagMaxUpdatedAt = tagCursorMs
            db.transaction {
                val tagsByAnime = remoteTags.groupBy { it.anime_id }
                for ((animeId, tags) in tagsByAnime) {
                    db.animeQueries.deleteAnimeTags(animeId)
                    tags.filter { !it.is_deleted }.forEach {
                        db.animeQueries.insertAnimeTag(animeId, it.tag)
                    }
                }
                remoteTags.forEach {
                    val remoteUpdatedMs = java.time.Instant.parse(it.updated_at).toEpochMilli()
                    if (remoteUpdatedMs > tagMaxUpdatedAt) {
                        tagMaxUpdatedAt = remoteUpdatedMs
                    }
                }
                db.syncQueries.setCursor("anime_tags", tagMaxUpdatedAt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pull tags skipped: ${e.message}", e)
        }
    }

    private suspend fun remoteHasAnime(userId: String): Boolean {
        return try {
            supabase.postgrest["anime"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeList<AnimeRemoteDto>()
                .isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "remoteHasAnime failed", e)
            false
        }
    }

    private companion object {
        private const val TAG = "SyncRepository"
    }
}
