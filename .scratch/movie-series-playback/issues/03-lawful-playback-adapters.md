# TICKET-03: Lawful MOVIE/SERIES playback adapters

Status: PENDING

## Objective

Connect the maximum practical set of lawful, directly playable sources supported by primary
documentation and normalize them into the existing player/downloader candidates.

## User or system value

MOVIE/SERIES can play and, where explicitly permitted, download from user-owned or public sources.

## Dependencies

TICKET-02 and `research/safe-playback-sources.md`.

## Scope

Final adapter list is selected from the research report. Direct user URLs and personal media
servers are preferred. Each adapter must have mocked contract tests and explicit downloadability.

## Out of scope

Unlicensed embed/CDN extraction, DRM bypass and sources without a primary permitted media contract.

## Acceptance criteria

- [ ] At least one configured adapter resolves a SERIES episode to a playable candidate.
- [ ] The same candidate reaches playback and download only when allowed.
- [ ] Secrets/configuration remain local and redacted.
- [ ] One provider failure does not cancel other applicable adapters.

## Verification plan

Mocked adapter tests, cascade tests, player/download candidate tests and debug assembly.

## TDD classification

REQUIRED

## Expected architecture impact

Adapters behind the capability-aware playback seam; no provider-specific branches in UI.

## Risks

Personal servers require user configuration; official public APIs may expose embeds rather than
direct downloadable media.

## Implementation notes

Empty before implementation.

## Deviations

Empty before implementation.

## Review findings

Empty before review.

## Completion evidence

Empty before completion.
