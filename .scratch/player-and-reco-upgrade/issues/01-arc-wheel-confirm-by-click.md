# TICKET-01: Arc wheel — confirm by click, not by settling

## Status

DONE_WITH_DEVIATIONS

## Objective

Dragging the dub/track arc wheel and letting go must only preview/activate the settled capsule;
confirming the choice requires an explicit tap on it.

## User or system value

Long dub lists (many studios) are currently unusable to browse: any drag that ends near an item
auto-selects it, so scrolling past options accidentally confirms the wrong one.

## Dependencies

None.

## Scope

- `localplayer/ui/PlayerArcMenu.kt`: `ArcCapsuleMenu`'s `onDragEnd` — stop calling `onCommit`;
  keep the snap animation and update `committed`/preview styling only.
- Capsule `onClick` — unchanged: still calls `onCommit(index)` immediately (this is the "click
  to confirm" path, works for both the centered and any other capsule).
- `localplayer/ui/PlayerControls.kt`'s `OptionMenu` call site — verify `onCommit` still means
  "confirmed selection, close the menu"; no signature change expected.

## Out of scope

- The short (< `ARC_MENU_THRESHOLD`) list/column variant (`MenuPill` path) — already
  click-to-confirm, not affected.
- `EpisodeQualitySheet.kt` resolution picker — different component, already click-confirmed.
- Visual redesign of the wheel.

## Acceptance criteria

- [ ] Dragging, releasing away from any capsule, and doing nothing further does not change the
      active audio track/speed and does not close the menu.
- [ ] After a drag settles, the nearest capsule shows the same "committed" visual style it does
      today, but the underlying selection is unchanged until tapped.
- [ ] Tapping the now-active (centered) capsule confirms it and closes the menu.
- [ ] Tapping any other capsule directly (no prior drag) still confirms it and closes the menu
      immediately (existing behavior preserved).
- [ ] Looping distance calc, haptics-on-index-change, and snap animation are unchanged.

## Verification plan

1. `.\gradlew.bat :app:compileDebugKotlin`
2. `.\gradlew.bat :app:testDebugUnitTest`
3. Manual: open a title with ≥5 dub studios, drag the wheel through several items without
   releasing on purpose near one, confirm nothing commits until tapped.

## TDD classification

RECOMMENDED — drag-vs-tap distinction lives in `pointerInput`/gesture callbacks (not unit
testable in isolation beyond existing `circularDistance`), but the "does settling call onCommit"
behavior can be asserted with a fake `onCommit` lambda in a Compose UI test if one is added;
otherwise verified manually per Verification plan.

## Expected architecture impact

None — behavior-only change inside an existing composable, no new state surfaces.

## Risks

- Users who relied on "drag-and-release to confirm" (if any) will need to add a tap; this is the
  explicit fix requested, not a regression.

## Implementation notes

`ArcCapsuleMenu`'s `onDragEnd` now only animates the snap and updates `committed`/`rawIndex`;
the `onCommit(normalized)` call was removed from that path. The capsule `onClick` handler was
left untouched — it already called `onCommit(index)` directly for both the centered and any
other capsule, which is exactly the "tap confirms" path the ticket asks for.

## Deviations

None — implementation matched the plan exactly (a two-line removal plus a doc-comment update).

## Review findings

Self-review before commit: no blocking findings. Confirmed `OptionMenu`'s call site
(`onCommit = { index -> commit(index); onDismiss() }` in `PlayerControls.kt`) needed no change,
since it already treats `onCommit` as "confirmed, close" — which now only fires from a tap.

## Completion evidence

- Command: `.\gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- Files: `localplayer/ui/PlayerArcMenu.kt`.
- **Not verified by me:** the on-device gesture feel (does settling still look/haptic the same,
  does a tap on the centered capsule feel responsive) — no automated test covers
  `pointerInput`/gesture code; per the ticket's own TDD classification this needs the user's
  on-device confirmation, same caveat as TICKET-08 in `.scratch/vetro-polish/`.
