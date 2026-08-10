# TICKET-02: Capability-aware playback routing

Status: PENDING

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

- [ ] MOVIE/SERIES route contains no AniLibria/AnimeGo/AnimeHeaven/jut.su adapter.
- [ ] Kodik remains anime-only unless a separately authorized lawful contract exists.
- [ ] ANIME routing is unchanged.
- [ ] UI distinguishes not configured, no match and provider failure.

## Verification plan

Targeted source-routing and ViewModel tests plus `:app:compileDebugKotlin`.

## TDD classification

REQUIRED

## Expected architecture impact

New internal seam with at least the existing direct URL adapter and later personal-server adapter.

## Risks

Avoid a large rewrite of stable anime adapter implementations.

## Implementation notes

Empty before implementation.

## Deviations

Empty before implementation.

## Review findings

Empty before review.

## Completion evidence

Empty before completion.
