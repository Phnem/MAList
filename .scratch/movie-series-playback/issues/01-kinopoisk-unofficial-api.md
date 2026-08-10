# TICKET-01: Switch Kinopoisk catalog adapter to the supplied API

Status: DONE

## Objective

Make RU MOVIE/SERIES search and metadata use `kinopoiskapiunofficial.tech` while preserving the
existing `MovieSeriesRepository` interface and ids.

## User or system value

The user's valid key starts producing Kinopoisk results instead of a silent TMDB-only fallback.

## Dependencies

None.

## Scope

- Replace endpoint and DTO mapping for search/details.
- Add season DTO/mapping needed by the series catalog path without writing known counts into
  released episode storage.
- Add Ktor MockEngine tests for host/header/query/status/mapping.
- Fail clearly on a blank key without emitting it.

## Out of scope

Playback streams; the `/videos` endpoint contains trailers/teasers, not episodes.

## Acceptance criteria

- [x] Search `Doctor House` maps a SERIES result with Kinopoisk id.
- [x] Details map titles, year, rating, poster, genres and IMDB bridge.
- [x] Seasons map to a separate catalog structure and do not overwrite released episodes.
- [x] Blank/401/429/5xx outcomes retain correct failure semantics.
- [x] Repository RU merge tests pass; EN policy remains documented.

## Verification plan

`./gradlew :core:network:testDebugUnitTest --tests "*Kinopoisk*" --tests "*MovieSeriesRepository*"`

## TDD classification

REQUIRED

## Expected architecture impact

Implementation replacement behind the existing deep catalog module.

## Risks

The supplied API has a 500-request daily quota for new accounts and 20 req/s general limit.

## Implementation notes

Replaced the incompatible `api.kinopoisk.dev` contract with typed requests for
`kinopoiskapiunofficial.tech`. The supplied key is read from ignored `local.properties`; a blank
key fails before any request. Shared HTTP outcome mapping now serves both Kinopoisk and TMDB.

## Deviations

None.

## Review findings

Spec and Standards reviews found no remaining BLOCKING/IMPORTANT issues after explicit title/year,
search-vs-ID 404 and shared HTTP-policy fixes.

## Completion evidence

Focused Kinopoisk/TMDB/repository tests pass. Authenticated live smoke found Doctor House and its
eight seasons without printing the configured key. `:core:network:testDebugUnitTest` and
`:app:compileDebugKotlin` pass.
