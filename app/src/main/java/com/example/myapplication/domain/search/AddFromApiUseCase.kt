package com.example.myapplication.domain.search

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.data.models.RatingScale
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.domain.IdGenerator
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.domain.addedit.SaveAnimeParams
import com.example.myapplication.domain.addedit.SaveAnimeUseCase
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppLanguage

/** Рейтинг API → 10-балльная шкала приложения (см. [RatingScale.fromApi]). */
fun apiRatingTo10(apiRating: Int?): Float = RatingScale.fromApi(apiRating)

fun mapApiGenresToTagIds(genres: List<String>, genreRepository: GenreRepository): List<String> =
    genres.mapNotNull { apiGenre ->
        val normalized = apiGenre.trim()
        if (normalized.isBlank()) return@mapNotNull null
        genreRepository.allGenres.find { def ->
            def.id.equals(normalized, ignoreCase = true) ||
                def.ru.equals(normalized, ignoreCase = true) ||
                def.en.equals(normalized, ignoreCase = true) ||
                def.id.replace("-", " ").equals(normalized.replace("-", " "), ignoreCase = true)
        }?.id
    }.distinct().take(5)

/**
 * Adds a media item from API search result to the local database.
 * Downloads poster from URL, maps genres to app genre IDs, converts rating to the 10-point scale, saves via SaveAnimeUseCase.
 */
class AddFromApiUseCase(
    private val saveAnimeUseCase: SaveAnimeUseCase,
    private val imageStorage: ImageStorageRepository,
    private val idGenerator: IdGenerator,
    private val genreRepository: GenreRepository,
    private val repository: AnimeRepository,
) {
    suspend operator fun invoke(
        result: ApiSearchResult
    ): Result<Unit> = runCatching {
        val animeId = idGenerator.generateUuid()
        val imageFileName = result.posterUrl?.let { url ->
            imageStorage.saveImageFromUrl(url, animeId).getOrNull()
        }

        val rating10 = apiRatingTo10(result.rating)
        val selectedTags = mapApiGenresToTagIds(result.genres, genreRepository)

        // Shikimori-результат из поиска несёт только shikimori_id; MAL id дотягиваем detail-запросом,
        // чтобы у записи сразу были ОБА id (для обогащения english по id, а не по названию).
        val shikimoriMalId = if (result.source.equals("Shikimori", ignoreCase = true) && result.malId == null) {
            result.externalId?.toIntOrNull()?.let { sid ->
                repository.shikimoriById(sid, AppLanguage.EN).getOrNull()?.malId
            }
        } else {
            null
        }
        val effectiveMalId = result.malId ?: shikimoriMalId ?: result.externalId?.toIntOrNull().takeIf {
            result.source.equals("MAL", ignoreCase = true) || result.source.equals("Jikan", ignoreCase = true)
        }

        val params = SaveAnimeParams(
            animeId = null,
            title = result.title,
            episodes = result.episodes.coerceAtLeast(1),
            rating = rating10,
            imageUri = null,
            currentImageFileName = imageFileName,
            orderIndex = 0,
            dateAdded = idGenerator.currentTimeMillis(),
            isFavorite = false,
            selectedTags = selectedTags,
            categoryType = result.categoryType,
            // `categoryType` выдачи поиска уже проштампован разделом, в котором тайтл нашли
            // (см. ApiService.searchApi) — отсюда и берётся тип записи.
            mediaType = MediaType.fromCategoryType(result.categoryType),
            comment = "",
            anilistId = result.externalId?.toIntOrNull().takeIf { result.source.equals("AniList", ignoreCase = true) },
            malId = effectiveMalId,
            shikimoriId = result.externalId?.toIntOrNull().takeIf { result.source.equals("Shikimori", ignoreCase = true) }
        )
        saveAnimeUseCase(params)
    }
}
