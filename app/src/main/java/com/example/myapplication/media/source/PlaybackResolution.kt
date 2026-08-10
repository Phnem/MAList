package com.example.myapplication.media.source

import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.MediaType
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.network.AppLanguage
import kotlinx.serialization.Serializable

@Serializable
data class PlaybackIdentity(
    val libraryId: String,
    val title: String,
    val titleEn: String? = null,
    val titleRu: String? = null,
    val mediaType: MediaType,
    val tmdbId: Int? = null,
    val kinopoiskId: Int? = null,
    val malId: Int? = null,
    val anilistId: Int? = null,
) {
    fun toAnime(seasonInfo: SeasonInfo?, episodeNumber: Int): Anime = Anime(
        id = libraryId,
        title = seasonInfo?.title?.takeIf { it.isNotBlank() } ?: title,
        titleEn = titleEn,
        titleRu = titleRu,
        episodes = seasonInfo?.episodes?.coerceAtLeast(episodeNumber) ?: episodeNumber,
        rating = 0f,
        imageFileName = null,
        orderIndex = 0,
        dateAdded = 0L,
        mediaType = mediaType,
        tmdbId = tmdbId,
        kinopoiskId = kinopoiskId,
        malId = seasonInfo?.malId ?: malId,
        anilistId = seasonInfo?.anilistId ?: anilistId,
    )

    companion object {
        fun from(anime: Anime): PlaybackIdentity = PlaybackIdentity(
            libraryId = anime.id,
            title = anime.title,
            titleEn = anime.titleEn,
            titleRu = anime.titleRu,
            mediaType = anime.mediaType,
            tmdbId = anime.tmdbId,
            kinopoiskId = anime.kinopoiskId,
            malId = anime.malId,
            anilistId = anime.anilistId,
        )
    }
}

/** Everything a playback provider may use to resolve one episode. */
data class PlaybackRequest(
    val anime: Anime,
    val episodeNumber: Int,
    val seasonInfo: SeasonInfo? = null,
    val language: AppLanguage = AppLanguage.RU,
) {
    val mediaType: MediaType get() = anime.mediaType
    val tmdbId: Int? get() = anime.tmdbId
    val kinopoiskId: Int? get() = anime.kinopoiskId
    val seasonNumber: Int get() = seasonInfo?.seasonNumber ?: 1
}

sealed interface PlaybackResolution {
    data class Found(val hosters: List<VetroHoster>) : PlaybackResolution
    data class NotConfigured(val mediaType: MediaType) : PlaybackResolution
    data object NoMatch : PlaybackResolution
    data object Failure : PlaybackResolution
}

sealed interface PlaybackRoute {
    data object AnimeRu : PlaybackRoute
    data object AnimeEn : PlaybackRoute
    data object DirectOnly : PlaybackRoute
    data object None : PlaybackRoute
}

/** Pure capability policy; [SourceEngine] dispatches exclusively through this route. */
object PlaybackRoutingPolicy {
    fun route(mediaType: MediaType, language: AppLanguage): PlaybackRoute = when {
        mediaType == MediaType.ANIME && language == AppLanguage.RU -> PlaybackRoute.AnimeRu
        mediaType == MediaType.ANIME && language == AppLanguage.EN -> PlaybackRoute.AnimeEn
        mediaType == MediaType.MOVIE || mediaType == MediaType.SERIES -> PlaybackRoute.DirectOnly
        else -> PlaybackRoute.None
    }
}

internal val PlaybackResolution.hostersOrEmpty: List<VetroHoster>
    get() = (this as? PlaybackResolution.Found)?.hosters.orEmpty()

/** A successful provider wins even when another applicable provider failed. */
internal fun playbackResolution(
    playableHosters: List<VetroHoster>,
    hadProviderFailure: Boolean,
): PlaybackResolution = when {
    playableHosters.isNotEmpty() -> PlaybackResolution.Found(playableHosters)
    hadProviderFailure -> PlaybackResolution.Failure
    else -> PlaybackResolution.NoMatch
}
