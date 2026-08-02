# Current handoff

## Original goal

Fix the observed autoskip miss and playback contention; pause automatic background enrichment for
players/readers and resume on exit.

## Canonical artifacts

- `.scratch/reliable-autoskip-followup/spec.md`
- `.scratch/reliable-autoskip-followup/MASTER_PLAN.md`
- `.scratch/reliable-autoskip-followup/architecture/INITIAL_REVIEW.md`

## Current workflow state

COMPLETED

## Completed tickets

TICKET-01 through TICKET-04.

## Active ticket

None.

## Next eligible ticket

None.

## Decisions that must be preserved

No timestamp scaling; opening-only relaxed reference compatibility; fail closed on season ambiguity;
pause automatic web-link enrichment for Activity lifetime including PiP.

## Current repository state

Dirty before this project with overlapping autoskip/player changes. No destructive Git operations.

## Verification already performed

All targeted tests, `:app:testDebugUnitTest`, and `:app:assembleDebug` pass.

## Known failures or blockers

None.

## Exact recommended next action

Install the debug APK and capture `StreamTelemetry` plus the once-per-episode skip diagnostic during
one real Food Wars S3E6 playback session.
