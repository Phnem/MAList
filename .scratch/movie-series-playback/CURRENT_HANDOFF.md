# Current handoff

## Original goal

Enable the supplied Kinopoisk catalog key and connect every lawful directly playable MOVIE/SERIES
source practical for the existing player and downloader.

## Canonical artifacts

- `.scratch/movie-series-playback/spec.md`
- `.scratch/movie-series-playback/MASTER_PLAN.md`
- `.scratch/movie-series-playback/architecture/INITIAL_REVIEW.md`

## Current workflow state

BETWEEN_TICKETS

## Completed tickets

TICKET-01 — switched the catalog adapter to `kinopoiskapiunofficial.tech`, with typed mappings,
MockEngine regression tests and shared HTTP outcome semantics.

TICKET-02 — capability-aware playback routing, explicit outcomes and typed player recovery.

TICKET-03 — Direct HTTPS/WebDAV adapters, explicit download capability and encrypted credentials.

TICKET-04 — Jellyfin/Emby library search, PlaybackInfo direct/HLS resolution and scoped secrets.

## Active ticket

None.

## Next eligible ticket

TICKET-05 is ready.

## Decisions that must be preserved

- Supplied key belongs to `kinopoiskapiunofficial.tech`.
- Catalog metadata/trailers are not full playback streams.
- Only lawful direct/personal/public media adapters may be added.
- `VetroVideo` remains the player/downloader candidate.

## Deviations that affect later work

None.

## Current repository state

TICKET-04 changes are ready for their focused commit.

## Relevant commits

Feature base: `ab230b4`; latest completed commit before this ticket: `20c328c`.

## Verification already performed

Authenticated Kinopoisk smoke, focused catalog tests, full earlier app suite, and focused
Jellyfin/Emby/WebDAV/security tests pass. App Kotlin compilation passes. Spec and Standards
reviews for TICKET-04 are accepted.

## Known failures or blockers

No blocker. Playback-source research is complete.

## Files most relevant to the next ticket

`SettingsScreen.kt`, `SettingsViewModel.kt`, `PlaybackSourceCredentialsStore.kt`, and the personal
source adapters.

## Exact recommended next action

Commit TICKET-04, activate TICKET-05, then add a failing settings-state test before UI wiring.
