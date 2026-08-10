package com.example.myapplication.media.source

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.media.source.movieseries.MovieSeriesCandidate
import com.example.myapplication.media.source.movieseries.MovieSeriesRanking
import com.example.myapplication.media.source.movieseries.MovieSeriesStreamingProvider
import com.example.myapplication.media.source.movieseries.NoProviderHealth
import com.example.myapplication.media.source.movieseries.ProviderHealthPolicy
import com.example.myapplication.media.source.movieseries.ProviderHealthRegistry
import com.example.myapplication.media.source.movieseries.ProviderApplicability
import com.example.myapplication.media.source.movieseries.ProviderResolution
import com.example.myapplication.media.source.movieseries.buildCandidates
import com.example.myapplication.media.source.movieseries.isConfigured
import com.example.myapplication.media.source.movieseries.isFailure
import com.example.myapplication.media.source.movieseries.toHosters
import com.example.myapplication.network.AppLanguage

/** Default quality target when the caller has no stored preference. */
private const val DEFAULT_PREFERRED_RESOLUTION = 1080

/**
 * The providers that serve one language's cascade.
 *
 * Pure and separate from the cascade so the RU and EN line-ups can be asserted without running any
 * provider. Order is preserved; ranking happens after the results come back.
 */
fun selectMovieSeriesProviders(
    providers: List<MovieSeriesStreamingProvider>,
    mediaType: MediaType,
    language: AppLanguage,
): List<MovieSeriesStreamingProvider> = providers.filter { provider ->
    ProviderApplicability.isApplicable(provider.capabilities, mediaType, language)
}

/**
 * Runs every applicable MOVIE/SERIES provider and folds the answers into one ranked resolution.
 *
 * Providers that cannot serve this request are dropped before any network call, so an unconfigured
 * library never costs a timeout. The cascade deliberately does not stop at the first hit: the source
 * picker needs every option, and a second provider often carries a better translation or quality.
 */
internal suspend fun resolveMovieSeriesSources(
    request: PlaybackRequest,
    sources: List<MovieSeriesStreamingProvider>,
    timeoutMs: Long = 20_000L,
    preferredResolution: Int = DEFAULT_PREFERRED_RESOLUTION,
    health: ProviderHealthRegistry = NoProviderHealth,
    now: () -> Long = System::currentTimeMillis,
): PlaybackResolution {
    val applicable = selectMovieSeriesProviders(sources, request.mediaType, request.language)
    if (applicable.isEmpty()) return PlaybackResolution.NotConfigured(request.mediaType)

    // A provider that failed its last several attempts is skipped outright. Waiting out its full
    // timeout again on every episode is exactly the cost section 19 of the brief is about.
    val instant = now()
    val reachable = applicable.filterNot { health.healthOf(it.id).isDisabledAt(instant) }
    if (reachable.isEmpty()) return PlaybackResolution.Failure

    val attempts = runPlaybackProviderCascade(
        reachable.map { source ->
            PlaybackProviderCall(source.displayName, timeoutMs) { source.resolve(request) }
        }
    )
    val byLabel = reachable.associateBy { it.displayName }

    // Snapshot the penalties before recording this round. Recording first would reset the counter of
    // any provider that just answered, leaving every candidate on a penalty of zero and turning the
    // health dimension of the ranking into dead weight.
    val penalties = reachable.associate { provider ->
        provider.id to ProviderHealthPolicy.penalty(health.healthOf(provider.id))
    }

    attempts.forEach { attempt ->
        val provider = byLabel[attempt.label] ?: return@forEach
        // A crash or a timeout has no outcome object, but it is still a failure worth remembering.
        val outcome = attempt.value
            ?: ProviderResolution.TemporaryError(if (attempt.timedOut) "timeout" else "error")
        health.record(provider.id, outcome, attempt.elapsedMs)
    }
    val candidates = attempts.flatMap { attempt ->
        val found = attempt.value as? ProviderResolution.Found ?: return@flatMap emptyList()
        val provider = byLabel[attempt.label] ?: return@flatMap emptyList()
        buildCandidates(
            providerId = provider.id,
            providerName = provider.displayName,
            hosters = found.hosters,
            accuracy = found.accuracy,
            language = found.language,
            elapsedMs = attempt.elapsedMs,
            healthPenalty = penalties[provider.id] ?: 0,
        )
    }

    val playable = rankedPlayableHosters(candidates, preferredResolution, request.language)
    val results = attempts.mapNotNull(SourceAttempt<ProviderResolution>::value)

    // A crashed or timed-out attempt has no value to inspect, so both sources of failure count.
    val hadFailure = attempts.any(SourceAttempt<ProviderResolution>::failed) ||
        results.any { it.isFailure }
    val anyConfigured = results.any { it.isConfigured }

    if (!anyConfigured && !hadFailure) return PlaybackResolution.NotConfigured(request.mediaType)
    return playbackResolution(playable, hadFailure)
}

private fun rankedPlayableHosters(
    candidates: List<MovieSeriesCandidate>,
    preferredResolution: Int,
    language: AppLanguage,
): List<VetroHoster> = MovieSeriesRanking
    .rank(candidates, preferredResolution, language)
    .toHosters()
    .withPropagatedSkipReference()
    .playableHosters()
