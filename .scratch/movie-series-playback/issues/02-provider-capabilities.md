# TICKET-02: Capability-aware playback routing

Status: DONE

## Objective

Stop invoking anime-only sources for MOVIE/SERIES and represent configured/applicable/no-match/
failure outcomes explicitly.

## User or system value

The player stops claiming that anime sites searched for a live-action series and reports the real
reason a stream is unavailable.

## Dependencies

TICKET-01.

## Scope

- Introduce a small playback request containing media type, ids, season, episode and language.
- Add provider capabilities and a pure routing policy.
- Preserve current ANIME behavior.
- Update season discovery and episode resolution messaging for unsupported/unconfigured SERIES.

## Out of scope

Adding a new remote playback provider.

## Acceptance criteria

- [x] MOVIE/SERIES route contains no AniLibria/AnimeGo/AnimeHeaven/jut.su adapter.
- [x] Kodik remains anime-only unless a separately authorized lawful contract exists.
- [x] ANIME routing is unchanged.
- [x] UI distinguishes not configured, no match and provider failure.

## Verification plan

Targeted source-routing and ViewModel tests plus `:app:compileDebugKotlin`.

## TDD classification

REQUIRED

## Expected architecture impact

New internal seam with at least the existing direct URL adapter and later personal-server adapter.

## Risks

Avoid a large rewrite of stable anime adapter implementations.

## Implementation notes

Added `PlaybackRequest`, `PlaybackIdentity`, explicit resolution outcomes and a sealed route that
drives `SourceEngine`. Player recovery now preserves media type and TMDB/Kinopoisk ids. Provider
attempts run through a cancellation-safe supervised cascade.

## Deviations

None.

## Review findings

Initial reviews found stale player identity, a non-authoritative provider list and insufficient
cascade coverage. All were resolved; final Spec review has no BLOCKING/IMPORTANT findings.

## Completion evidence

Full `:app:testDebugUnitTest` passes, including routing, mixed failure/success cascade, recovery
identity and user-message regressions. `:app:compileDebugKotlin` passes. Final Spec and Standards
reviews have no remaining BLOCKING/IMPORTANT findings.
