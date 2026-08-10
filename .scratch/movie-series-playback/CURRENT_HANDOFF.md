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

## Active ticket

None; TICKET-03 is ready.

## Next eligible ticket

TICKET-03 — lawful playback adapters.

## Decisions that must be preserved

- Supplied key belongs to `kinopoiskapiunofficial.tech`.
- Catalog metadata/trailers are not full playback streams.
- Only lawful direct/personal/public media adapters may be added.
- `VetroVideo` remains the player/downloader candidate.

## Deviations that affect later work

None.

## Current repository state

TICKET-02 changes are ready for their focused commit.

## Relevant commits

Base: `ab230b4`.

## Verification already performed

Authenticated live search/details/seasons/videos/external-sources smoke against the intended
Kinopoisk host; key was not printed. Focused Kinopoisk/TMDB/repository tests, full core-network
unit tests and app Kotlin compilation pass. Spec and Standards reviews are accepted.

## Known failures or blockers

No blocker. Playback-source research is complete.

## Files most relevant to the next ticket

`PlaybackResolution.kt`, `PlaybackProviderCascade.kt`, `SourceEngine.kt`, and the source research.

## Exact recommended next action

Commit TICKET-02, split TICKET-03 by adapter family, then start Direct HTTP/WebDAV with a failing
contract test.
