package com.example.myapplication.localplayer.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import com.example.myapplication.localplayer.domain.MediaSkipCoordinator
import com.example.myapplication.localplayer.domain.SkipMediaKey
import com.example.myapplication.localplayer.domain.SkipSeekDecision
import com.example.myapplication.localplayer.domain.SkipSegment
import com.example.myapplication.localplayer.domain.SkipSegmentRequest
import com.example.myapplication.localplayer.domain.SkipSegmentResolution
import com.example.myapplication.localplayer.domain.SkipSegmentResolver
import com.example.myapplication.media.source.VetroSkipReference
import com.example.myapplication.media.source.VetroTimestamp
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

data class MediaSkipPlaybackState(
    val activeSegment: SkipSegment?,
    val manualSkip: () -> Unit,
)

/**
 * Common Compose adapter around [MediaSkipCoordinator]. It deliberately has no controls/PiP input:
 * segment entry and automatic seek continue while the overlay is hidden or the Activity is in PiP.
 */
@Composable
fun rememberMediaSkipPlayback(
    player: ExoPlayer,
    mediaId: String,
    diagnosticEpisodeKey: String,
    episodeNumber: Int?,
    anilistId: Int?,
    malId: Int?,
    durationMs: Long,
    positionMs: Long,
    autoSkipEnabled: Boolean,
    exactTimestamps: List<VetroTimestamp> = emptyList(),
    exactOrigin: String? = null,
    reference: VetroSkipReference? = null,
): MediaSkipPlaybackState {
    val resolver = koinInject<SkipSegmentResolver>()
    val coordinator = remember { MediaSkipCoordinator() }
    val mediaKey = remember(player, mediaId, episodeNumber) {
        SkipMediaKey(
            playerIdentity = System.identityHashCode(player),
            mediaId = mediaId,
            episodeNumber = episodeNumber,
        )
    }
    val request = remember(
        mediaKey,
        anilistId,
        malId,
        durationMs,
        exactTimestamps,
        exactOrigin,
        reference,
    ) {
        SkipSegmentRequest(
            anilistId = anilistId,
            malId = malId,
            episodeNumber = episodeNumber,
            durationMs = durationMs,
            exactTimestamps = exactTimestamps,
            exactOrigin = exactOrigin,
            reference = reference,
        )
    }
    var resolved by remember {
        mutableStateOf<Pair<SkipMediaKey, SkipSegmentResolution>?>(null)
    }
    val latestMediaKey by rememberUpdatedState(mediaKey)
    val latestRequest by rememberUpdatedState(request)
    val durationKnown = durationMs > 0L

    LaunchedEffect(mediaKey, request) {
        val empty = SkipSegmentResolution(emptyList(), null, null)
        resolved = null
        coordinator.install(mediaKey, empty)
        if (!durationKnown) return@LaunchedEffect
        val resolution = try {
            resolver.resolve(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            empty
        }
        if (latestMediaKey != mediaKey || latestRequest != request) return@LaunchedEffect
        coordinator.install(mediaKey, resolution)
        resolved = mediaKey to resolution
    }

    val currentResolution = resolved?.takeIf { it.first == mediaKey }?.second
    val active = currentResolution?.let {
        coordinator.activeSegment(mediaKey, positionMs)
    }
    val latestPosition by rememberUpdatedState(positionMs)
    val latestResolution by rememberUpdatedState(currentResolution)
    val diagnosticSnapshot = remember(diagnosticEpisodeKey) {
        MediaDiagnosticSnapshot(
            resolution = currentResolution,
            durationMs = durationMs,
        )
    }
    SideEffect {
        if (currentResolution != null) {
            diagnosticSnapshot.resolution = currentResolution
        }
        if (durationMs > 0L) diagnosticSnapshot.durationMs = durationMs
    }

    DisposableEffect(player, mediaKey) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                coordinator.onPositionDiscontinuity(mediaKey, newPosition.positionMs)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(diagnosticEpisodeKey) {
        onDispose {
            SkipDiagnostics.logOnce(
                diagnosticEpisodeKey,
                diagnosticSnapshot.resolution,
                diagnosticSnapshot.durationMs,
                decision = null,
                noSeekResult = "not_applied:episode_left",
            )
        }
    }

    LaunchedEffect(mediaKey, active, positionMs, autoSkipEnabled) {
        val decision = coordinator.automaticSeek(mediaKey, positionMs, autoSkipEnabled)
        if (decision != null) {
            player.seekTo(decision.targetMs)
            SkipDiagnostics.logOnce(
                diagnosticEpisodeKey,
                currentResolution,
                durationMs,
                decision,
            )
        }
    }

    return MediaSkipPlaybackState(
        activeSegment = active,
        manualSkip = {
            coordinator.manualSeek(mediaKey, latestPosition)?.let { decision ->
                player.seekTo(decision.targetMs)
                SkipDiagnostics.logOnce(
                    diagnosticEpisodeKey,
                    latestResolution,
                    durationMs,
                    decision,
                )
            }
        },
    )
}

private class MediaDiagnosticSnapshot(
    var resolution: SkipSegmentResolution?,
    var durationMs: Long,
)

private object SkipDiagnostics {
    private val loggedEpisodes = ConcurrentHashMap.newKeySet<String>()

    fun logOnce(
        episodeKey: String,
        resolution: SkipSegmentResolution?,
        currentDurationMs: Long,
        decision: SkipSeekDecision?,
        noSeekResult: String = "not_applied:no_decision",
    ) {
        if (!loggedEpisodes.add(episodeKey)) return
        val segments = resolution?.segments.orEmpty().joinToString(
            prefix = "[",
            postfix = "]",
        ) { "${it.kind}:${it.startMs}-${it.endMs}" }
        runCatching {
            Log.i(
                TAG,
                "episode=$episodeKey origin=${resolution?.origin ?: "none"} " +
                    "referenceDurationMs=${resolution?.referenceDurationMs} " +
                    "currentDurationMs=$currentDurationMs segments=$segments " +
                    if (decision == null) {
                        "seek=$noSeekResult"
                    } else {
                        "seek=${decision.reason}:applied->${decision.targetMs}"
                    },
            )
        }
    }

    private const val TAG = "VetroAutoskip"
}
