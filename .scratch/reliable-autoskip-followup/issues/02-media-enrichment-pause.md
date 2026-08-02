# TICKET-02: Suspend automatic web-link enrichment for media Activities

## Status

COMPLETED

## Objective

Give stream/local players and manga reader exclusive priority over automatic link enrichment.

## User or system value

Background metadata requests no longer compete with playback or reader image loading.

## Dependencies

TICKET-01.

## Scope

Pause-token module, coordinator/worker integration, three Activity lifecycle call sites, JVM tests.

## Out of scope

Downloads and manually started full enrichment.

## Acceptance criteria

- [x] First token cancels/suppresses automatic web-link work.
- [x] Nested tokens resume only after the last closes.
- [x] Tokens are idempotent.
- [x] Stream, local, and manga Activities hold a token for their entire lifetime.
- [x] Worker and continuation paths observe pause state.

## Verification plan

Pause-counter unit tests and relevant compilation/tests.

## TDD classification

REQUIRED

## Expected architecture impact

Deepens CollectionEnrichmentCoordinator behind one lifecycle interface.

## Risks

Cancellation/reschedule races and Activity recreation.

## Implementation notes

- A retained ViewModel owns the Activity token, so configuration recreation and PiP do not create
  a resume window.
- The automatic worker checks pause before work, between titles, and before every title alias.
- Coroutine cancellation is explicitly rethrown; only automatic `WEB_LINK_WORK` is cancelled.

## Deviations

## Review findings

Initial review requested config-safe ownership and finer-grained cancellation. Both were fixed.
Final standards and specification reviews: PASS.

## Completion evidence

`InteractiveMediaPauseGateTest` and affected Kotlin compilation: BUILD SUCCESSFUL.
