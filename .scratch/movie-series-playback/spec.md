# MOVIE/SERIES catalog and lawful playback providers

## Problem

The app labels movie/series catalog results as TMDB-only because the implemented Kinopoisk adapter
targets `api.kinopoisk.dev`, while the user has a valid key for `kinopoiskapiunofficial.tech`.
The episode UI also invokes anime-only streaming adapters for SERIES, producing misleading
"not found" and "No playable streams found" errors.

## Desired outcome

Russian MOVIE/SERIES search uses both TMDB and the user's documented Kinopoisk API. Playback and
download use an explicit capability-aware provider cascade that only runs adapters applicable to
the media type and never claims an anime-only source supports a live-action series.

## Current behavior

- RU catalog orchestration expects Kinopoisk, but its adapter, DTOs and endpoint belong to a
  different provider than the supplied key.
- SERIES season discovery is rejected before any request.
- The generic episode UI remains visible and calls AniLibria/AnimeGo/Kodik/AnimeHeaven paths;
  Kodik itself requests only `anime,anime-serial`.
- Direct HLS/DASH/MP4 URLs are supported only as a fallback from stored web links.

## Required behavior

1. Use `kinopoiskapiunofficial.tech` with `X-API-KEY` and its documented search/details/seasons
   contracts.
2. Preserve the existing TMDB+Kinopoisk merge, ids and failure semantics.
3. Make provider applicability explicit for ANIME/MOVIE/SERIES and language.
4. Never run anime-only adapters for MOVIE/SERIES.
5. Keep player and downloader consuming the same normalized `VetroVideo` candidates.
6. Add lawful adapters only: user-owned media servers, user-supplied direct media URLs, or
   first-party public streams whose terms permit playback. No DRM/access-control bypass, hidden
   stream extraction, scraped pirate embeds, or third-party credential harvesting.

## User-visible behavior

- RU movie/series search visibly includes Kinopoisk results when the local key is configured.
- SERIES with no configured applicable playback adapter receives an accurate configuration/
  unsupported message, not an anime-source "title not found" message.
- Configured lawful providers participate in normal quality selection, playback and download when
  their returned media is downloadable.

## Domain rules

- Catalog provider and playback provider are different roles. Kinopoisk metadata does not imply a
  playable episode stream.
- A playback candidate declares whether it is streamable and downloadable; downloadability is not
  inferred from URL shape alone when the provider forbids it.
- Secrets stay local and are never committed or logged.

## Functional requirements

- Kinopoisk search maps movie/series types, RU/EN titles, year, rating, poster and ids.
- Kinopoisk details and season counts map without changing the released-only `Anime.episodes`
  invariant.
- Provider routing is testable without network access.
- HTTP mappings have MockEngine regression tests.
- Personal-provider configuration is disabled by default and fails without leaking tokens.

## Non-functional requirements

- No secret values in Git, logs, exceptions or test fixtures.
- One bounded timeout per adapter; one failed adapter does not cancel the cascade.
- Existing ANIME playback behavior remains unchanged.
- Existing player/download recovery and ranking remain the single downstream implementation.

## Compatibility and migration constraints

- Keep `ExternalIds.kinopoisk` and stored ids unchanged.
- Reuse the existing `KINOPOISK_API_KEY` local configuration name.
- Old rows require no database migration for the catalog-provider switch.

## Failure and fallback behavior

- Missing/invalid Kinopoisk key is observable and TMDB remains available; partial success is not
  described as multi-provider success.
- Unsupported media type skips an adapter without a network request.
- Network/auth failures remain distinct from a legitimate no-match response.
- A playback cascade with no applicable configured adapters reports `NotConfigured`; configured
  adapters that return no candidate report `NoMatch`; failures report bounded diagnostics.

## Out of scope

- Collaps, HDVB, Videoseed, VidSrc, Rezka, Filmix, KinoPub, Lampac modules or equivalent
  unlicensed embed/CDN extraction.
- Cloudflare/JS/access-control bypass or DRM decryption.
- Downloading media when the provider does not explicitly permit it.
- Pretending Kinopoisk trailers/external-source links are full episode streams.

## Acceptance criteria

- A MockEngine test proves the exact Kinopoisk host, header and search/details/seasons mapping.
- Live smoke with the supplied local key finds `Doctor House` as `TV_SERIES` without exposing the
  key.
- RU repository tests contain both TMDB and Kinopoisk candidates; EN behavior remains explicit.
- MOVIE/SERIES never call anime-only playback or season-discovery adapters.
- At least one lawful direct/personal-media adapter works end-to-end through player and downloader,
  or the feature remains explicitly `NEEDS_USER_CONFIGURATION` with the missing configuration
  documented.
- Relevant unit tests and debug assembly pass.

## Open questions

- DEFERRED: which personal server the user owns (Jellyfin/Emby/Plex). Adapters may be implemented
  as disabled configuration options, but no server-specific ticket is marked complete without a
  mocked contract test.
- DEFERRED: official VK/RUTUBE playback feasibility; include only if primary documentation exposes
  a permitted playable URL contract.

## Test seams

- Kinopoisk HTTP adapter through Ktor MockEngine.
- Pure playback-provider applicability/routing policy.
- Provider adapter with mocked HTTP response returning normalized `VetroHoster`/`VetroVideo`.
- Existing player/download tests for candidate propagation.
