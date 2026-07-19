package com.example.myapplication.domain.settings

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.domain.addedit.SaveAnimeParams
import com.example.myapplication.domain.addedit.SaveAnimeUseCase
import com.example.myapplication.domain.search.apiRatingTo10
import com.example.myapplication.domain.search.mapApiGenresToTagIds
import com.example.myapplication.network.AnimeDetails
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.sync.TitleMatcher
import kotlinx.coroutines.delay

data class RepairAnimeDbResult(
    val scannedCount: Int,
    val repairedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
)

/**
 * Проходит по коллекции и заполняет отсутствующие поля (обложка, жанры, рейтинг, ID и пр.).
 *
 * Иерархия источников для аниме-записей ЕДИНА для картинок, жанров и оценок и не зависит
 * от языка приложения:
 *   основа — AniList, endpoint 1 — Shikimori, endpoint 2 — MAL.
 * Поля сливаются по-отдельности: если у AniList нет постера, но есть жанры — жанры берём
 * у AniList, постер ищем дальше по иерархии. Жанры любых источников (EN от AniList/MAL,
 * RU от Shikimori) конвертируются в канонические ID через [GenreRepository] и корректно
 * отображаются на обоих языках.
 *
 * Внутри одной записи два раунда: сперва строгий матч по названию у всех источников,
 * затем (если пробелы остались и строгих матчей нет) — relaxed-выбор лучшего кандидата.
 *
 * Не-аниме записи (кино/сериалы) идут прежним путём через общий searchApi.
 */
