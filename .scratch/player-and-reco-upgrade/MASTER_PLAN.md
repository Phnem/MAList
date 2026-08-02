# Player fixes + recommendation-engine upgrade — Master Plan

## Workflow

Current workflow state: FINAL_REVIEW
Current ticket: None
Last completed ticket: TICKET-06
Next eligible ticket: None — all six tickets complete
Last updated: 2026-08-02

## Goal

Fix three player-UX papercuts (dub-wheel confirm behavior, dub-track persistence across
episodes, a "+89" seek bubble) and upgrade the recommendation engine's scoring using ideas
adapted from Reko, Mangaki, Implicit and the BALSE paper — scoped to a single-user, on-device,
BYOK-AI app (no new backend, no on-device ML model).

## Canonical specification

`.scratch/player-and-reco-upgrade/spec.md`

## Architecture review

Not run as a separate heavyweight pass — all six tickets stay inside existing module
boundaries (`localplayer/ui`, `domain/recommendations`) with no new architectural style
introduced. See each ticket's "Expected architecture impact". TICKET-06 introduces one new
narrow class following an existing AI-feature-with-cache-and-throttle pattern already present
elsewhere in the codebase.

## Global constraints

- `PlayerControls.kt` is shared by both players — TICKET-01/03 changes apply to both
  automatically; verify neither breaks the streaming player.
- No multi-user backend, no on-device ML model (user decisions, see below).
- BYOK AI is optional everywhere — TICKET-06 must degrade silently with no provider configured.
- Recommendation cache (`RecommendationCacheStore`) must stay backward-compatible for snapshots
  already on disk.
- **Working tree already contained substantial unrelated uncommitted changes** before this
  workflow started (`PlayerControls.kt`, `PlayerScreen.kt`, `StreamPlayerActivity.kt` and many
  files outside `localplayer`/`media`/`domain/recommendations` — pre-existing WIP, likely
  `.scratch/reliable-autoskip*`/`.scratch/streaming-reliability` work). These files are edited
  precisely (unique, targeted `old_string` matches) so ticket diffs stay additive and isolated in
  intent, but the on-disk diff for those specific files is **not** commit-clean per ticket — it
  mixes ticket work with pre-existing unrelated changes. Per repository-protection rules and the
  platform instruction to never commit without explicit request, **no commits are made by this
  workflow**; all ticket work stays in the working tree for the user to review/stage/commit
  themselves (or to explicitly ask for commits once ready).

## Non-goals

- Cross-session persistence of the preferred dub track (TICKET-02 is session-scoped only).
- A shared/multi-user "similar users" backend.
- On-device embedding models.
- New `EpisodeQualitySheet.kt` behavior — unrelated component.

## Verification commands

### Fast checks

`.\gradlew.bat :app:compileDebugKotlin`

### Ticket checks

`.\gradlew.bat :app:testDebugUnitTest`

### Full checks

`.\gradlew.bat :app:assembleDebug`

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Arc wheel confirm-by-click | DONE_WITH_DEVIATIONS | — | | |
| TICKET-02 | Dub track carries across episodes | DONE_WITH_DEVIATIONS | — | | |
| TICKET-03 | "+89" seek bubble | DONE | — | | |
| TICKET-04 | Taste-vector scoring | DONE | — | | |
| TICKET-05 | Similarity-weighted co-occurrence | DONE | TICKET-04 | | |
| TICKET-06 | Visual cold-start via cover descriptors | DONE_WITH_DEVIATIONS | TICKET-04, TICKET-05 (soft) | | |

## Ticket details

### TICKET-01 — Arc wheel confirm-by-click

Status: PENDING
Dependencies: none
Acceptance criteria: see `issues/01-arc-wheel-confirm-by-click.md`

### TICKET-02 — Dub track carries across episodes

Status: PENDING
Dependencies: none
Acceptance criteria: see `issues/02-dub-track-carries-across-episodes.md`

### TICKET-03 — "+89" seek bubble

Status: PENDING
Dependencies: none
Acceptance criteria: see `issues/03-plus-89-seek-bubble.md`

### TICKET-04 — Taste-vector scoring

Status: PENDING
Dependencies: none
Acceptance criteria: see `issues/04-taste-vector-scoring.md`

### TICKET-05 — Similarity-weighted co-occurrence

