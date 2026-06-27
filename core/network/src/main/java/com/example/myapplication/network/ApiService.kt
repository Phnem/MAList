package com.example.myapplication.network

/**
 * Network API contract. All methods return Result for idiomatic Kotlin error handling.
 */
interface ApiService {
    suspend fun fetchDetails(title: String, language: AppLanguage, isManga: Boolean = false, apiId: String? = null): Result<AnimeDetails?>
    suspend fun findTotalEpisodes(
        title: String,
        categoryType: String,
        appContentType: AppContentType
    ): Result<Pair<Int, String>?>
    suspend fun checkGithubUpdate(owner: String, repo: String): Result<GithubReleaseInfo?>

    suspend fun searchApi(query: String, contentType: AppContentType, language: AppLanguage): Result<List<ApiSearchResult>>

    /**
     * Только Shikimori (для RU inspect: trace → Gemini → Shikimori).
     * [allowZeroEpisodes]: для AI-рекомендаций, иначе Shikimori даёт 0 эпизодов у не вышедших/фильмов — список становится пустым.
     */
    suspend fun searchAnimeShikimoriOnly(
        query: String,
        language: AppLanguage,
        allowZeroEpisodes: Boolean = false
    ): Result<List<ApiSearchResult>>

    /** AniList [Media] по числовому id (для trace.moe → AniList). */
    suspend fun mediaByAnilistId(id: Int): Result<ApiSearchResult?>

    /** AniList-only search (used by batch update checker). */
    suspend fun searchAnimeAniListOnly(
        query: String,
        language: AppLanguage,
        limit: Int = 20
    ): Result<List<ApiSearchResult>>

    /** Shikimori title by numeric id. */
    suspend fun shikimoriById(id: Int, language: AppLanguage): Result<ApiSearchResult?>

    /** MAL/Jikan title by numeric id. */
    suspend fun malById(id: Int, language: AppLanguage): Result<ApiSearchResult?>

    /** MAL-only search via Jikan (used as EN fallback). */
    suspend fun searchAnimeMalOnly(
        query: String,
        language: AppLanguage,
        limit: Int = 20
    ): Result<List<ApiSearchResult>>
}
