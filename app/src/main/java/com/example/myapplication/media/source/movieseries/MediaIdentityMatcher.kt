package com.example.myapplication.media.source.movieseries

import com.example.myapplication.media.source.PlaybackRequest

/**
 * What one side of a comparison knows about a title.
 *
 * Every field is optional because providers differ in what they expose: a personal library may only
 * know a folder name, while a catalogue answers with three ids and no year.
 */
data class MediaIdentity(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val kinopoiskId: Int? = null,
    val title: String? = null,
    val year: Int? = null,
) {
    companion object {
        fun of(request: PlaybackRequest): MediaIdentity = MediaIdentity(
            tmdbId = request.tmdbId,
            imdbId = request.imdbId,
            kinopoiskId = request.kinopoiskId,
            title = request.anime.title,
        )
    }
}

/**
 * How strong the evidence behind a match is, strongest first.
 *
 * Declaration order is the ranking: `compareTo` on an enum follows [ordinal], so a TMDB match always
 * beats a title match without a separate score table.
 */
enum class MatchAccuracy {
    TMDB_ID,
    IMDB_ID,
    KINOPOISK_ID,
    TITLE_AND_YEAR,

    /** Titles agree but at least one side has no year. Weakest evidence that is still usable. */
    TITLE_ONLY,
}

sealed interface IdentityMatch {
    data class Matched(val accuracy: MatchAccuracy) : IdentityMatch

    /**
     * At least one shared id disagrees. Disqualifying: a candidate carrying a different TMDB id is
     * a different title, whatever else happens to line up.
     */
    data object Conflict : IdentityMatch

    /** Nothing links the two. Not a contradiction — simply no grounds to accept. */
    data object NoEvidence : IdentityMatch
}

/**
 * ID-first identity rules shared by every MOVIE/SERIES provider.
 *
 * Pure and free of any provider's wire format, so a new adapter inherits the accuracy rules instead
 * of reinventing weaker ones.
 */
object MediaIdentityMatcher {

    fun match(wanted: MediaIdentity, candidate: MediaIdentity): IdentityMatch {
        val agreements = mutableListOf<MatchAccuracy>()

        compareIds(wanted.tmdbId, candidate.tmdbId)?.let { agrees ->
            if (!agrees) return IdentityMatch.Conflict
            agreements += MatchAccuracy.TMDB_ID
        }
        compareIds(wanted.imdbId.normalizeImdbId(), candidate.imdbId.normalizeImdbId())?.let { agrees ->
            if (!agrees) return IdentityMatch.Conflict
            agreements += MatchAccuracy.IMDB_ID
        }
        compareIds(wanted.kinopoiskId, candidate.kinopoiskId)?.let { agrees ->
            if (!agrees) return IdentityMatch.Conflict
            agreements += MatchAccuracy.KINOPOISK_ID
        }

        agreements.minOrNull()?.let { return IdentityMatch.Matched(it) }

        return matchByTitle(wanted, candidate)
    }

    /**
     * Picks the single best candidate, or nothing.
     *
     * Only the strongest tier that produced matches is considered: a TMDB match and a title match
     * are not rivals, and letting a weak match break a tie is how the wrong episode gets played.
     * Ambiguity inside that tier returns `null` rather than an arbitrary pick.
     */
    fun <T> selectUnique(
        wanted: MediaIdentity,
        candidates: List<T>,
        identityOf: (T) -> MediaIdentity,
    ): T? {
        val matched = candidates.mapNotNull { candidate ->
            val result = match(wanted, identityOf(candidate))
            (result as? IdentityMatch.Matched)?.let { candidate to it.accuracy }
        }
        if (matched.isEmpty()) return null

        val best = matched.minOf { (_, accuracy) -> accuracy }
        val topTier = matched.filter { (_, accuracy) -> accuracy == best }
        return topTier.singleOrNull()?.first
    }

    /** `null` when the pair carries no shared evidence; otherwise whether the two ids agree. */
    private fun <T : Any> compareIds(wanted: T?, candidate: T?): Boolean? {
        if (wanted == null || candidate == null) return null
        return wanted == candidate
    }

    private fun matchByTitle(wanted: MediaIdentity, candidate: MediaIdentity): IdentityMatch {
        val wantedTitle = wanted.title.normalizeTitle()
        val candidateTitle = candidate.title.normalizeTitle()
        if (wantedTitle.isEmpty() || candidateTitle.isEmpty()) return IdentityMatch.NoEvidence
        if (wantedTitle != candidateTitle) return IdentityMatch.NoEvidence

        val wantedYear = wanted.year
        val candidateYear = candidate.year
        return when {
            wantedYear == null || candidateYear == null -> IdentityMatch.Matched(MatchAccuracy.TITLE_ONLY)
            // Same name, different year: remakes are the common case, so this is a rejection.
            wantedYear != candidateYear -> IdentityMatch.NoEvidence
            else -> IdentityMatch.Matched(MatchAccuracy.TITLE_AND_YEAR)
        }
    }
}

/** IMDb ids are case-insensitive; blank means "absent", never "empty id". */
fun String?.normalizeImdbId(): String? =
    this?.trim()?.lowercase()?.takeIf(String::isNotEmpty)

/** Strips punctuation and case so `Doctor House` and `doctor-house` compare equal. */
fun String?.normalizeTitle(): String =
    this?.lowercase()?.replace(Regex("[^\\p{L}\\p{N}]+"), " ")?.trim().orEmpty()

/** Released-only semantics stay upstream; this only answers whether the numbers line up. */
fun episodeMatches(
    candidateSeason: Int?,
    candidateEpisode: Int?,
    wantedSeason: Int,
    wantedEpisode: Int,
): Boolean = candidateSeason == wantedSeason && candidateEpisode == wantedEpisode