Status: PENDING
Dependencies: TICKET-04
Acceptance criteria: see `issues/05-similarity-weighted-cooccurrence.md`

### TICKET-06 — Visual cold-start via cover descriptors

Status: PENDING
Dependencies: TICKET-04, TICKET-05 (sequenced after for a stable scoring surface, not a hard
data dependency)
Acceptance criteria: see `issues/06-visual-cold-start.md`

## Decisions

- **Single-user only, no shared backend.** User decision (interview, 2026-08-02): Reko's literal
  "find similar users" requires a multi-user ratings backend Vetro does not have (Supabase here
  is one user's private cross-device backup, not shared data). Reko/Mangaki/Implicit ideas are
  adapted to single-user signals (own history + public AniList/Shikimori/MAL related graphs).
  Follow-up: if a shared backend is ever built for other reasons, real cross-user matching
  becomes a candidate follow-up feature — not tracked as a ticket here.
- **Cold-start visual signal via cloud AI, not on-device.** User decision (interview,
  2026-08-02): use the app's existing BYOK vision-capable AI provider rather than adding an
  on-device TFLite/ONNX model. Implementation-level adaptation recorded in TICKET-06: since
  `AiProvider` has no embeddings endpoint, similarity is computed via cached visual descriptor
  tags extracted through a vision-chat call, not a literal embedding vector — this still
  satisfies "cloud AI provider" as decided.

## Global deviations

None yet.

## Known risks

- TICKET-04/05 reweight existing scoring; ranking changes are hard to A/B in this app — mitigate
  with focused unit tests and a sanity check against obviously-correct orderings (see each
  ticket's Risks).
- TICKET-06's real-world impact depends on how many users have a vision-capable BYOK key
  configured — accepted per user's own scoping decision.

## Deferred work

- Cross-session persistence of preferred dub track (TICKET-02 follow-up, not requested yet).
- New content dimensions (format/studio/era) for taste-vector scoring (TICKET-04 follow-up, not
  requested yet — would need new fields on `Anime`/`ApiSearchResult`).
- Multi-user "similar users" backend (descoped, see Decisions).

## Final acceptance checklist

- [x] Every required ticket completed (3 DONE, 3 DONE_WITH_DEVIATIONS — all deviations recorded
      and justified in each ticket file)
- [x] Full test suite run — `.\gradlew.bat :app:testDebugUnitTest` BUILD SUCCESSFUL; also
      `.\gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL (confirms DI wiring for TICKET-06)
- [x] Specification reviewed requirement by requirement — see Requirement audit below
- [x] No unresolved blocking review findings — each ticket's self-review found none
- [x] Migration and compatibility behavior verified — no schema changes; recommendation cache
      stays backward-compatible (verified: no field changes to `RecommendationItem`/
      `RecommendationsSnapshot`)
- [ ] User-visible behavior verified — **not verified by this workflow.** All six tickets touch
      gesture/animation/AI-dependent code paths that cannot be exercised from unit tests; every
      ticket file says so explicitly under "Not verified by me." Needs the user's own on-device
      pass.
- [x] Deferred work explicitly recorded — see Deferred work section
- [x] Final architecture checkpoint completed — no new architectural style introduced across any
      of the six tickets; see each ticket's "Expected architecture impact"

## Requirement audit (spec.md → implementation)

| Requirement | Status | Evidence |
|---|---|---|
| Arc wheel confirms only on tap | PASS | TICKET-01, `PlayerArcMenu.kt` |
| Dub track carries across episodes (local + streaming) | PASS | TICKET-02, both players fixed |
| "+89" seek bubble, both players | PASS | TICKET-03, shared `PlayerControls.kt` |
| Taste-vector cosine scoring | PASS | TICKET-04, `RecommendationScorer.kt` |
| Similarity-weighted co-occurrence | PASS | TICKET-05, `RecommendationScorer.kt` |
| Visual cold start, BYOK-gated, silent fallback | PASS (adapted) | TICKET-06, descriptor-tag
  proxy instead of a literal embedding — documented adaptation, not a scope change |
| Single-user only, no shared backend | PASS | no new backend anywhere in the six tickets |
| No cross-session dub persistence (out of scope) | NOT_APPLICABLE | correctly left undone |
| No on-device ML model | PASS | TICKET-06 uses BYOK cloud AI exclusively |
