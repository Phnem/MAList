package com.example.myapplication.domain.inspect

import com.example.myapplication.data.remote.GeminiStructuredClient
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.network.TraceMoeRemoteDataSource
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

enum class InspectContentMode {
    Anime,
    MoviesSeries
}

class InspectImageUseCase(
    private val traceMoe: TraceMoeRemoteDataSource,
    private val animeRepository: AnimeRepository,
    private val gemini: GeminiStructuredClient
) {

    suspend fun validateGeminiApiKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        gemini.validateApiKey(apiKey.trim())
    }

    suspend operator fun invoke(
        imageBytes: ByteArray,
        mimeTypeForTrace: ContentType,
        mimeTypeForGemini: String,
        contentMode: InspectContentMode,
        appLanguage: AppLanguage,
        geminiApiKey: String
    ): Result<List<ApiSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            when (contentMode) {
                InspectContentMode.Anime ->
                    inspectAnime(
                        imageBytes,
                        mimeTypeForTrace,
                        mimeTypeForGemini,
                        appLanguage,
                        geminiApiKey
                    )
                InspectContentMode.MoviesSeries -> inspectMoviesSeries(imageBytes, mimeTypeForGemini, appLanguage, geminiApiKey)
            }
        }
    }

    private suspend fun inspectAnime(
        imageBytes: ByteArray,
        traceContentType: ContentType,
        geminiMime: String,
        appLanguage: AppLanguage,
        geminiApiKey: String
    ): List<ApiSearchResult> {
        return when (appLanguage) {
            AppLanguage.EN -> {
                val trace = traceMoe.search(imageBytes, traceContentType, withAnilistInfo = false).getOrThrow()
                val item = animeRepository.mediaByAnilistId(trace.anilistId).getOrThrow()
                    ?: error("AniList: media not found for id ${trace.anilistId}")
                listOf(item)
            }
            AppLanguage.RU -> {
                if (geminiApiKey.isBlank()) {
                    throw InspectGeminiRequiredException(InspectGeminiRequirement.RU_ANIME_PATH)
                }
                val trace = traceMoe.search(imageBytes, traceContentType, withAnilistInfo = true).getOrThrow()
                // 0.5 и ниже не включительно
                if (trace.similarity <= 0.5) return emptyList()
                val ru = gemini.russianAnimeTitle(
                    romaji = trace.romajiTitle,
                    english = trace.englishTitle,
                    apiKey = geminiApiKey
                ).getOrThrow()
                // Только Shikimori — без AniList/Jikan в ранжировании (путь inspect: Gemini → Shikimori)
                animeRepository.searchAnimeShikimoriOnly(ru, AppLanguage.RU).getOrThrow()
            }
        }
    }

    private suspend fun inspectMoviesSeries(
        imageBytes: ByteArray,
        geminiMime: String,
        appLanguage: AppLanguage,
        geminiApiKey: String
    ): List<ApiSearchResult> {
        if (geminiApiKey.isBlank()) {
            throw InspectGeminiRequiredException(InspectGeminiRequirement.MOVIES_TV)
        }
        val b64 = Base64.getEncoder().encodeToString(imageBytes)
        val (title, contentType) = gemini.identifyMovieOrTvFromImage(
            imageBase64 = b64,
            mimeType = geminiMime,
            apiKey = geminiApiKey
        ).getOrThrow()
        return animeRepository.searchApi(title, contentType, appLanguage).getOrThrow()
    }
}
