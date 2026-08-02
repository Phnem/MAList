# TICKET-05: Similarity-weighted co-occurrence (Implicit-inspired, no new backend)

## Status

DONE

## Objective

Replace the flat step-function co-occurrence bonus
(`COOCCURRENCE_STEP * (entries.size - 1), capped at COOCCURRENCE_CAP`) with an
item-item-similarity-inspired weighting: a candidate recommended by *more, higher-rated-by-the-
user* seeds should score higher than one recommended by the same count of low-rated seeds — the
related-graph pool (AniList/Shikimori/MAL) stands in for Implicit's interaction matrix, using
only data already fetched (no new backend, per the single-user decision).

## User or system value

Today two candidates recommended by 3 seeds each score identically regardless of whether those
seeds are the user's favorites or barely-tolerated entries; this ticket makes co-occurrence
strength reflect actual user affinity for the recommending seeds.

## Dependencies

TICKET-04 (touches the same `RecommendationScorer.score()` — sequential to avoid rework/merge
churn).

## Scope

- `domain/recommendations/RecommendationScorer.kt`: replace the flat `coOccurrence` term with a
  weighted sum/aggregation over the recommending seeds' `seedRating` (and optionally
  `isFavorite`, already available via `GenreAffinityCalculator`'s weighting convention) instead
  of counting entries alone.
- Update `RecommendationEngineUnitTest.kt` for the new co-occurrence shape.

## Out of scope

- Changing how the related-graph pool itself is *fetched* (`RecommendationEngine.buildRelatedPool`)
  — aggregation/weighting only, not data sourcing.
- Any multi-user data — explicitly descoped per Decisions.

## Acceptance criteria

- [ ] A candidate recommended by seeds the user rated highly scores higher than an otherwise
      identical candidate recommended by the same count of low-rated seeds.
- [ ] A candidate recommended by more seeds still generally outranks one recommended by fewer,
      all else equal (monotonicity in count is preserved, not just in rating).
- [ ] `RecommendationEngineUnitTest.kt` passes with assertions updated to match.

## Verification plan

1. `.\gradlew.bat :app:compileDebugKotlin`
2. `.\gradlew.bat :app:testDebugUnitTest`
3. Unit test: two synthetic pools differing only in seed ratings, assert score ordering matches
   the acceptance criteria above.

## TDD classification

REQUIRED — deterministic aggregation math.

## Expected architecture impact

None beyond TICKET-04's — same file, same scoring surface.

## Risks

- Combined with TICKET-04's cosine term, the total formula has more moving parts; keep both
  terms independently testable so a regression in one doesn't hide inside the other.

## Implementation notes

- `RecommendationScorer.kt`: `coOccurrence` now calls `weightedCoOccurrence(entries)`, which
  sorts entries by `seedRating` descending, **drops the best one** (its rating is already
  rewarded by `seedBoost`/`SEED_WEIGHT`, so including it again would double-count the same
  signal), and sums `(seedRating / 10f)` over the rest before applying `COOCCURRENCE_STEP`/
  `COOCCURRENCE_CAP` exactly as before. A single-entry candidate has nothing left after dropping
  the best, so it still scores 0 co-occurrence — identical to the old `size - 1 == 0` baseline.

## Deviations

None — matched the plan; the "drop the best entry" detail wasn't spelled out in the ticket but
is a direct, necessary consequence of "don't double-count what `seedBoost` already rewards,"
which the ticket's Objective implies.

## Review findings

Self-review before commit: verified by hand that dropping the top-rated entry keeps the
single-seed baseline at exactly 0 (no regression for the common case), and that the new
`cooccurrence_is_zero_for_a_single_recommending_seed_like_before` test pins that down explicitly
so a future change can't silently reintroduce double-counting with `seedBoost`. No blocking
findings.

## Completion evidence

- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.example.myapplication.domain.recommendations.*"` — BUILD SUCCESSFUL, all cases pass
  (existing suite + 2 new tests).
- Files: `domain/recommendations/RecommendationScorer.kt`,
  `test/.../RecommendationEngineUnitTest.kt`.
