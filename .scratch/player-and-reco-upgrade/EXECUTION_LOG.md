# Execution log — Player fixes + recommendation engine upgrade

## Initial codebase discovery

### Relevant modules

- `localplayer/ui/PlayerArcMenu.kt` — "барабан-дуга" (arc wheel), used for long option lists
  (`ARC_MENU_THRESHOLD = 5`) in the player's audio-track/speed menu.
- `localplayer/ui/PlayerControls.kt` — `OptionMenu` hosts the arc wheel for `PlayerMenu.AUDIO` /
  `PlayerMenu.SPEED`; shared by both players (`media/ui/StreamPlayerSurface.kt` also renders
  `PlayerControlsOverlay`). Bottom dock capsule (PiP/Rotate/Lock/Aspect) lives at
  ~line 586-620, right-aligned after a `Spacer(Modifier.weight(1f))`. `SkipButton` (existing
  bubble precedent) sits at `BottomEnd`, `padding(end = 16.dp, bottom = 104.dp)`.
- `localplayer/ui/PlayerScreen.kt` — owns `audioTracks: List<AudioTrackOption>` state and
  `applyAudioOverride`; the whole season plays as one ExoPlayer playlist inside one composition,
  so per-episode reset is a Compose-state problem, not a cross-session persistence problem.
- `domain/recommendations/{RecommendationEngine,RecommendationScorer,GenreAffinityCalculator,
  RecommendationModels,RecommendationFilter}.kt` — content-based engine: related-graph pooling
  (AniList batch / Shikimori similar / MAL recommendations) + genre-affinity weighted vector +
  ad hoc additive scoring (`genreScore + coOccurrence + ratingBoost + seedBoost`, magic
  constants). Cold start (`RecommendationStrategy.Onboarding`) falls back to unpersonalized
  AniList trending.
- `data/ai/AiProvider.kt` — BYOK "AI Connect" registry (OpenAI/Anthropic/Gemini/DeepSeek/Groq/
  OpenRouter/Cohere), chat + vision endpoints only, **no embeddings endpoint**. Vision-capable
  subset already used by an existing Visual Search feature.

### Existing behavior

- Arc wheel: `onDragEnd` snaps to nearest index **and immediately calls `onCommit`**, which the
  call site wires straight to `onDismiss()` — releasing a drag anywhere both confirms and closes
  the menu. Tapping a capsule directly does the same.
- Audio track: `onTracksChanged` re-extracts options per media item; ExoPlayer's own track
  selector re-picks a default for the new episode. No app-level memory of the previous pick.
- Recommendation engine is single-user only today; no shared/multi-user data model exists.
  Supabase is used exclusively as one user's private cross-device backup (`sync/supabase/*`).

### Existing terminology

- "Барабан-дуга" / arc wheel — long-list picker used for player menus.
- "Related pool" / "seed" — recommendation-engine vocabulary already in place; kept as-is.

### Existing tests

- `RecommendationEngineUnitTest.kt` covers `resolveStrategy` + scoring; must keep passing (or be
  updated deliberately) through TICKET-04/05.
- `PlayerZoomTest.kt` covers gesture-zone/multiplier pure functions (pattern to follow for new
  pure logic in TICKET-01/03).

### Constraints discovered

- `PlayerControls.kt` is shared between the local and streaming players — any dock/menu change
  there affects both automatically (already the pattern used for the 2× hold-to-speed feature,
  TICKET-08 of `.scratch/vetro-polish/`).
- AI features in this app are BYOK — there is no guaranteed default key. Any new AI-backed
  feature must degrade silently (no key/vision-capable provider configured → skip, no error
  surfaced) rather than assume availability.

### Questions answerable from code

- Where the dub-selection wheel lives and how selection currently commits: answered by reading
  `PlayerArcMenu.kt` / `PlayerControls.kt`.
- Why dub selection resets per episode: answered by reading `PlayerScreen.kt` (fresh
  `onTracksChanged` per media item, no override re-application).
- Where to add the seek bubble: answered by reading the bottom row of `PlayerControlsOverlay`.

### Remaining material uncertainties (resolved via user interview)

- Whether "similar users" (Reko's core feature) requires a new multi-user backend →
  **resolved: single-user only**, see Decisions in `MASTER_PLAN.md`.
- How to source cover embeddings for BALSE-style cold start given no embeddings endpoint exists
  → **resolved: reuse existing BYOK vision-capable provider** via descriptor extraction
  (see TICKET-06 for the adapted approach; this is an implementation-level adaptation, not a
  scope change from the user's answer).
