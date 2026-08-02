# TICKET-03: "+89" seek bubble next to the bottom dock

## Status

DONE

## Objective

Add a bubble button, styled like the existing `SkipButton`, immediately left of the bottom-right
dock capsule (PiP/Rotate/Lock/Aspect). One tap seeks forward 89 seconds from the tap moment.

## User or system value

Fast, one-tap skip past a fixed-length segment (e.g. a recap or a slow cold open) without using
the drag-to-seek gesture or opening a menu.

## Dependencies

None. (Independent of TICKET-01/02 — different part of `PlayerControls.kt`.)

## Scope

- `localplayer/ui/PlayerControls.kt`: new bubble composable (reuse/adapt the existing
  `SkipButton` visual style — capsule, ambient dock surface) placed in the bottom row, to the
  left of the `PiP/Rotate/Lock/Aspect` dock `Row` (~line 576-621), inside the same
  `AnimatedVisibility(visible = controlsVisible, ...)` block so it hides/shows with the rest of
  the bottom dock.
- On tap: `player.seekTo((player.currentPosition + 89_000L).coerceAtMost(duration))`.
- Applies to both players via the shared `PlayerControlsOverlay`/`PlayerControls.kt`.

## Out of scope

- Configurable seek amount or button position.
- A matching "-89" or rewind bubble — not requested.
- Changing the existing `SkipButton` (opening-skip) bubble's position or behavior.

## Acceptance criteria

- [ ] A "+89" bubble is visible to the left of the PiP/Rotate/Lock/Aspect dock whenever the
      bottom controls are visible, in both the local and streaming players.
- [ ] Tapping it once seeks forward exactly 89 seconds from the position at tap time.
- [ ] Seeking is clamped at `duration` (tapping near the end doesn't seek past it / doesn't
      crash).
- [ ] The bubble hides/shows together with the rest of the bottom dock (respects
      `controlsVisible`).
- [ ] Does not interfere with the existing double-tap seek-burst or the 2× hold gesture zones
      (button sits in the dock row, not over the video gesture surface).

## Verification plan

1. `.\gradlew.bat :app:compileDebugKotlin`
2. `.\gradlew.bat :app:testDebugUnitTest` (unit test the pure clamp function:
   `(position + 89_000).coerceAtMost(duration)` including the near-end case).
3. Manual: tap near the end of an episode, confirm it clamps instead of erroring; tap mid-episode,
   confirm exact +89s.

## TDD classification

RECOMMENDED — the seek-amount clamp is a pure one-liner, worth a test; the button
placement/visibility is verified manually.

## Expected architecture impact

None — one more button in an existing dock row, no new state beyond what `player`/`duration`
already provide.

## Risks

- Low. Main risk is layout crowding in the bottom row on narrow screens — verify visually during
  manual check.

## Implementation notes

- `localplayer/ui/PlayerControls.kt`: added pure `seekForwardTarget(position, duration,
  amountMs)` and a `SeekForwardBubble` composable (same `ambientDockSurface(Capsule, ...)` style
  as `SkipButton`, no icon, label `"+${SEEK_FORWARD_MS / 1000}"`). Placed in the bottom row
  between the `Spacer(weight(1f))` and the PiP/Rotate/Lock/Aspect dock `Row`, inside the same
  `AnimatedVisibility(controlsVisible)` block, so it shows/hides with the rest of the dock.
  `SEEK_FORWARD_MS = 89_000L` constant next to the existing `SEEK_STEP_MS`.
- Shared file → applies to both players automatically, consistent with TICKET-08's precedent.

## Deviations

None — matched the plan.

## Review findings

Self-review before commit: no blocking findings. Button sits inside the existing bottom `Row`
(not a floating overlay like `SkipButton`), so it participates in the row's own layout and won't
overlap the video gesture surface.

## Completion evidence

- Command: `.\gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.example.myapplication.localplayer.ui.PlayerControlsSeekTest"` — BUILD SUCCESSFUL, all
  cases pass.
- Files: `localplayer/ui/PlayerControls.kt`, `test/.../PlayerControlsSeekTest.kt` (new).
- **Not verified by me:** on-device visual placement/crowding on a real screen — confirms with
  the user.
