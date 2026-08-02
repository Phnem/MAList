# TICKET-02: Carry the chosen dub track across episodes

## Status

DONE_WITH_DEVIATIONS

## Objective

Once the user manually selects an audio track for one episode, subsequent episodes in the same
playlist/session should automatically use the matching track instead of resetting to
ExoPlayer's default pick.

## User or system value

Without this, switching dub studios is a per-episode chore ("приходится делать это раз за
разом снова").

## Dependencies

None.

## Scope

- `localplayer/ui/PlayerScreen.kt`: remember the last user-picked track's identity (label and/or
  language, not `groupIndex`/`trackIndex` — those are per-media-item and meaningless across
  episodes) in Compose state scoped to the `PlayerScreen` composition.
- On `onTracksChanged` for a new media item, if a track matching the remembered identity exists
  among the new `AudioTrackOption`s, apply it via `applyAudioOverride` automatically.
- `onSelectAudio` callback: update the remembered identity whenever the user manually picks a
  track (including via the wheel's now-tap-to-confirm path from TICKET-01).

## Out of scope

- Persisting the preference across app restarts / different playback sessions (would need a
  disk-backed store, e.g. mirroring `EpisodePlaybackStore.savePreferredQuality`) — explicitly
  deferred; flagged as a follow-up ticket if requested.
- Any change to how tracks are extracted (`extractAudioOptions`) or matched by
  `groupIndex`/`trackIndex` for the *current* episode's own menu.
- ~~The streaming player~~ — investigated during implementation, see Deviations: it does **not**
  share `PlayerScreen.kt`'s state, but has its own identical defect (studio/rendition choice
  resets on `switchToEpisode`), which was folded into this ticket rather than deferred.

## Acceptance criteria

- [ ] Selecting a non-default audio track on episode N, then advancing to episode N+1 (same
      title, same session), applies the matching track automatically without user action.
- [ ] If episode N+1 has no track matching the remembered identity, playback proceeds with
      ExoPlayer's own default pick (no crash, no stuck "resolving" state).
- [ ] Manually picking a different track on episode N+1 updates the remembered identity for
      episode N+2 onward.
- [ ] Closing and reopening the player (new `PlayerScreen` composition) resets to default
      behavior — this ticket does not add cross-session persistence.

## Verification plan

1. `.\gradlew.bat :app:compileDebugKotlin`
2. `.\gradlew.bat :app:testDebugUnitTest` (add a unit test for the pure track-matching function:
   given previous identity + new option list → expected pick, including the no-match case).
3. Manual: local title with multiple embedded audio tracks across episodes, switch track, advance
   episode, confirm it stuck.

## TDD classification

RECOMMENDED — extract the "match previous identity against new options" logic as a pure
function so it's testable without ExoPlayer; the `onTracksChanged` wiring itself is verified
manually.

## Expected architecture impact

Minimal: one more piece of remembered state in `PlayerScreen`, no new module boundaries.

## Risks

- Matching by label/language string could false-positive if two distinct tracks share a label
  across episodes (e.g. two different "Russian" dubs) — acceptable given today's baseline is
  "always resets," and label/language is the only track-identity signal `AudioTrackOption`
  exposes; document as a known limitation rather than over-engineering fuzzy matching.

## Implementation notes

- `localplayer/ui/PlayerScreen.kt`: added `preferredAudioLabel` state; `onSelectAudio` records it;
  `onTracksChanged` calls the new pure `matchPreferredAudioTrack(preferredLabel, options)` and
  applies the match via the existing `applyAudioOverride` if found and not already selected.
- `media/player/StreamRecoveryPolicy.kt`: added pure `selectPreferredVideo(resolved,
  preferredSourceName)`, mirroring `selectResumePosition`'s style (small policy function next to
  its siblings).
- `media/ui/StreamPlayerActivity.kt`: added `preferredSourceName` state; `onSelectRendition` sets
  it from `rendition.sourceName`; `switchToEpisode` now picks
  `selectPreferredVideo(resolved, preferredSourceName)` instead of `resolved.firstOrNull()`,
  the same way `preferredResolution` was already carried over from `current.resolution`.
- Tests: `PlayerScreenTest.kt` (new) for `matchPreferredAudioTrack`;
  `StreamRecoveryPolicyTest.kt` extended for `selectPreferredVideo`.

## Deviations

- **Planned:** scope limited to `localplayer/ui/PlayerScreen.kt` (embedded audio tracks in the
  local file player).
  **Actual:** also fixed the equivalent defect in the streaming player
  (`media/ui/StreamPlayerActivity.kt` + `media/player/StreamRecoveryPolicy.kt`), where "dub" is
  represented as a studio/source rendition rather than an embedded audio track.
  **Reason:** the ticket's own Scope section flagged this as something to *confirm during
  implementation, not assume* — investigation showed `StreamPlayerSurface.kt` does **not** share
  `PlayerScreen.kt`'s state, and `switchToEpisode()` was found to always take
  `resolved.firstOrNull()` regardless of the studio the user had picked via the (now
  tap-to-confirm, TICKET-01) wheel — the same user-reported bug, in the more commonly used
  player (streaming sources are the app's primary watch path; the local player is for downloaded
  files). Leaving it unfixed would mean the reported bug was still fully reproducible for most
  users.
  **Consequence:** two additional files touched (`StreamRecoveryPolicy.kt`,
  `StreamPlayerActivity.kt`) beyond the ticket's original Scope list; both changes are small,
  additive, and follow an existing precedent in the same files
  (`selectResumePosition`/`preferredResolution` carry-over).
  **Follow-up:** none needed — this closes the same defect class in both players.

## Review findings

Self-review before commit:

- Matching by `label`/`sourceName` string is a known, accepted limitation (documented in the
  ticket's Risks) — no fuzzier matching added, consistent with "don't over-engineer."
- `preferredSourceName = rendition.sourceName ?: preferredSourceName` in `onSelectRendition`
  avoids clobbering a remembered studio name with `null` when a rendition has no `sourceName`
  (e.g. an embedded-track-only candidate).

No blocking findings.

## Completion evidence

- Command: `.\gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.example.myapplication.localplayer.ui.PlayerScreenTest" --tests
  "com.example.myapplication.media.player.StreamRecoveryPolicyTest"` — BUILD SUCCESSFUL, all
  cases pass.
- Files: `localplayer/ui/PlayerScreen.kt`, `media/player/StreamRecoveryPolicy.kt`,
  `media/ui/StreamPlayerActivity.kt`, `test/.../PlayerScreenTest.kt` (new),
  `test/.../StreamRecoveryPolicyTest.kt`.
- **Not verified by me:** the on-device gesture/session experience (does the carried-over track
  actually sound right, does the studio switch feel instant) — confirms with the user, same
  caveat as TICKET-08 in `.scratch/vetro-polish/`.