class RepairAnimeDbUseCase(
    private val repository: AnimeRepository,
    private val saveAnimeUseCase: SaveAnimeUseCase,
    private val imageStorage: ImageStorageRepository,
    private val genreRepository: GenreRepository,
) {
    suspend operator fun invoke(
        language: AppLanguage,
        contentType: AppContentType,
        sessionLog: RepairDbSessionLog,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): RepairAnimeDbResult {
        // Отдельный проход: сверить и починить malId по настоящему myanimelist_id из Shikimori
        // (легаси-порча — у части записей malId == shikimoriId, это РАЗНЫЕ id → чужое аниме в MAL/AniList).
        reconcileMalIds(repository.getAllAnimeSnapshot(), sessionLog)

        val all = repository.getAllAnimeSnapshot() // перечитываем: malId мог измениться после реконсиляции
        val needing = all.filter { detectGaps(it).needsRepair }

        sessionLog.info(
            "Start repair: ${all.size} entries, language=$language, " +
                "contentType=$contentType, needRepair=${needing.size} " +
                "(hierarchy: AniList → Shikimori → MAL)",
        )
        onProgress(0, needing.size)

        for ((index, anime) in needing.withIndex()) {
            repairOne(anime, language, contentType, sessionLog)
            onProgress(index + 1, needing.size)
            if (index < needing.lastIndex) delay(ITEM_DELAY_MS)
        }

        val finalNeeding = repository.getAllAnimeSnapshot().count { detectGaps(it).needsRepair }
        val result = RepairAnimeDbResult(
            scannedCount = all.size,
            repairedCount = needing.size - finalNeeding,
            failedCount = finalNeeding,
            skippedCount = all.size - needing.size,
        )
        sessionLog.info(
            "Repair done: scanned=${result.scannedCount}, repaired=${result.repairedCount}, " +
                "failed=${result.failedCount}, skipped=${result.skippedCount}",
        )
        return result
    }

    /**
     * Отдельный проход реконсиляции MAL id. У части записей `malId` ошибочно равен `shikimoriId`
     * (это РАЗНЫЕ идентификаторы), из-за чего поиск по MAL id попадает на чужое аниме. Берём
     * настоящий `myanimelist_id` из Shikimori detail и исправляем расхождение; заодно бэкфиллим
     * malId там, где у записи есть shikimoriId, но malId пуст. Кандидаты ограничены `malId == null`
     * или `malId == shikimoriId`, чтобы не дёргать сеть по заведомо корректным записям.
     */
    private suspend fun reconcileMalIds(all: List<Anime>, sessionLog: RepairDbSessionLog) {
        val suspects = all.filter { anime ->
            anime.shikimoriId != null && (anime.malId == null || anime.malId == anime.shikimoriId)
        }
        if (suspects.isEmpty()) return
        sessionLog.info("MAL id reconcile: ${suspects.size} candidates (malId null или == shikimoriId)")

        var fixed = 0
        for ((index, anime) in suspects.withIndex()) {
            val shikiId = anime.shikimoriId ?: continue
            val realMalId = repository.shikimoriById(shikiId, AppLanguage.EN).getOrNull()?.malId
            when {
                realMalId == null -> sessionLog.debug(
                    "No myanimelist_id for \"${anime.title}\" (shikimori=$shikiId); malId=${anime.malId} оставлен",
                )
                realMalId != anime.malId -> runCatching { repository.setMalId(anime.id, realMalId) }
                    .onSuccess {
                        fixed++
                        sessionLog.info(
                            "Fixed malId \"${anime.title}\": ${anime.malId} → $realMalId (shikimori=$shikiId)",
                        )
                    }
                    .onFailure { e -> sessionLog.warn("setMalId failed for \"${anime.title}\"", e) }
                else -> Unit // malId уже верный
            }
            if (index < suspects.lastIndex) delay(ITEM_DELAY_MS)
        }
        sessionLog.info("MAL id reconcile done: fixed=$fixed of ${suspects.size}")
    }

    private suspend fun repairOne(
        anime: Anime,
        language: AppLanguage,
        contentType: AppContentType,
        sessionLog: RepairDbSessionLog,
    ) {
        val gaps = detectGaps(anime)
        if (!gaps.needsRepair) return

        sessionLog.debug(
            "Needs repair \"${anime.title}\": image=${gaps.missingImage}, " +
                "tags=${gaps.missingTags}, rating=${gaps.missingRating}, id=${gaps.missingExternalId}, " +
                "type=${gaps.missingCategoryType}, episodes=${gaps.missingEpisodes}",
        )

        val isAnimeEntry = anime.categoryType.isBlank() ||
            anime.categoryType.equals("ANIME", ignoreCase = true)

        val candidates: List<ApiSearchResult> = if (!isAnimeEntry) {
            val ct = when {
                anime.categoryType.equals("MOVIE", ignoreCase = true) -> AppContentType.MOVIE
                anime.categoryType.equals("SERIES", ignoreCase = true) -> AppContentType.SERIES
                else -> contentType
            }
            val results = repository.searchApi(anime.title, ct, language).getOrNull().orEmpty()
            listOfNotNull(pickBestMatch(anime.title, results) ?: results.firstOrNull())
        } else {
            collectAnimeCandidates(anime, language, sessionLog)
        }

        if (candidates.isEmpty()) {
            sessionLog.warn("No API data for \"${anime.title}\"")
            return
        }

        sessionLog.debug(
            "Candidates for \"${anime.title}\": " +
                candidates.joinToString { "\"${it.title}\" via ${it.source}" },
        )

        val changed = runCatching { applyRepair(anime, candidates, gaps, sessionLog) }
            .onFailure { e -> sessionLog.warn("Repair error for \"${anime.title}\"", e) }
            .getOrDefault(false)
        if (changed) {
            sessionLog.info("Repaired \"${anime.title}\" via ${candidates.joinToString { it.source }}")
        } else {
            sessionLog.warn("Matched \"${anime.title}\" but gaps remain")
        }
    }

    /**
     * Кандидаты в порядке иерархии. Источники опрашиваются лениво: следующий дергаем, только
     * если уже собранные не закрывают все пробелы. Второй relaxed-раунд — если строгих матчей
     * не нашлось вовсе.
     */
    private suspend fun collectAnimeCandidates(
        anime: Anime,
        language: AppLanguage,
        sessionLog: RepairDbSessionLog,
    ): List<ApiSearchResult> {
        val gaps = detectGaps(anime)
        val collected = mutableListOf<ApiSearchResult>()

        suspend fun round(strict: Boolean) {
            val fetchers: List<suspend () -> ApiSearchResult?> = listOf(
                { fetchFromAniList(anime, language, sessionLog, strict = strict) },
                { fetchFromShikimori(anime, language, sessionLog, strict = strict) },
                { fetchFromMal(anime, language, sessionLog, strict = strict) },
            )
            for (fetch in fetchers) {
                if (gapsCovered(gaps, collected)) return
                val result = fetch() ?: continue
                collected += result
            }
        }

        round(strict = true)
        if (collected.isEmpty()) {
            sessionLog.debug("No strict match for \"${anime.title}\", relaxed round")
            round(strict = false)
        }
        return collected
    }

    /** Могут ли уже собранные кандидаты закрыть все пробелы записи. */
    private fun gapsCovered(gaps: FieldGaps, candidates: List<ApiSearchResult>): Boolean {
        if (candidates.isEmpty()) return false
        val imageOk = !gaps.missingImage || candidates.any { !it.posterUrl.isNullOrBlank() }
        val tagsOk = !gaps.missingTags ||
            candidates.any { mapApiGenresToTagIds(it.genres, genreRepository).isNotEmpty() }
        val ratingOk = !gaps.missingRating || candidates.any { (it.rating ?: 0) > 0 }
        val episodesOk = !gaps.missingEpisodes || candidates.any { it.episodes > 0 }
        val idOk = !gaps.missingExternalId || candidates.any { it.externalId != null }
        val typeOk = !gaps.missingCategoryType || candidates.any { it.categoryType.isNotBlank() }
        return imageOk && tagsOk && ratingOk && episodesOk && idOk && typeOk
    }

    private data class FieldGaps(
        val missingImage: Boolean,
        val missingTags: Boolean,
        val missingRating: Boolean,
        val missingExternalId: Boolean,
        val missingCategoryType: Boolean,
        val missingEpisodes: Boolean,
    ) {
        val needsRepair: Boolean =
            missingImage || missingTags || missingRating || missingExternalId ||
                missingCategoryType || missingEpisodes
    }

    private fun detectGaps(anime: Anime): FieldGaps {
        val hasImage = !anime.imageFileName.isNullOrBlank() &&
            imageStorage.hasLocalImage(anime.imageFileName)
        return FieldGaps(
            missingImage = !hasImage,
            missingTags = anime.tags.isEmpty(),
            missingRating = anime.rating <= 0,
            missingExternalId = anime.anilistId == null && anime.malId == null && anime.shikimoriId == null,
            missingCategoryType = anime.categoryType.isBlank(),
            missingEpisodes = anime.episodes <= 0,
        )
    }

    private suspend fun fetchFromShikimori(
        anime: Anime,
        language: AppLanguage,
        sessionLog: RepairDbSessionLog,
        strict: Boolean,
    ): ApiSearchResult? {
        anime.shikimoriId?.let { id ->
            repository.shikimoriById(id, language).getOrNull()?.let {
                sessionLog.debug("Shikimori byId=$id for \"${anime.title}\"")
                return it
            }
        }

        val results = repository.searchAnimeShikimoriOnly(
            query = anime.title,
            language = language,
            allowZeroEpisodes = true,
        ).getOrNull().orEmpty()

        val match = if (strict) {
            pickBestMatch(anime.title, results)
        } else {
            pickRelaxed(anime.title, results)?.also {
                sessionLog.debug("Shikimori relaxed match for \"${anime.title}\"")
            }
        }
        return match?.let { withShikimoriMalId(it, language) }
    }

    /**
     * Списковый Shikimori-результат не содержит MAL id — дотягиваем его detail-запросом,
     * чтобы у записи проставились ОБА id (shikimori_id и mal_id).
     */
    private suspend fun withShikimoriMalId(result: ApiSearchResult, language: AppLanguage): ApiSearchResult {
        if (result.malId != null) return result
        val shikiId = result.externalId?.toIntOrNull() ?: return result
        val malId = repository.shikimoriById(shikiId, language).getOrNull()?.malId ?: return result
        return result.copy(malId = malId)
    }

    private suspend fun fetchFromAniList(
        anime: Anime,
        language: AppLanguage,
        sessionLog: RepairDbSessionLog,
        strict: Boolean,
    ): ApiSearchResult? {
        anime.anilistId?.let { id ->
            repository.mediaByAnilistId(id).getOrNull()?.let {
                sessionLog.debug("AniList byId=$id for \"${anime.title}\"")
                return it
            }
        }

        val results = repository.searchAnimeAniListOnly(
            query = anime.title,
            language = language,
            limit = 10,
        ).getOrNull().orEmpty()

        if (strict) {
            pickBestMatch(anime.title, results)?.let { return it }
            repository.fetchDetails(anime.title, language, isManga = anime.mediaType == com.example.myapplication.data.models.MediaType.MANGA).getOrNull()?.toApiSearchResult()?.let {
                sessionLog.debug("fetchDetails fallback for \"${anime.title}\" via ${it.source}")
                return it
            }
            return null
        }

        return pickRelaxed(anime.title, results).also {
            if (it != null) sessionLog.debug("AniList relaxed match for \"${anime.title}\"")
        }
    }

    private suspend fun fetchFromMal(
        anime: Anime,
        language: AppLanguage,
        sessionLog: RepairDbSessionLog,
        strict: Boolean,
    ): ApiSearchResult? {
        anime.malId?.let { id ->
            repository.malById(id, language).getOrNull()?.let {
                sessionLog.debug("MAL byId=$id for \"${anime.title}\"")
                return it
            }
        }

        val results = repository.searchAnimeMalOnly(
            query = anime.title,
            language = language,
            limit = 10,
        ).getOrNull().orEmpty()

        if (strict) {
            return pickBestMatch(anime.title, results)
        }

        return pickRelaxed(anime.title, results).also {
            if (it != null) sessionLog.debug("MAL relaxed match for \"${anime.title}\"")
        }
    }

    /**
     * Слияние по полям: каждое недостающее поле берём у ПЕРВОГО кандидата в иерархии,
     * у которого оно есть. Внешние ID собираем со всех кандидатов сразу.
     * @return true если в БД реально записаны новые данные
     */
    private suspend fun applyRepair(
        anime: Anime,
        candidates: List<ApiSearchResult>,
        gaps: FieldGaps,
        sessionLog: RepairDbSessionLog,
    ): Boolean {
        var changed = false

        val imageFileName = when {
            !gaps.missingImage -> anime.imageFileName
            else -> {
                val url = candidates.firstNotNullOfOrNull { it.posterUrl?.takeIf { u -> u.isNotBlank() } }
                if (url == null) {
                    sessionLog.warn("No poster URL for \"${anime.title}\"")
                    anime.imageFileName
                } else {
                    imageStorage.saveImageFromUrl(url, anime.id).fold(
                        onSuccess = { name ->
                            changed = true
                            sessionLog.debug("Poster saved for \"${anime.title}\": $name")
                            name
                        },
                        onFailure = { e ->
                            sessionLog.warn("Poster download failed for \"${anime.title}\": $url", e)
                            anime.imageFileName
                        },
                    )
                }
            }
        }

        val tags = if (gaps.missingTags) {
            val mapped = candidates.firstNotNullOfOrNull { candidate ->
                mapApiGenresToTagIds(candidate.genres, genreRepository)
                    .takeIf { it.isNotEmpty() }
                    ?.also { sessionLog.debug("Tags for \"${anime.title}\" via ${candidate.source}: $it") }
            }
            if (mapped != null) {
                changed = true
                mapped
            } else {
                if (candidates.any { it.genres.isNotEmpty() }) {
                    sessionLog.warn(
                        "Genres from API not mapped for \"${anime.title}\": " +
                            candidates.flatMap { it.genres }.distinct(),
                    )
                }
                anime.tags
            }
        } else {
            anime.tags
        }

        val rating = if (gaps.missingRating) {
            val rating10 = candidates.firstNotNullOfOrNull { candidate ->
                apiRatingTo10(candidate.rating).takeIf { it > 0f }
            }
            if (rating10 != null) {
                changed = true
                rating10
            } else {
                anime.rating
            }
        } else {
            anime.rating
        }

        val episodes = if (gaps.missingEpisodes) {
            changed = true
            candidates.firstNotNullOfOrNull { it.episodes.takeIf { e -> e > 0 } } ?: 1
        } else {
            anime.episodes
        }

        val categoryType = if (gaps.missingCategoryType) {
            changed = true
            candidates.firstNotNullOfOrNull { it.categoryType.takeIf { t -> t.isNotBlank() } } ?: "ANIME"
        } else {
            anime.categoryType
        }

        var newAnilistId = anime.anilistId
        var newMalId = anime.malId
        var newShikimoriId = anime.shikimoriId
        candidates.forEach { candidate ->
            val (anilistId, malId, shikimoriId) = externalIdsFrom(candidate)
            if (newAnilistId == null && anilistId != null) newAnilistId = anilistId
            if (newMalId == null && malId != null) newMalId = malId
            if (newShikimoriId == null && shikimoriId != null) newShikimoriId = shikimoriId
        }
        if (newAnilistId != anime.anilistId || newMalId != anime.malId || newShikimoriId != anime.shikimoriId) {
            changed = true
        }

        if (!changed) return false

        val params = SaveAnimeParams(
            animeId = anime.id,
            title = anime.title,
            episodes = episodes,
            rating = rating,
            imageUri = null,
            currentImageFileName = imageFileName,
            orderIndex = anime.orderIndex,
            dateAdded = anime.dateAdded,
            isFavorite = anime.isFavorite,
            selectedTags = tags,
            categoryType = categoryType,
            comment = anime.comment,
            anilistId = newAnilistId,
            malId = newMalId,
            shikimoriId = newShikimoriId,
            anilistNotFoundAt = anime.anilistNotFoundAt,
            malNotFoundAt = anime.malNotFoundAt,
            shikimoriNotFoundAt = anime.shikimoriNotFoundAt,
        )
        saveAnimeUseCase(params).getOrThrow()
        return true
    }

    private fun externalIdsFrom(result: ApiSearchResult): Triple<Int?, Int?, Int?> {
        val extId = result.externalId?.toIntOrNull()
        val (anilistId, malFromSource, shikimoriId) = when {
            result.source.equals("Shikimori", ignoreCase = true) -> Triple(null, null, extId)
            result.source.equals("AniList", ignoreCase = true) -> Triple(extId, null, null)
            result.source.equals("MAL", ignoreCase = true) ||
                result.source.equals("Jikan", ignoreCase = true) -> Triple(null, extId, null)
            else -> Triple(null, null, null)
        }
        // Shikimori detail несёт ещё и MAL id (result.malId) — не теряем его: пишем оба id сразу.
        return Triple(anilistId, malFromSource ?: result.malId, shikimoriId)
    }

    private fun pickBestMatch(localTitle: String, results: List<ApiSearchResult>): ApiSearchResult? {
        if (results.isEmpty()) return null
        return results
            .map { result ->
                val candidates = listOfNotNull(result.title, result.altTitle)
                result to TitleMatcher.bestScore(localTitle, candidates)
            }
            .filter { (_, score) -> score >= TitleMatcher.MATCH_THRESHOLD }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun pickRelaxed(localTitle: String, results: List<ApiSearchResult>): ApiSearchResult? {
        if (results.isEmpty()) return null
        return pickBestMatch(localTitle, results) ?: results.firstOrNull()
    }

    private fun AnimeDetails.toApiSearchResult(): ApiSearchResult = ApiSearchResult(
        title = title,
        altTitle = altTitle,
        posterUrl = posterUrl,
        episodes = episodesAired.coerceAtLeast(1),
        description = description,
        type = type,
        genres = genres,
        rating = rating,
        source = source,
        categoryType = "ANIME",
        externalId = null,
    )

    private companion object {
        private const val ITEM_DELAY_MS = 450L
    }
}
