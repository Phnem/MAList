# TICKET-04: Jellyfin and Emby playback adapters

Status: PENDING

## Objective

Resolve MOVIE/SERIES from user-owned Jellyfin and Emby libraries into the existing player.

## Dependencies

TICKET-03.

## Scope

- Typed REST contracts for user/library lookup and episode resolution.
- Direct-stream/HLS candidates with required auth headers.
- Permission-aware download candidates.
- MockEngine request/response and cascade tests.

## Acceptance criteria

- [ ] Jellyfin SERIES episode resolves from a configured personal library.
- [ ] Emby SERIES episode resolves from a configured personal library.
- [ ] Provider failure does not hide a working sibling.
- [ ] Tokens are never logged or serialized into URLs.

## Verification plan

Focused personal-server tests and app compilation.

## TDD classification

REQUIRED
