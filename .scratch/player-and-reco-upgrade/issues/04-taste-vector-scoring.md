# TICKET-04: Taste-vector scoring (Reko/Mangaki-inspired, single-user)

## Status

DONE

## Objective

Replace the ad hoc additive `score()` formula in `RecommendationScorer`
(`genreScore + coOccurrence + ratingBoost + seedBoost`, unrelated units summed with magic
constants) with a compact taste vector over the user's existing tag-affinity dimensions and a
cosine-similarity comparison against each candidate's own tag vector — Reko's "compact profile,
cheap comparison" idea and Mangaki's "hybrid content + rating features" idea, adapted to the
data this app actually has (tags/genres + user ratings; no format/studio fields exist on `Anime`
today, so those dimensions are not invented here).

## User or system value

More principled, tunable ranking; a foundation TICKET-05's similarity weighting builds on
instead of layering more ad hoc constants onto the current formula.

## Dependencies

None (first of the three reco tickets; TICKET-05 depends on this one landing first to avoid
concurrent edits to the same scoring surface).

## Scope

- `domain/recommendations/GenreAffinityCalculator.kt` and/or a new module: keep producing the
  user's tag-affinity vector (existing logic/output shape can likely stay close to as-is).
- `domain/recommendations/RecommendationScorer.kt`: replace `score()`'s additive formula with a
  cosine-similarity term between the user's affinity vector and the candidate's own tag vector
  (built from `entries.genres` mapped through the same `genreToTagId`), combined with the
  existing rating/seed signals in a single documented formula (still simple — this is not a
  request to build a generalized ML pipeline).
- Update `RecommendationEngineUnitTest.kt` deliberately for the new scoring shape.

## Out of scope

- Adding new content dimensions (format, studio, era) not currently present on the `Anime`/
  `ApiSearchResult` models — would require new data plumbing, separate ticket if wanted later.
- Changing `RecommendationFilter.kt` (dedup/eligibility filtering) — scoring only.
- TICKET-05's co-occurrence weighting and TICKET-06's visual signal — separate tickets.

## Acceptance criteria

- [ ] `RecommendationScorer` computes a candidate's score using cosine similarity between the
      user's tag-affinity vector and the candidate's tag vector, not a flat average.
- [ ] Existing signals (co-occurrence bonus as it stands today, rating boost, seed boost) remain
      present in the combined score — TICKET-04 changes the genre term's shape, not the whole
      formula's inputs (co-occurrence reshaping is TICKET-05).
- [ ] `RecommendationEngineUnitTest.kt` passes with assertions updated to match the new formula's
      intent (documented in Deviations/Implementation notes if ranking order changes for the
      existing fixtures).
- [ ] No new field is added to `Anime`/`ApiSearchResult` to support this ticket.

## Verification plan

1. `.\gradlew.bat :app:compileDebugKotlin`
2. `.\gradlew.bat :app:testDebugUnitTest`
3. Sanity check: for a fixture library skewed toward one genre cluster, candidates sharing more
   of that cluster should rank above candidates sharing less, same as before — cosine similarity
   should not invert obviously-correct existing orderings.

## TDD classification

REQUIRED — deterministic scoring math, directly and cheaply unit-testable; write the cosine
similarity tests first.

## Expected architecture impact

Low: stays inside `domain/recommendations`, no new public API beyond what `RecommendationEngine`
already calls into `RecommendationScorer` with.

## Risks

- Reweighting could shuffle today's recommendation ordering in ways that feel different to the
  user even when "more correct" — no way to A/B in this app, so lean on the sanity check above
  and keep the change reviewable/revertable in one ticket-scoped commit.

## Implementation notes

- `RecommendationScorer.kt`: `genreScore` is now `cosineGenreScore(genreTagIds)` — cosine
  similarity between the full `affinity` vector (magnitude computed once per scorer instance via
  a lazily-cached `affinityNorm`, not per candidate) and the candidate's multi-hot tag vector
  (1 per distinct matched tag). `GenreAffinityCalculator` was left as-is — it already produces
  exactly the vector shape (`Map<tagId, Float>` in `[-1, 1]`) this scoring needed; no changes
  were necessary there, so the "and/or a new module" option in Scope wasn't used.
- `RecommendationEngineUnitTest.kt`: added
  `cosine_scoring_rewards_matching_more_of_the_users_liked_tags`, which specifically exercises
  the property a flat average could not express (matching two liked tags outscores matching one).

## Deviations

- **Planned:** possibly touch `GenreAffinityCalculator.kt` too.
  **Actual:** no changes needed there — its output was already the right vector shape.
  **Reason:** re-reading it during implementation showed the averaging problem lived entirely in
  `RecommendationScorer.score()`, not in how the vector itself was built.
  **Consequence:** smaller diff than planned. **Follow-up:** none.

## Review findings

Self-review before commit: verified by hand that all *existing* `RecommendationScorerTest` cases
stay valid under cosine scoring (traced through `affinity=emptyMap()` → `affinityNorm=0` →
`cosineGenreScore` returns 0, identical to the old formula's "no matched genres" case for every
test that doesn't pass an explicit non-empty affinity map; the one test that does
(`genre_affinity_ranks_matching_candidate_higher`) still orders correctly under cosine — traced
the arithmetic by hand). No blocking findings.

## Completion evidence

- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.example.myapplication.domain.recommendations.*"` — BUILD SUCCESSFUL, all cases pass
  (existing suite unmodified in behavior + 1 new test).
- Files: `domain/recommendations/RecommendationScorer.kt`,
  `test/.../RecommendationEngineUnitTest.kt`.
