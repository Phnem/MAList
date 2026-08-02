# Player fixes + recommendation-engine upgrade

## Problem

Three player-UX papercuts make the dub-selection wheel and playback controls harder to use than
they should be, and the recommendation engine's scoring is an ad hoc additive formula that
doesn't borrow proven techniques from reference systems (Reko, Mangaki, Implicit, BALSE) the
user pointed at for inspiration.

## Desired outcome

1. The player's arc-wheel dub/track picker previews on settle and only confirms on an explicit
   tap.
2. A dub-track choice made on one episode carries over to the rest of the season within the same
   viewing session.
3. A "+89" bubble next to the bottom dock seeks forward 89s on a single tap.
4. The recommendation engine's scoring is upgraded using ideas adapted from Reko (compact taste
   vector + cosine-style comparison), Mangaki (hybrid content + rating features), Implicit
   (item-item similarity instead of a flat co-occurrence bonus) and BALSE (visual cold start) —
   scoped to what a **single-user, on-device, BYOK-AI** app can actually support (see Domain
   rules).

## Current behavior

See `EXECUTION_LOG.md` → "Existing behavior" for the full trace through the code.

## Required behavior

### Arc wheel confirm-by-click (TICKET-01)

- Dragging and releasing the wheel leaves the nearest-to-center capsule visually active
  (today's "committed" style) but does **not** call `onCommit` and does **not** close the menu.
- Tapping a capsule (whether or not it is already the active/centered one) confirms it: calls
  `onCommit(index)` and the menu closes, exactly like today's tap path.
- Looping/haptics/animation during drag are unchanged.

### Dub selection persists across episodes (TICKET-02)

- Once the user manually picks an audio track, the next episode(s) in the same playlist/session
  apply the same track automatically whenever a track with a matching identity (label/language)
  is available, instead of falling back to ExoPlayer's own default pick.
- If the new episode has no matching track, fall back to ExoPlayer's default selection (today's
  behavior) — do not error or block playback.
- Scope: in-memory, for the lifetime of the current `PlayerScreen` composition (i.e. the current
  viewing session / playlist). Cross-session (app-restart) persistence is explicitly out of
  scope (see Out of scope).

### "+89" seek bubble (TICKET-03)

- A new bubble, styled consistently with the existing `SkipButton` bubble, appears to the left of
  the bottom-right dock capsule (PiP / Rotate / Lock / Aspect), showing the label "+89".
- A single tap seeks forward 89 seconds from the position at the moment of the tap, clamped to
  `duration`.
- Present in both the local player and the streaming player (shared `PlayerControls.kt`).
- Follows the dock's existing show/hide-with-controls behavior; no new persistent state.

### Recommendation engine upgrade (TICKET-04/05/06)

- TICKET-04: replace the ad hoc additive `score()` formula and genre-only affinity with a
  compact multi-dimensional taste vector (tag affinity + normalized rating signal) and a
  cosine-similarity-based comparison between the user's vector and each candidate's vector.
- TICKET-05: replace the flat step-function co-occurrence bonus
  (`COOCCURRENCE_STEP * count, capped`) with similarity-weighted aggregation across the
  related-graph pool — still sourced entirely from the existing AniList/Shikimori/MAL related
  data (public, already fetched), no new backend or per-user data.
- TICKET-06: when the engine is in cold start (or low confidence), additionally rank the
  candidate pool by visual similarity between candidate covers and the user's own (already
  added, even unrated) collection covers, using the BYOK vision-capable AI provider if one is
  configured; silently skip this signal otherwise.

## User-visible behavior

- Wheel: scrolling feels the same; confirmation now requires a tap, so long lists are safe to
  browse without accidentally committing.
- Dub track: pick once per title, it sticks for the rest of that viewing session.
- Player: a small "+89" bubble next to the existing dock buttons.
- Recommendations: ranking quality changes (should improve), no new UI surface required by
  TICKET-04/05; TICKET-06 can surface a "Похоже на обложки в вашей коллекции" style seed label
  for cold-start items when the visual signal contributed to a pick (exact copy decided during
  implementation, no new `UiStrings` budget concerns expected — see uistrings-255-param-limit
  constraint).

