package com.example.myapplication.media.ui

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.seasons.SeasonInfo
import com.example.myapplication.localplayer.ui.LocalPlayerViewModel
import com.example.myapplication.media.MediaGateway
import com.example.myapplication.media.player.HeaderResolvingPlayerFactory
import com.example.myapplication.media.source.flattenVideosWithSource
import com.example.myapplication.media.player.VetroVideoCache
import com.example.myapplication.media.progress.EpisodePlaybackStore
import com.example.myapplication.media.source.VetroVideo
import com.example.myapplication.media.source.rankVideosForResolution
import com.example.myapplication.ui.shared.theme.OneUiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import kotlin.math.abs

/**
 * Header-aware remote playback using the same custom Exo controls as the local player.
 */
class StreamPlayerActivity : ComponentActivity() {

    private val mediaGateway: MediaGateway by inject()
    private val okHttpClient: OkHttpClient by inject()
    private val playbackStore: EpisodePlaybackStore by inject()
    private val settings: DataStore<Preferences> by inject(named("settings"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var activePlayer: Player? = null
    private var activeAnimeId: String = ""
    private var activeSeason: Int = 1
    private var activeEpisode: Int = 1
    private val pipState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val legacyVideo = intent.getStringExtra(EXTRA_VIDEO_JSON)
            ?.let { runCatching { json.decodeFromString(VetroVideo.serializer(), it) }.getOrNull() }
        val intentVideos = intent.getStringExtra(EXTRA_VIDEOS_JSON)
            ?.let {
                runCatching {
                    json.decodeFromString(ListSerializer(VetroVideo.serializer()), it)
                }.getOrNull()
            }
            .orEmpty()
        val initialVideos = (intentVideos + listOfNotNull(legacyVideo)).distinctBy { it.url }
        val video = initialVideos.firstOrNull()
        if (video == null) {
            finish()
            return
        }
        val animeId = intent.getStringExtra(EXTRA_ANIME_ID).orEmpty()
        val animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE).orEmpty()
        val animeTitleEn = intent.getStringExtra(EXTRA_ANIME_TITLE_EN)
        val animeTitleRu = intent.getStringExtra(EXTRA_ANIME_TITLE_RU)
        val malId = intent.getIntExtra(EXTRA_MAL_ID, -1).takeIf { it > 0 }
        val anilistId = intent.getIntExtra(EXTRA_ANILIST_ID, -1).takeIf { it > 0 }
        val season = intent.getIntExtra(EXTRA_SEASON, 1).coerceAtLeast(1)
        val episode = intent.getIntExtra(EXTRA_EPISODE, 1).coerceAtLeast(1)
        val seasonInfo = intent.getStringExtra(EXTRA_SEASON_JSON)?.let {
            runCatching { json.decodeFromString(SeasonInfo.serializer(), it) }.getOrNull()
        }

        activeAnimeId = animeId
        activeSeason = season
        activeEpisode = episode

        setContent {
            OneUiTheme {
                val scope = rememberCoroutineScope()
                val autoSkip by settings.data
                    .map { it[LocalPlayerViewModel.AUTO_SKIP_KEY] ?: false }
                    .collectAsState(initial = false)
                val stored by playbackStore.episodeFlow(animeId, season, episode)
                    .collectAsState(initial = null)
                var candidates by remember { mutableStateOf(initialVideos) }
                var currentIndex by remember { mutableIntStateOf(0) }
                var current by remember { mutableStateOf(video) }
                var resumePosition by remember { mutableLongStateOf(0L) }
                var storedResumeApplied by remember { mutableStateOf(false) }
                var retrying by remember { mutableStateOf(false) }
                var automaticRetries by remember { mutableStateOf(0) }
                var playbackError by remember { mutableStateOf<String?>(null) }
                var manualSwitchFallback by remember { mutableStateOf<VetroVideo?>(null) }
                var failedRenditionUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
                var landscape by rememberSaveable { mutableStateOf(true) }

                BackHandler { finish() }
                DisposableEffect(landscape) {
                    requestedOrientation = if (landscape) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    onDispose {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }

                val player = remember(current.url, current.resolvedAt) {
                    HeaderResolvingPlayerFactory.createPlayer(
                        this@StreamPlayerActivity,
                        okHttpClient,
                        current,
                    ).apply {
                        setMediaSource(HeaderResolvingPlayerFactory.buildMediaSource(okHttpClient, current))
                        playWhenReady = true
                        prepare()
                        if (resumePosition > 0L) seekTo(resumePosition)
                    }
                }

                fun refreshStream() {
                    if (retrying) return
                    val nextIndex = (currentIndex + 1..candidates.lastIndex)
                        .firstOrNull { candidates[it].url != current.url }
                    if (nextIndex != null) {
                        resumePosition = player.currentPosition.coerceAtLeast(0L)
                        currentIndex = nextIndex
                        playbackError = null
                        val next = candidates[nextIndex]
                        Log.i(
                            TAG,
                            "Switching to fallback ${nextIndex + 1}/${candidates.size}",
                        )
                        current = next.copy(resolvedAt = System.currentTimeMillis())
                        return
                    }
                    retrying = true
                    playbackError = null
                    resumePosition = player.currentPosition.coerceAtLeast(0L)
                    scope.launch {
                        val refreshed = resolveReplacement(
                            animeId = animeId,
                            animeTitle = animeTitle,
                            animeTitleEn = animeTitleEn,
                            animeTitleRu = animeTitleRu,
                            malId = malId,
                            anilistId = anilistId,
                            episode = episode,
                            seasonInfo = seasonInfo,
                            previous = current,
                            excludedUrls = candidates.mapTo(mutableSetOf()) { it.url },
                        )
                        retrying = false
                        if (refreshed != null) {
                            VetroVideoCache.put(
                                VetroVideoCache.key(
                                    animeId,
                                    episode,
                                    "best",
                                    refreshed.label,
                                ),
                                refreshed,
                            )
                            val updated = candidates + refreshed
                            candidates = updated
                            currentIndex = updated.lastIndex
                            current = refreshed.copy(resolvedAt = System.currentTimeMillis())
                        } else {
                            playbackError = "Источник больше не отвечает. Попробуйте ещё раз."
                        }
                    }
                }

                LaunchedEffect(player, stored) {
                    if (storedResumeApplied) return@LaunchedEffect
                    val saved = stored ?: return@LaunchedEffect
                    storedResumeApplied = true
                    if (!saved.watched && saved.positionMs > 0L) {
                        player.seekTo(saved.positionMs)
                    }
                }

                LaunchedEffect(player, animeId, season, episode) {
                    while (isActive) {
                        val duration = player.duration
                        if (duration > 0L) {
                            playbackStore.saveProgress(
                                animeId = animeId,
                                season = season,
                                episode = episode,
                                positionMs = player.currentPosition,
                                durationMs = duration,
                            )
                        }
                        delay(1_000L)
                    }
                }

                DisposableEffect(player, current.url) {
                    activePlayer = player
                    val mediaSession = MediaSession.Builder(this@StreamPlayerActivity, player)
                        .setId("vetro-stream-${System.currentTimeMillis()}")
                        .build()
                    val listener = object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.w(TAG, "Playback failed: ${error.errorCodeName}", error)
                            val previousRendition = manualSwitchFallback
                            if (previousRendition != null) {
                                Log.w(TAG, "Manual rendition failed; rolling back from ${current.sourceName}")
                                failedRenditionUrls = failedRenditionUrls + current.url
                                manualSwitchFallback = null
                                automaticRetries = 0
                                playbackError = null
                                current = previousRendition.copy(resolvedAt = System.currentTimeMillis())
                                return
                            }
                            // Чаще всего падает не сам кандидат, а соединение/протухший сегмент,
                            // поэтому первую попытку тратим на пересборку плеера с тем же URL —
                            // уходить к другой озвучке/качеству имеет смысл только если и это не помогло.
                            if (automaticRetries == 0) {
                                Log.i(TAG, "Retrying same stream URL before fallback")
                                automaticRetries = 1
                                resumePosition = player.currentPosition.coerceAtLeast(0L)
                                playbackError = null
                                current = current.copy(resolvedAt = System.currentTimeMillis())
                                return
                            }
                            if (automaticRetries < MAX_AUTOMATIC_RETRIES) {
                                automaticRetries += 1
                                refreshStream()
                            } else {
                                playbackError = error.localizedMessage
                                    ?: "Не удалось воспроизвести поток"
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                manualSwitchFallback = null
                                automaticRetries = 0
                            }
                            if (playbackState == Player.STATE_ENDED && player.duration > 0L) {
                                scope.launch {
                                    playbackStore.saveProgress(
                                        animeId = animeId,
                                        season = season,
                                        episode = episode,
                                        positionMs = player.duration,
                                        durationMs = player.duration,
                                    )
                                }
                            }
                        }
                    }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                        mediaSession.release()
                        if (activePlayer === player) activePlayer = null
                        player.release()
                    }
                }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    StreamPlayerSurface(
                        player = player,
                        video = current,
                        renditions = selectStudioRenditions(
                            candidates.filterNot { it.url in failedRenditionUrls },
                            current,
                        ),
                        onSelectRendition = { rendition ->
                            if (rendition.url != current.url) {
                                resumePosition = player.currentPosition.coerceAtLeast(0L)
                                manualSwitchFallback = current
                                automaticRetries = 0
                                val selectedIndex = candidates.indexOfFirst {
                                    it.url == rendition.url
                                }
                                if (selectedIndex >= 0) currentIndex = selectedIndex
                                playbackError = null
                                current = rendition.copy(resolvedAt = System.currentTimeMillis())
                            }
                        },
                        title = "$animeTitle · ${if (season > 1) "S$season " else ""}E$episode",
                        episodeNumber = episode,
                        malId = seasonInfo?.malId ?: malId,
                        anilistId = seasonInfo?.anilistId ?: anilistId,
                        autoSkipEnabled = autoSkip,
                        isInPip = pipState.value,
                        onEnterPip = ::enterPip,
                        onRotate = { landscape = !landscape },
                        onBack = { finish() },
                    )

                    if (retrying || playbackError != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = if (retrying) {
                                    "Обновляем ссылку на поток…"
                                } else {
                                    playbackError.orEmpty()
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (!retrying) {
                                Button(
                                    onClick = {
                                        automaticRetries = 0
                                        refreshStream()
                                    }
                                ) {
                                    Text("Повторить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        persistActivePlayer()
        super.onStop()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipState.value = isInPictureInPictureMode
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    private fun persistActivePlayer() {
        val player = activePlayer ?: return
        val duration = player.duration
        if (duration <= 0L || activeAnimeId.isBlank()) return
        val position = player.currentPosition.coerceIn(0L, duration)
        lifecycleScope.launch {
            playbackStore.saveProgress(
                animeId = activeAnimeId,
                season = activeSeason,
                episode = activeEpisode,
                positionMs = position,
                durationMs = duration,
            )
        }
    }

    private suspend fun resolveReplacement(
        animeId: String,
        animeTitle: String,
        animeTitleEn: String?,
        animeTitleRu: String?,
        malId: Int?,
        anilistId: Int?,
        episode: Int,
        seasonInfo: SeasonInfo?,
        previous: VetroVideo,
        excludedUrls: Set<String>,
    ): VetroVideo? {
        val anime = Anime(
            id = animeId,
            title = seasonInfo?.title?.takeIf { it.isNotBlank() } ?: animeTitle,
            titleEn = animeTitleEn,
            titleRu = animeTitleRu,
            episodes = seasonInfo?.episodes?.coerceAtLeast(episode) ?: episode,
            rating = 0f,
            imageFileName = null,
            orderIndex = 0,
            dateAdded = 0L,
            anilistId = seasonInfo?.anilistId ?: anilistId,
            malId = seasonInfo?.malId ?: malId,
        )
        val videos = flattenVideosWithSource(mediaGateway.resolveHosters(anime, episode, seasonInfo))
        val preferredResolution = previous.resolution ?: 1080
        return rankVideosForResolution(videos, preferredResolution)
            .firstOrNull { it.url !in excludedUrls }
    }

    companion object {
        private const val TAG = "StreamPlayer"
        private const val MAX_AUTOMATIC_RETRIES = 8
        private const val EXTRA_VIDEO_JSON = "video_json"
        private const val EXTRA_VIDEOS_JSON = "videos_json"
        private const val EXTRA_ANIME_ID = "anime_id"
        private const val EXTRA_ANIME_TITLE = "anime_title"
        private const val EXTRA_ANIME_TITLE_EN = "anime_title_en"
        private const val EXTRA_ANIME_TITLE_RU = "anime_title_ru"
        private const val EXTRA_MAL_ID = "mal_id"
        private const val EXTRA_ANILIST_ID = "anilist_id"
        private const val EXTRA_SEASON = "season"
        private const val EXTRA_EPISODE = "episode"
        private const val EXTRA_SEASON_JSON = "season_json"
        private val intentJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        fun intent(
            context: Context,
            video: VetroVideo,
            animeId: String,
            animeTitle: String,
            episode: Int,
            videos: List<VetroVideo> = listOf(video),
            season: Int = 1,
            animeTitleEn: String? = null,
            animeTitleRu: String? = null,
            malId: Int? = null,
            anilistId: Int? = null,
            seasonInfo: SeasonInfo? = null,
        ): Intent = Intent(context, StreamPlayerActivity::class.java).apply {
            putExtra(
                EXTRA_VIDEO_JSON,
                intentJson.encodeToString(VetroVideo.serializer(), video),
            )
            putExtra(
                EXTRA_VIDEOS_JSON,
                intentJson.encodeToString(
                    ListSerializer(VetroVideo.serializer()),
                    videos.distinctBy { it.url },
                ),
            )
            putExtra(EXTRA_ANIME_ID, animeId)
            putExtra(EXTRA_ANIME_TITLE, animeTitle)
            putExtra(EXTRA_ANIME_TITLE_EN, animeTitleEn)
            putExtra(EXTRA_ANIME_TITLE_RU, animeTitleRu)
            putExtra(EXTRA_MAL_ID, malId ?: -1)
            putExtra(EXTRA_ANILIST_ID, anilistId ?: -1)
            putExtra(EXTRA_SEASON, season)
            putExtra(EXTRA_EPISODE, episode)
            seasonInfo?.let {
                putExtra(
                    EXTRA_SEASON_JSON,
                    intentJson.encodeToString(SeasonInfo.serializer(), it),
                )
            }
        }
    }
}
private fun selectStudioRenditions(
    videos: List<VetroVideo>,
    current: VetroVideo,
): List<VetroVideo> {
    val grouped = videos
        .filter { !it.sourceName.isNullOrBlank() }
        .groupBy { it.sourceName.orEmpty() }
    if (grouped.size <= 1) return emptyList()
    val targetResolution = current.resolution ?: 1080
    return grouped.values.mapNotNull { variants ->
        variants.minByOrNull { candidate ->
            abs((candidate.resolution ?: targetResolution) - targetResolution)
        }
    }.distinctBy { it.url }
}