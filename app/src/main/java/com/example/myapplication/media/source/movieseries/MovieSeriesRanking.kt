package com.example.myapplication.media.source.movieseries

import com.example.myapplication.media.source.VetroHoster
import com.example.myapplication.media.source.VetroVideo
import com.example.myapplication.network.AppLanguage
import kotlin.math.abs

/**
 * One playable option with the provenance the ranking and the source picker need.
 *
 * The extra fields live here rather than on [VetroVideo] so provider bookkeeping never reaches the
 * player: everything downstream still consumes the plain normalized model.
 */
data class MovieSeriesCandidate(
    val providerId: ProviderId,
    val providerName: String,
    val video: VetroVideo,
    /** The hoster URL the provider reported; kept verbatim so regrouping cannot invent one. */
    val hosterUrl: String = "",
    val accuracy: MatchAccuracy? = null,
    val language: AppLanguage? = null,
    /** Dub/translation label when the provider names one, e.g. `LostFilm`. */
    val translation: String? = null,
    val elapsedMs: Long = 0,
    /** Penalty contributed by provider health; 0 is a healthy provider. */
    val healthPenalty: Int = 0,
)

/**
 * Deterministic ranking across providers.
 *
 * Ordering is expressed as an explicit chain of comparisons rather than a single opaque score, so a
 * change in priority is visible in review instead of hidden in arithmetic. Every comparison ends in
 * a stable tiebreak, so the same inputs always produce the same order.
 */
object MovieSeriesRanking {

    fun rank(
        candidates: List<MovieSeriesCandidate>,
        preferredResolution: Int,
        requestedLanguage: AppLanguage,
    ): List<MovieSeriesCandidate> = candidates
        .distinctBy { it.video.url }
        .sortedWith(comparator(preferredResolution, requestedLanguage))

    private fun comparator(
        preferredResolution: Int,
        requestedLanguage: AppLanguage,
    ): Comparator<MovieSeriesCandidate> = compareBy<MovieSeriesCandidate>(
        // Identification strength first: a stream of the wrong title is worthless at any bitrate.
        { candidate -> candidate.accuracy?.ordinal ?: Int.MAX_VALUE },
        // Then the language the user actually asked for.
        { candidate -> if (candidate.matchesLanguage(requestedLanguage)) 0 else 1 },
        // A provider currently failing goes below a healthy one offering the same thing.
        { candidate -> candidate.healthPenalty },
        // Closest to the requested quality, preferring not to exceed it.
        { candidate -> candidate.resolutionDistance(preferredResolution) },
        { candidate -> if (candidate.exceeds(preferredResolution)) 1 else 0 },
        { candidate -> if (candidate.video.isPreferred) 0 else 1 },
        // Faster providers break remaining ties; latency is evidence, not a guess.
        { candidate -> candidate.elapsedMs },
    )
        .thenByDescending { candidate -> candidate.resolution() ?: 0 }
        // Stable final tiebreak so equal candidates never reorder between runs.
        .thenBy { candidate -> candidate.providerName }
        .thenBy { candidate -> candidate.video.url }

    private fun MovieSeriesCandidate.matchesLanguage(requested: AppLanguage): Boolean =
        language == null || language == requested

    private fun MovieSeriesCandidate.resolution(): Int? =
        video.resolution?.takeIf { it > 0 }
            ?: Regex("""(?i)\b(\d{3,4})p\b""")
                .find(video.label)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

    private fun MovieSeriesCandidate.resolutionDistance(preferred: Int): Int =
        resolution()?.let { abs(it - preferred) } ?: (Int.MAX_VALUE / 2)

    private fun MovieSeriesCandidate.exceeds(preferred: Int): Boolean =
        (resolution() ?: 0) > preferred
}

/** Flattens provider results into candidates, keeping which provider produced each stream. */
fun buildCandidates(
    providerId: ProviderId,
    providerName: String,
    hosters: List<VetroHoster>,
    accuracy: MatchAccuracy? = null,
    language: AppLanguage? = null,
    elapsedMs: Long = 0,
    healthPenalty: Int = 0,
): List<MovieSeriesCandidate> = hosters.flatMap { hoster ->
    hoster.videos.orEmpty().map { video ->
        MovieSeriesCandidate(
            providerId = providerId,
            providerName = providerName,
            hosterUrl = hoster.url,
            video = video.copy(
                sourceName = video.sourceName?.takeIf(String::isNotBlank) ?: hoster.name,
            ),
            accuracy = accuracy,
            language = language,
            translation = hoster.name.takeIf { it.isNotBlank() && it != providerName },
            elapsedMs = elapsedMs,
            healthPenalty = healthPenalty,
        )
    }
}

/** Regroups ranked candidates back into the normalized model the player consumes. */
fun List<MovieSeriesCandidate>.toHosters(): List<VetroHoster> =
    groupBy { candidate -> candidate.translation ?: candidate.providerName }
        .map { (name, group) ->
            VetroHoster(
                name = name,
                url = group.first().hosterUrl,
                videos = group.map(MovieSeriesCandidate::video),
            )
        }
