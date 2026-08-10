package com.example.myapplication.media.source

/** A lawful direct/personal-library source for MOVIE/SERIES playback. */
interface MovieSeriesPlaybackSource {
    val sourceName: String
    suspend fun resolve(request: PlaybackRequest): MovieSeriesSourceResult
}

sealed interface MovieSeriesSourceResult {
    data class Found(val hosters: List<VetroHoster>) : MovieSeriesSourceResult
    data object NoMatch : MovieSeriesSourceResult
    data object NotConfigured : MovieSeriesSourceResult
}

internal suspend fun resolveMovieSeriesSources(
    request: PlaybackRequest,
    sources: List<MovieSeriesPlaybackSource>,
    timeoutMs: Long = 20_000L,
): PlaybackResolution {
    val attempts = runPlaybackProviderCascade(
        sources.map { source ->
            PlaybackProviderCall(source.sourceName, timeoutMs) { source.resolve(request) }
        }
    )
    val results = attempts.mapNotNull(SourceAttempt<MovieSeriesSourceResult>::value)
    val playable = results.filterIsInstance<MovieSeriesSourceResult.Found>()
        .flatMap(MovieSeriesSourceResult.Found::hosters)
        .withPropagatedSkipReference()
        .playableHosters()
    val anyConfigured = results.any { it != MovieSeriesSourceResult.NotConfigured }
    if (!anyConfigured && attempts.none(SourceAttempt<MovieSeriesSourceResult>::failed)) {
        return PlaybackResolution.NotConfigured(request.mediaType)
    }
    return playbackResolution(playable, attempts.any { it.failed })
}