## Domain rules

- **Single-user only.** No shared/multi-user ratings backend. "Similar users" (Reko's literal
  feature) is explicitly descoped; recommendation signal comes from the user's own history plus
  public related/recommendation graphs already integrated (AniList/Shikimori/MAL), which are
  themselves a form of third-party co-occurrence statistics.
- **BYOK AI is optional, never required.** Any AI-backed signal (TICKET-06) must degrade to "no
  extra signal" when no vision-capable provider is configured — never block or error the
  recommendation flow.
- **No embeddings endpoint exists.** `AiProvider` only exposes chat/vision chat completions, not
  a raw embedding vector API. BALSE's "embedding similarity" is adapted to a descriptor-based
  visual-similarity proxy (structured tags/attributes extracted via a vision-capable chat call,
  cached), not a literal vector embedding — see TICKET-06.

## Functional requirements

- TICKET-01 through TICKET-06 acceptance criteria (see individual ticket files).

## Non-functional requirements

- No regression to existing recommendation unit tests' intent (`RecommendationEngineUnitTest.kt`)
  — update tests deliberately alongside scoring changes, never silently.
- No new mandatory network/AI dependency for the player fixes (TICKET-01/02/03) — pure
  local/Compose-state changes.
- TICKET-06's AI calls must respect the existing throttle/backoff patterns already used for AI
  features in this codebase (see `AiThrottlePolicy`, `AiRateLimitException`-style handling in
  `domain/titles`/`domain/enrichment`).

## Compatibility and migration constraints

- None of the six tickets touch persisted schemas (Room/DataStore/Supabase) — all state is
  either in-memory (TICKET-02) or derived/cached recommendation output (TICKET-04/05/06, same
  `RecommendationCacheStore` shape, `RecommendationItem`/`RecommendationsSnapshot` fields may
  gain optional additions but must stay backward-compatible for cached snapshots already on
  disk — treat unknown/missing new fields as safe defaults on deserialize).

## Failure and fallback behavior

- TICKET-02: no matching track in the new episode → ExoPlayer's default pick (unchanged today).
- TICKET-06: no configured vision-capable provider, or the AI call fails/rate-limits → cold
  start behaves exactly as it does today (trending pool, no visual re-ranking); never blocks or
  delays the existing recommendation refresh path.

## Out of scope

- Building a multi-user/shared recommendation backend (Reko-style "similar users") — explicitly
  descoped by user decision.
- On-device ML embedding models (TFLite/ONNX) for cold start — explicitly descoped by user
  decision in favor of the cloud AI provider path.
- Cross-session (app-restart) persistence of the user's preferred dub track. (Candidate
  follow-up ticket if requested later.)
- Any change to the existing quality-picker (`EpisodeQualitySheet.kt` / resolution menu) — a
  different, already-click-confirmed component not affected by TICKET-01.
- Changing the arc wheel's use for the SPEED menu beyond the same confirm-by-click mechanics
  (behavior is uniform across AUDIO/SPEED, no special-casing).

## Acceptance criteria

- See per-ticket "Acceptance criteria" sections.

## Open questions

None outstanding — the two material unknowns (multi-user backend scope, cold-start embedding
source) were resolved via user interview before ticket planning; see Decisions in
`MASTER_PLAN.md`.

## Test seams

- `PlayerArcMenu.kt`: `circularDistance`, drag-settle vs. commit logic — extractable pure
  functions for unit tests, following the `PlayerZoomTest.kt` precedent.
- `PlayerScreen.kt`: track-matching function (given previous label/language + new
  `List<AudioTrackOption>`, return the option to apply) — pure, testable in isolation.
- `PlayerControls.kt`: seek-forward clamp (`(position + 89_000).coerceAtMost(duration)`) — pure.
- `RecommendationScorer.kt` / new taste-vector module: cosine similarity and co-occurrence
  weighting — pure functions, directly unit-testable (existing pattern in
  `RecommendationEngineUnitTest.kt`).
