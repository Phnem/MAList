# Autoskip and playback reliability follow-up — Master Plan

## Workflow

Current workflow state: COMPLETED
Current ticket: None
Last completed ticket: TICKET-04
Next eligible ticket: None
Last updated: 2026-07-31

## Goal

Repair the real-device autoskip miss, reduce playback/reader network contention, improve stream
resilience and diagnostics, and prevent credentials from leaking through Logcat.

## Canonical specification

`.scratch/reliable-autoskip-followup/spec.md`

## Architecture review

`.scratch/reliable-autoskip-followup/architecture/INITIAL_REVIEW.md`

## Global constraints

- Preserve existing dirty user work.
- No timestamp scaling.
- Fail closed on ambiguous season/source matches.
- Never log full media URLs, request headers, or credentials.

## Non-goals

See specification.

## Verification commands

### Fast checks

`.\gradlew.bat :app:testDebugUnitTest --tests "<target>"`

### Ticket checks

Targeted JVM tests plus affected Kotlin compilation.

### Full checks

`.\gradlew.bat :app:testDebugUnitTest`

`.\gradlew.bat :app:assembleDebug`

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Restore validated sources and usable opening references | COMPLETED | — | — | PASS |
| TICKET-02 | Suspend automatic web-link enrichment for media Activities | COMPLETED | TICKET-01 | — | PASS |
| TICKET-03 | Increase stream resilience and add privacy-safe diagnostics | COMPLETED | TICKET-02 | — | PASS |
| TICKET-04 | Prevent Supabase credentials from reaching Logcat | COMPLETED | TICKET-03 | — | PASS |

## Decisions

- Opening reference compatibility becomes 2% AND 30 seconds; other segment kinds stay strict.
- Automatic web-link enrichment pauses for the lifetime of stream/local player and manga reader
  Activities, including PiP.
- Manual full enrichment and downloads are not cancelled.
- Player recreation observed in the supplied log was user-initiated source switching, not an
  automatic player defect.

## Global deviations

The original strict `1%/15s` policy is retained for endings/recaps and non-jut references; only
jut.su openings use `2%/30s` to cover the captured Food Wars mismatch.

## Known risks

- AniLibria and jut.su are external and may change search behavior.
- The worktree already has overlapping uncommitted source/player changes.

## Deferred work

CDN health ranking after real telemetry samples are available.

## Final acceptance checklist

- [x] Every required ticket completed
- [x] Full unit tests and debug assembly pass
- [x] Specification audited requirement by requirement
- [x] No unresolved blocking review findings
- [x] Deferred work explicitly recorded
- [x] Final architecture checkpoint completed
