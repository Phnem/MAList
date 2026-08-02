# Current handoff

## Original goal

Fix three player-UX papercuts (dub-wheel confirm-by-click, dub-track persistence across
episodes, a "+89" seek bubble) and upgrade the recommendation engine's scoring using ideas
adapted from Reko/Mangaki/Implicit/BALSE — single-user, on-device, BYOK-AI scope.

## Canonical artifacts

- `.scratch/player-and-reco-upgrade/spec.md`
- `.scratch/player-and-reco-upgrade/MASTER_PLAN.md`
- `.scratch/player-and-reco-upgrade/issues/01..06-*.md`

## Current workflow state

FINAL_REVIEW — all six tickets implemented and verified by build/test, none committed.

## Completed tickets

TICKET-01 through TICKET-06 — all DONE or DONE_WITH_DEVIATIONS. See each ticket file's
"Completion evidence" for exact commands run and files touched.

## Active ticket

None.

## Next eligible ticket

None — this master plan's scope is complete. Deferred follow-up work (see below) is not
tracked as tickets here.

## Decisions that must be preserved

- Single-user only; no shared/multi-user recommendation backend (user decision, 2026-08-02).
- Cold-start visual signal uses the app's existing BYOK vision-capable AI provider, adapted to
  descriptor-tag overlap (no embeddings endpoint exists in `AiProvider`) rather than a literal
  vector embedding (user decision, 2026-08-02, implementation-level adaptation documented in
  TICKET-06).

## Deviations that affect later work

- TICKET-02 expanded beyond its original file scope to also fix the streaming player's studio/
  rendition persistence (`StreamPlayerActivity.kt`, `StreamRecoveryPolicy.kt`) — see that
  ticket's Deviations for why.
- TICKET-06 did not wire in the full `AiThrottlePolicy` retry/backoff machinery — a rate-limited
  descriptor call is skipped silently rather than retried. See that ticket's Deviations for the
  reasoning; flagged in case real backoff is wanted later.

## Current repository state

**Nothing was committed.** The working tree already contained substantial unrelated
uncommitted changes before this workflow started (`PlayerControls.kt`, `PlayerScreen.kt`,
`StreamPlayerActivity.kt` and many files well outside this plan's scope — likely
`.scratch/reliable-autoskip*`/`.scratch/streaming-reliability` work in progress). Per repository
safety rules and the standing instruction to never commit without explicit request, all six
tickets' changes are left staged only in the working tree for the user to review and commit
however they prefer (in one batch, or split back out by ticket using each ticket file's
"Completion evidence" file list as a guide).

## Relevant commits

None — see above.

## Verification already performed

- `.\gradlew.bat :app:compileDebugKotlin` — repeated after each ticket, always BUILD SUCCESSFUL.
- `.\gradlew.bat :app:testDebugUnitTest` — full suite, BUILD SUCCESSFUL (includes 8 new tests
  across `PlayerScreenTest.kt`, `PlayerControlsSeekTest.kt`, `StreamRecoveryPolicyTest.kt`
  additions, `RecommendationEngineUnitTest.kt` additions, `VisualSimilarityTest.kt`).
- `.\gradlew.bat :app:assembleDebug` — BUILD SUCCESSFUL, confirms TICKET-06's new Koin wiring
  resolves at build time.

## Known failures or blockers

None. All gesture/animation/on-device-AI behavior is explicitly flagged as "not verified by me"
in each ticket — needs the user's own on-device pass before considering this feature-complete
from a UX standpoint, not just a build/test standpoint.

## Files most relevant to a review pass

Player fixes: `localplayer/ui/PlayerArcMenu.kt`, `localplayer/ui/PlayerScreen.kt`,
`localplayer/ui/PlayerControls.kt`, `media/ui/StreamPlayerActivity.kt`,
`media/player/StreamRecoveryPolicy.kt`.

Recommendation engine: `domain/recommendations/RecommendationScorer.kt`,
`domain/recommendations/RecommendationEngine.kt`,
`domain/recommendations/CoverDescriptorProvider.kt` (new),
`domain/recommendations/VisualSimilarity.kt` (new),
`data/local/CoverDescriptorCacheStore.kt` (new), `di/appModule.kt`.

## Exact recommended next action

1. Review the diff (it's entangled with pre-existing unrelated WIP in a few files — see
   "Current repository state").
2. On-device manual pass through each ticket's "Verification plan" (all six need real-device
   confirmation, none of it is automatable).
3. Commit when ready — ask explicitly, since this workflow will not commit unprompted.
