package com.example.myapplication.media.source.movieseries

import com.example.myapplication.data.models.MediaType
import com.example.myapplication.media.source.PlaybackRequest
import com.example.myapplication.media.source.VetroHoster
import com.example.myapplication.network.AppLanguage

/** Stable key for one provider. Survives renames of the user-visible name. */
@JvmInline
value class ProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProviderId must not be blank" }
    }
}

/**
 * What a provider is able to do.
 *
 * A capability is potential, not permission: declaring [DOWNLOAD] only means the provider can ever
 * grant an offline copy. Whether one particular stream may be downloaded stays on
 * `VetroVideo.downloadAllowed`, which remains default-deny.
 */
enum class ProviderCapability {
    MOVIE,
    SERIES,
    RU,
    EN,
    DIRECT,
    HLS,
    SUBTITLES,
    MULTI_AUDIO,
    DOWNLOAD,
    TMDB_ID,
    IMDB_ID,
    KINOPOISK_ID,
}

/**
 * One provider's answer.
 *
 * The distinctions matter downstream: health must not punish a provider for honestly reporting that
 * it does not carry a title, and the source picker must not describe an outage as "not found".
 */
sealed interface ProviderResolution {
    /**
     * The provider carries this title and returned playable candidates.
     *
     * [accuracy] and [language] are how the provider identified the title. They stay optional so an
     * adapter that cannot say is not forced to invent a confident-looking answer; ranking treats a
     * missing accuracy as the weakest evidence rather than the strongest.
     */
    data class Found(
        val hosters: List<VetroHoster>,
        val accuracy: MatchAccuracy? = null,
        val language: AppLanguage? = null,
    ) : ProviderResolution

    /** The provider answered correctly and does not carry this title. Not a failure. */
    data object NotFound : ProviderResolution

    /** The user has not configured this provider. Not a failure. */
    data object NotConfigured : ProviderResolution

    /** The provider cannot serve this media type / language / id set at all. Not a failure. */
    data object Unsupported : ProviderResolution

    /** Reachable but currently failing: timeout, 5xx, connection reset. Retry later. */
    data class TemporaryError(val detail: String? = null) : ProviderResolution

    /** Refused us: 401/403, geo-block, credential rejected. */
    data class Blocked(val detail: String? = null) : ProviderResolution

    /** Explicitly throttled. [retryAfterMs] when the provider stated one. */
    data class RateLimited(val retryAfterMs: Long? = null) : ProviderResolution

    /** Answered with something we cannot parse — shape change, truncated body, wrong content type. */
    data class InvalidResponse(val detail: String? = null) : ProviderResolution
}

/** Counts against provider health; [ProviderResolution.NotFound] deliberately does not. */
val ProviderResolution.isFailure: Boolean
    get() = when (this) {
        is ProviderResolution.TemporaryError,
        is ProviderResolution.Blocked,
        is ProviderResolution.RateLimited,
        is ProviderResolution.InvalidResponse,
        -> true

        is ProviderResolution.Found,
        ProviderResolution.NotFound,
        ProviderResolution.NotConfigured,
        ProviderResolution.Unsupported,
        -> false
    }

/** The provider was reachable and configured enough to give a real answer. */
val ProviderResolution.isConfigured: Boolean
    get() = this != ProviderResolution.NotConfigured && this != ProviderResolution.Unsupported

/**
 * A MOVIE/SERIES playback provider.
 *
 * Implementations must be safe to run concurrently with their siblings and must let
 * `CancellationException` escape untouched.
 */
interface MovieSeriesStreamingProvider {
    val id: ProviderId

    /** User-visible name; also the `VetroHoster.name` this provider produces. */
    val displayName: String

    val capabilities: Set<ProviderCapability>

    suspend fun resolve(request: PlaybackRequest): ProviderResolution
}

/**
 * Pure applicability policy. Keeps a provider off the network when it could not possibly answer.
 *
 * A provider that declares neither [ProviderCapability.RU] nor [ProviderCapability.EN] is treated as
 * language-agnostic: personal libraries hold whatever the user put in them, so they answer for both.
 */
object ProviderApplicability {
    fun isApplicable(
        capabilities: Set<ProviderCapability>,
        mediaType: MediaType,
        language: AppLanguage,
    ): Boolean = supportsMediaType(capabilities, mediaType) && supportsLanguage(capabilities, language)

    private fun supportsMediaType(
        capabilities: Set<ProviderCapability>,
        mediaType: MediaType,
    ): Boolean = when (mediaType) {
        MediaType.MOVIE -> ProviderCapability.MOVIE in capabilities
        MediaType.SERIES -> ProviderCapability.SERIES in capabilities
        else -> false
    }

    private fun supportsLanguage(
        capabilities: Set<ProviderCapability>,
        language: AppLanguage,
    ): Boolean {
        val declared = capabilities.filter {
            it == ProviderCapability.RU || it == ProviderCapability.EN
        }
        if (declared.isEmpty()) return true
        return when (language) {
            AppLanguage.RU -> ProviderCapability.RU in capabilities
            AppLanguage.EN -> ProviderCapability.EN in capabilities
        }
    }
}

/**
 * Maps an HTTP status to the outcome it actually represents.
 *
 * Returns `null` for success so callers can continue parsing. A status the provider considers normal
 * (WebDAV `207`) must be handled by the caller before reaching here.
 */
fun providerResolutionForStatus(status: Int, retryAfterMs: Long? = null): ProviderResolution? = when {
    // Anything the provider itself deems success is the caller's business to parse.
    status in 200..299 -> null
    status == 404 || status == 410 -> ProviderResolution.NotFound
    status == 401 || status == 403 -> ProviderResolution.Blocked("HTTP $status")
    status == 429 -> ProviderResolution.RateLimited(retryAfterMs)
    status == 408 || status == 425 -> ProviderResolution.TemporaryError("HTTP $status")
    status in 500..599 -> ProviderResolution.TemporaryError("HTTP $status")
    else -> ProviderResolution.InvalidResponse("HTTP $status")
}

/**
 * Carries a typed outcome out of a nested helper.
 *
 * Adapters resolve through several requests; without this the only way to abort a deep helper was to
 * throw, which the cascade could only record as an anonymous failure.
 */
class ProviderHttpException(
    val resolution: ProviderResolution,
    message: String,
) : Exception(message)

/** Aborts with the outcome the status actually represents. No-op on success. */
fun requireProviderSuccess(status: Int, label: String, retryAfterMs: Long? = null) {
    val failure = providerResolutionForStatus(status, retryAfterMs) ?: return
    throw ProviderHttpException(failure, "$label HTTP $status")
}

/**
 * Runs an adapter body and converts the two failure shapes it can produce into typed outcomes.
 *
 * `CancellationException` is re-thrown untouched; a malformed payload becomes
 * [ProviderResolution.InvalidResponse] rather than an anonymous cascade failure.
 */
suspend fun resolveTyped(
    label: String,
    body: suspend () -> ProviderResolution,
): ProviderResolution = try {
    body()
} catch (error: kotlinx.coroutines.CancellationException) {
    throw error
} catch (error: ProviderHttpException) {
    error.resolution
} catch (error: kotlinx.serialization.SerializationException) {
    ProviderResolution.InvalidResponse("$label: ${error.javaClass.simpleName}")
} catch (error: IllegalArgumentException) {
    ProviderResolution.InvalidResponse("$label: ${error.javaClass.simpleName}")
}
