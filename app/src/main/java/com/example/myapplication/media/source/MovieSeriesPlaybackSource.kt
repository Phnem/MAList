package com.example.myapplication.media.source

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.media.source.movieseries.MovieSeriesStreamingProvider
import com.example.myapplication.media.source.movieseries.ProviderApplicability
import com.example.myapplication.media.source.movieseries.ProviderResolution
import com.example.myapplication.media.source.movieseries.isConfigured
import com.example.myapplication.media.source.movieseries.isFailure
import com.example.myapplication.network.AppLanguage

/**
 * Runs every applicable MOVIE/SERIES provider and folds the answers into one resolution.
 *
 * Providers that cannot serve this request are dropped before any network call, so an unconfigured
 * library never costs a timeout. One provider failing never suppresses a sibling's success.
 */
/**
 * The providers that serve one language's cascade.
 *
 * Pure and separate from the cascade so the RU and EN line-ups can be asserted without running any
 * provider. Order is preserved; ranking is a later concern.
 */
fun selectMovieSeriesProviders(
    providers: List<MovieSeriesStreamingProvider>,
    mediaType: MediaType,
    language: AppLanguage,
): List<MovieSeriesStreamingProvider> = providers.filter { provider ->
    ProviderApplicability.isApplicable(provider.capabilities, mediaType, language)
}

internal suspend fun resolveMovieSeriesSources(
    request: PlaybackRequest,
    sources: List<MovieSeriesStreamingProvider>,
    timeoutMs: Long = 20_000L,
): PlaybackResolution {
    val applicable = selectMovieSeriesProviders(sources, request.mediaType, request.language)
    if (applicable.isEmpty()) return PlaybackResolution.NotConfigured(request.mediaType)

    val attempts = runPlaybackProviderCascade(
        applicable.map { source ->
            PlaybackProviderCall(source.displayName, timeoutMs) { source.resolve(request) }
        }
    )
    val results = attempts.mapNotNull(SourceAttempt<ProviderResolution>::value)
    val playable = results.filterIsInstance<ProviderResolution.Found>()
        .flatMap(ProviderResolution.Found::hosters)
        .withPropagatedSkipReference()
        .playableHosters()

    // A crashed or timed-out attempt has no value to inspect, so both sources of failure count.
    val hadFailure = attempts.any(SourceAttempt<ProviderResolution>::failed) ||
        results.any { it.isFailure }
    val anyConfigured = results.any { it.isConfigured }

    if (!anyConfigured && !hadFailure) return PlaybackResolution.NotConfigured(request.mediaType)
    return playbackResolution(playable, hadFailure)
}
