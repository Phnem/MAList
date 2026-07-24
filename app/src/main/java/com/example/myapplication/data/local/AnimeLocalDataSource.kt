package com.example.myapplication.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.myapplication.data.local.AnimeDatabase
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.data.models.RatingScale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeLocalDataSource(
    private val factory: SQLDelightDatabaseFactory,
    private val mirrorCoordinator: DeveloperMirrorCoordinator
) {

    private fun db(): AnimeDatabase = factory.getDatabase()

    /**
     * Реактивный поток; при reconnectDatabase() переподписывается на новое подключение (hot swap).
     */
    fun observeAllAnime(filterType: MediaType? = null): Flow<List<Anime>> = factory.dbConnectionTrigger.flatMapLatest {
        if (filterType == null) {
            db().animeQueries.getAllAnimeWithTagsConcat().asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows ->
                    rows.map { row ->
                        Anime(
                            id = row.id,
                            title = row.title,
                            titleEn = row.title_en,
                            titleRu = row.title_ru,
                            episodes = row.episodes.toInt(),
                            rating = RatingScale.storedToDisplay(row.rating),
                            imageFileName = row.imagePath,
                            orderIndex = row.orderIndex.toInt(),
                            dateAdded = row.dateAdded,
                            isFavorite = row.isFavorite == 1L,
                            tags = parseTagsConcat(row.tagsConcat),
                            categoryType = row.categoryType ?: "",
                            comment = row.comment,
                            anilistId = row.anilist_id?.toInt(),
                            malId = row.mal_id?.toInt(),
                            shikimoriId = row.shikimori_id?.toInt(),
                            anilistNotFoundAt = row.anilist_not_found_at,
                            malNotFoundAt = row.mal_not_found_at,
                            shikimoriNotFoundAt = row.shikimori_not_found_at,
                            mediaType = runCatching { MediaType.valueOf(row.mediaType) }.getOrDefault(MediaType.ANIME)
                        )
                    }
                }
        } else {
            db().animeQueries.getAnimeWithTagsConcatByType(filterType.name).asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows ->
                    rows.map { row ->
                        Anime(
                            id = row.id,
                            title = row.title,
                            titleEn = row.title_en,
                            titleRu = row.title_ru,
                            episodes = row.episodes.toInt(),
                            rating = RatingScale.storedToDisplay(row.rating),
                            imageFileName = row.imagePath,
                            orderIndex = row.orderIndex.toInt(),
                            dateAdded = row.dateAdded,
                            isFavorite = row.isFavorite == 1L,
                            tags = parseTagsConcat(row.tagsConcat),
                            categoryType = row.categoryType ?: "",
                            comment = row.comment,
                            anilistId = row.anilist_id?.toInt(),
                            malId = row.mal_id?.toInt(),
                            shikimoriId = row.shikimori_id?.toInt(),
                            anilistNotFoundAt = row.anilist_not_found_at,
                            malNotFoundAt = row.mal_not_found_at,
                            shikimoriNotFoundAt = row.shikimori_not_found_at,
                            mediaType = runCatching { MediaType.valueOf(row.mediaType) }.getOrDefault(MediaType.ANIME)
                        )
                    }
                }
        }
    }

    /** Закрывает старый коннект и открывает новый (после миграции .copyTo). */
    fun reconnectDatabase() = factory.reconnectDatabase()

    fun getAnimeCount(): Int {
        return db().animeQueries.getAnimeCount().executeAsOne().toInt()
    }

    fun getMaxOrderIndex(): Int {
        return db().animeQueries.getMaxOrderIndex().executeAsOne().toInt()
    }

    fun getAllAnimeList(): List<Anime> {
        return db().animeQueries.getAllAnime()
            .executeAsList()
            .map { row ->
                mapRowToAnime(
                    id = row.id,
                    title = row.title,
                    titleEn = row.title_en,
                    titleRu = row.title_ru,
                    imagePath = row.imagePath,
                    episodes = row.episodes,
                    rating = row.rating,
                    orderIndex = row.orderIndex,
                    dateAdded = row.dateAdded,
                    isFavorite = row.isFavorite,
                    categoryType = row.categoryType,
                    comment = row.comment,
                    anilistId = row.anilist_id,
                    malId = row.mal_id,
                    shikimoriId = row.shikimori_id,
                    anilistNotFoundAt = row.anilist_not_found_at,
                    shikimoriNotFoundAt = row.shikimori_not_found_at,
                    mediaType = row.mediaType
                )
            }
    }

    fun getAnimeById(id: String): Anime? {
        return db().animeQueries
            .getAnimeById(id)
            .executeAsOneOrNull()
            ?.let { row ->
                mapRowToAnime(
                    id = row.id,
                    title = row.title,
                    titleEn = row.title_en,
                    titleRu = row.title_ru,
                    imagePath = row.imagePath,
                    episodes = row.episodes,
                    rating = row.rating,
                    orderIndex = row.orderIndex,
                    dateAdded = row.dateAdded,
                    isFavorite = row.isFavorite,
                    categoryType = row.categoryType,
                    comment = row.comment,
                    anilistId = row.anilist_id,
                    malId = row.mal_id,
                    shikimoriId = row.shikimori_id,
                    anilistNotFoundAt = row.anilist_not_found_at,
                    shikimoriNotFoundAt = row.shikimori_not_found_at,
                    mediaType = row.mediaType
                )
            }
    }

    suspend fun updateAnimeComment(id: String, comment: String) {
        db().animeQueries.updateAnimeComment(comment, System.currentTimeMillis(), id)
        mirrorCoordinator.requestExportIfEnabled()
    }

    private fun mapRowToAnime(
        id: String,
        title: String,
        titleEn: String? = null,
        titleRu: String? = null,
        imagePath: String?,
        episodes: Long,
        rating: Long,
        orderIndex: Long,
        dateAdded: Long,
        isFavorite: Long,
        categoryType: String?,
        comment: String = "",
        anilistId: Long? = null,
        malId: Long? = null,
        shikimoriId: Long? = null,
        anilistNotFoundAt: Long? = null,
        malNotFoundAt: Long? = null,
        shikimoriNotFoundAt: Long? = null,
        mediaType: String = MediaType.ANIME.name
    ): Anime = Anime(
        id = id,
        title = title,
        titleEn = titleEn,
        titleRu = titleRu,
        episodes = episodes.toInt(),
        rating = RatingScale.storedToDisplay(rating),
        imageFileName = imagePath,
        orderIndex = orderIndex.toInt(),
        dateAdded = dateAdded,
        isFavorite = isFavorite == 1L,
        tags = getTagsForAnime(id),
        categoryType = categoryType ?: "",
        comment = comment,
        anilistId = anilistId?.toInt(),
        malId = malId?.toInt(),
        shikimoriId = shikimoriId?.toInt(),
        anilistNotFoundAt = anilistNotFoundAt,
        malNotFoundAt = malNotFoundAt,
        shikimoriNotFoundAt = shikimoriNotFoundAt,
        mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.ANIME)
    )

    suspend fun insertAnime(anime: Anime) {
        db().animeQueries.transaction {
            db().animeQueries.insertAnime(
                id = anime.id,
                title = anime.title,
                imagePath = anime.imageFileName,
                episodes = anime.episodes.toLong(),
                rating = RatingScale.displayToStored(anime.rating).toLong(),
                status = "watching",
                isFavorite = if (anime.isFavorite) 1L else 0L,
                updatedAt = System.currentTimeMillis(),
                orderIndex = anime.orderIndex.toLong(),
                dateAdded = anime.dateAdded,
                categoryType = anime.categoryType,
                comment = anime.comment,
                isAiRecommendation = 0L,
                anilist_id = anime.anilistId?.toLong(),
                mal_id = anime.malId?.toLong(),
                shikimori_id = anime.shikimoriId?.toLong(),
                anilist_not_found_at = anime.anilistNotFoundAt,
                mal_not_found_at = anime.malNotFoundAt,
                shikimori_not_found_at = anime.shikimoriNotFoundAt,
                isPrivate = 0L,
                encryptionIv = null,
                deletedAt = null,
                mediaType = anime.mediaType.name,
                title_en = anime.titleEn,
                title_ru = anime.titleRu
            )

            // Insert tags
            anime.tags.forEach { tag ->
                db().animeQueries.insertAnimeTag(
                    anime_id = anime.id,
                    tag = tag
                )
            }
        }
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun updateAnime(anime: Anime) {
        db().animeQueries.transaction {
            db().animeQueries.updateAnime(
                title = anime.title,
                imagePath = anime.imageFileName,
                episodes = anime.episodes.toLong(),
                rating = RatingScale.displayToStored(anime.rating).toLong(),
                status = "watching",
                isFavorite = if (anime.isFavorite) 1L else 0L,
                updatedAt = System.currentTimeMillis(),
                orderIndex = anime.orderIndex.toLong(),
                categoryType = anime.categoryType,
                comment = anime.comment,
                isAiRecommendation = 0L,
                anilist_id = anime.anilistId?.toLong(),
                mal_id = anime.malId?.toLong(),
                shikimori_id = anime.shikimoriId?.toLong(),
                anilist_not_found_at = anime.anilistNotFoundAt,
                mal_not_found_at = anime.malNotFoundAt,
                shikimori_not_found_at = anime.shikimoriNotFoundAt,
                isPrivate = 0L,
                encryptionIv = null,
                deletedAt = null,
                mediaType = anime.mediaType.name,
                id = anime.id
            )

            // Update tags
            db().animeQueries.deleteAnimeTags(anime.id)
            anime.tags.forEach { tag ->
                db().animeQueries.insertAnimeTag(
                    anime_id = anime.id,
                    tag = tag
                )
            }
        }
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun deleteAnime(id: String) {
        db().animeQueries.transaction {
            db().animeQueries.deleteAnimeTags(id)
            db().animeQueries.deleteAnime(id)
        }
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun insertAllAnime(list: List<Anime>) {
        db().animeQueries.transaction {
            list.forEach { anime ->
                db().animeQueries.insertAnime(
                    id = anime.id,
                    title = anime.title,
                    imagePath = anime.imageFileName,
                    episodes = anime.episodes.toLong(),
                    rating = RatingScale.displayToStored(anime.rating).toLong(),
                    status = "watching",
                    isFavorite = if (anime.isFavorite) 1L else 0L,
                    updatedAt = System.currentTimeMillis(),
                    orderIndex = anime.orderIndex.toLong(),
                    dateAdded = anime.dateAdded,
                    categoryType = anime.categoryType,
                    comment = anime.comment,
                    isAiRecommendation = 0L,
                    anilist_id = anime.anilistId?.toLong(),
                    mal_id = anime.malId?.toLong(),
                    shikimori_id = anime.shikimoriId?.toLong(),
                    anilist_not_found_at = anime.anilistNotFoundAt,
                    mal_not_found_at = anime.malNotFoundAt,
                    shikimori_not_found_at = anime.shikimoriNotFoundAt,
                    isPrivate = 0L,
                    encryptionIv = null,
                    deletedAt = null,
                    mediaType = anime.mediaType.name,
                    title_en = anime.titleEn,
                    title_ru = anime.titleRu
                )
                anime.tags.forEach { tag ->
                    db().animeQueries.insertAnimeTag(
                        anime_id = anime.id,
                        tag = tag
                    )
                }
            }
        }
        mirrorCoordinator.requestExportIfEnabled()
    }

    fun observeUpdates(): Flow<List<AnimeUpdate>> = factory.dbConnectionTrigger.flatMapLatest {
        db().animeQueries.getAllUpdates()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    AnimeUpdate(
                        animeId = row.anime_id,
                        title = row.title,
                        currentEpisodes = row.current_episodes.toInt(),
                        newEpisodes = row.new_episodes.toInt(),
                        source = row.source,
                    )
                }
            }
    }

    fun getUpdates(): List<AnimeUpdate> {
        return db().animeQueries.getAllUpdates()
            .executeAsList()
            .map { row ->
                AnimeUpdate(
                    animeId = row.anime_id,
                    title = row.title,
                    currentEpisodes = row.current_episodes.toInt(),
                    newEpisodes = row.new_episodes.toInt(),
                    source = row.source
                )
            }
    }

    fun getIgnoredMap(): Map<String, Int> {
        return db().animeQueries.getIgnoredMap()
            .executeAsList()
            .associate { row -> row.anime_id to row.new_episodes.toInt() }
    }

    suspend fun setUpdates(updates: List<AnimeUpdate>) {
        db().animeQueries.transaction {
            db().animeQueries.deleteAllUpdates()
            updates.forEach { u ->
                db().animeQueries.insertUpdate(
                    anime_id = u.animeId,
                    title = u.title,
                    current_episodes = u.currentEpisodes.toLong(),
                    new_episodes = u.newEpisodes.toLong(),
                    source = u.source
                )
            }
        }
        mirrorCoordinator.requestExportIfEnabled()
    }

    /** Прогресс выходящих сезонов (карточки «в процессе»): anime_id → снимок. */
    fun observeAiringProgress(): Flow<Map<String, com.example.myapplication.data.models.AiringProgress>> =
        factory.dbConnectionTrigger.flatMapLatest {
            db().airingProgressQueries.getAllAiringProgress()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows ->
                    rows.associate { row ->
                        row.anime_id to com.example.myapplication.data.models.AiringProgress(
                            animeId = row.anime_id,
                            seasonNumber = row.season_number?.toInt(),
                            airedEpisodes = row.aired_episodes.toInt(),
                            totalEpisodes = row.total_episodes?.toInt(),
                            updatedAt = row.updated_at,
                        )
                    }
                }
        }

    /** Разовый снимок прогресса сезонов (прошлый проход) — для закрытия завершённых. */
    fun getAiringProgressSnapshot(): Map<String, com.example.myapplication.data.models.AiringProgress> =
        db().airingProgressQueries.getAllAiringProgress()
            .executeAsList()
            .associate { row ->
                row.anime_id to com.example.myapplication.data.models.AiringProgress(
                    animeId = row.anime_id,
                    seasonNumber = row.season_number?.toInt(),
                    airedEpisodes = row.aired_episodes.toInt(),
                    totalEpisodes = row.total_episodes?.toInt(),
                    updatedAt = row.updated_at,
                )
            }

    /** Полная перезапись снимка прогресса сезонов (каждый проход проверки авторитетен). */
    suspend fun setAiringProgress(items: List<com.example.myapplication.data.models.AiringProgress>) {
        db().airingProgressQueries.transaction {
            db().airingProgressQueries.deleteAllAiringProgress()
            items.forEach { p ->
                db().airingProgressQueries.upsertAiringProgress(
                    anime_id = p.animeId,
                    season_number = p.seasonNumber?.toLong(),
                    aired_episodes = p.airedEpisodes.toLong(),
                    total_episodes = p.totalEpisodes?.toLong(),
                    updated_at = p.updatedAt,
                )
            }
        }
    }

    suspend fun addIgnored(animeId: String, newEpisodes: Int) {
        db().animeQueries.setIgnored(
            anime_id = animeId,
            new_episodes = newEpisodes.toLong()
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun removeUpdate(animeId: String) {
        db().animeQueries.deleteUpdateByAnimeId(animeId)
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun setAnilistId(animeId: String, anilistId: Int) {
        db().animeQueries.setAnilistId(
            anilist_id = anilistId.toLong(),
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun setMalId(animeId: String, malId: Int) {
        db().animeQueries.setMalId(
            mal_id = malId.toLong(),
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun setShikimoriId(animeId: String, shikimoriId: Int) {
        db().animeQueries.setShikimoriId(
            shikimori_id = shikimoriId.toLong(),
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    /** Строки без EN-названия и без отметки попытки — кандидаты для обогащения (Stage 7/8). */
    fun getAnimeNeedingTitleEn(limit: Int): List<Anime> {
        return db().animeQueries.selectNeedingTitleEn(limit.toLong())
            .executeAsList()
            .map { row ->
                mapRowToAnime(
                    id = row.id,
                    title = row.title,
                    titleEn = row.title_en,
                    titleRu = row.title_ru,
                    imagePath = row.imagePath,
                    episodes = row.episodes,
                    rating = row.rating,
                    orderIndex = row.orderIndex,
                    dateAdded = row.dateAdded,
                    isFavorite = row.isFavorite,
                    categoryType = row.categoryType,
                    comment = row.comment,
                    anilistId = row.anilist_id,
                    malId = row.mal_id,
                    shikimoriId = row.shikimori_id,
                    anilistNotFoundAt = row.anilist_not_found_at,
                    shikimoriNotFoundAt = row.shikimori_not_found_at,
                    mediaType = row.mediaType
                )
            }
    }

    /** Сколько записей ещё ждут EN-названия (для прогресса дубляжа). */
    fun countAnimeNeedingTitleEn(): Int {
        return db().animeQueries.countNeedingTitleEn().executeAsOne().toInt()
    }

    /** Сброс отметок «проверено» у ненайденных — полный перескан дубляжа (dev-кнопка, Stage 9). */
    suspend fun resetTitleEnChecks() {
        db().animeQueries.resetTitleEnChecks(
            updatedAt = System.currentTimeMillis()
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    /** Записи без RU-названия и без отметки попытки — кандидаты обратного обогащения (Stage 10). */
    fun getAnimeNeedingTitleRu(limit: Int): List<Anime> {
        return db().animeQueries.selectNeedingTitleRu(limit.toLong())
            .executeAsList()
            .map { row ->
                mapRowToAnime(
                    id = row.id,
                    title = row.title,
                    titleEn = row.title_en,
                    titleRu = row.title_ru,
                    imagePath = row.imagePath,
                    episodes = row.episodes,
                    rating = row.rating,
                    orderIndex = row.orderIndex,
                    dateAdded = row.dateAdded,
                    isFavorite = row.isFavorite,
                    categoryType = row.categoryType,
                    comment = row.comment,
                    anilistId = row.anilist_id,
                    malId = row.mal_id,
                    shikimoriId = row.shikimori_id,
                    anilistNotFoundAt = row.anilist_not_found_at,
                    shikimoriNotFoundAt = row.shikimori_not_found_at,
                    mediaType = row.mediaType
                )
            }
    }

    /** Сколько записей ещё ждут RU-названия. */
    fun countAnimeNeedingTitleRu(): Int {
        return db().animeQueries.countNeedingTitleRu().executeAsOne().toInt()
    }

    suspend fun resetTitleRuChecks() {
        db().animeQueries.resetTitleRuChecks(
            updatedAt = System.currentTimeMillis()
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun setTitleRu(animeId: String, titleRu: String, atMillis: Long = System.currentTimeMillis()) {
        db().animeQueries.setTitleRu(
            title_ru = titleRu,
            title_ru_checked_at = atMillis,
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun markTitleRuChecked(animeId: String, atMillis: Long = System.currentTimeMillis()) {
        db().animeQueries.markTitleRuChecked(
            title_ru_checked_at = atMillis,
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun setTitleEn(animeId: String, titleEn: String, atMillis: Long = System.currentTimeMillis()) {
        db().animeQueries.setTitleEn(
            title_en = titleEn,
            title_en_checked_at = atMillis,
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun markTitleEnChecked(animeId: String, atMillis: Long = System.currentTimeMillis()) {
        db().animeQueries.markTitleEnChecked(
            title_en_checked_at = atMillis,
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun markAnilistNotFound(animeId: String, atMillis: Long) {
        db().animeQueries.markAnilistNotFound(
            anilist_not_found_at = atMillis,
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun markMalNotFound(animeId: String, atMillis: Long) {
        db().animeQueries.markMalNotFound(
            mal_not_found_at = atMillis,
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    suspend fun markShikimoriNotFound(animeId: String, atMillis: Long) {
        db().animeQueries.markShikimoriNotFound(
            shikimori_not_found_at = atMillis,
            updatedAt = System.currentTimeMillis(),
            id = animeId
        )
        mirrorCoordinator.requestExportIfEnabled()
    }

    private fun getTagsForAnime(animeId: String): ImmutableList<String> {
        return db().animeQueries.getAnimeTags(animeId).executeAsList().toImmutableList()
    }

    private fun parseTagsConcat(tagsConcat: String?): ImmutableList<String> {
        val raw = tagsConcat?.trim().orEmpty()
        if (raw.isEmpty()) return persistentListOf()
        return raw.split(TAG_CONCAT_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toImmutableList()
    }

    private companion object {
        private const val TAG_CONCAT_DELIMITER = '\u001F'
    }
}
