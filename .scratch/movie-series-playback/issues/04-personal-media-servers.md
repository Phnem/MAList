# TICKET-04: Jellyfin and Emby playback adapters

Status: DONE

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

- [x] Jellyfin SERIES episode resolves from a configured personal library.
- [x] Emby SERIES episode resolves from a configured personal library.
- [x] Provider failure does not hide a working sibling.
- [x] Tokens are never logged or serialized into URLs.

## Verification plan

Focused personal-server tests and app compilation.

## TDD classification

REQUIRED

## Implementation notes

Added encrypted provider-scoped Jellyfin/Emby configuration, exact provider-id/library matching,
episode lookup and typed PlaybackInfo resolution. Direct stream is preferred when the server
allows it; HLS/transcode is the fallback. Required headers reach Media3, while offline download is
enabled only for progressive media when both local policy and server `CanDownload` allow it.

Credential headers are stripped from persisted work, rehydrated through a provider/root-bound
reference, never follow redirects and are removed from every child request outside the canonical
configured root.

## Deviations

Playback session progress/stop reporting is deferred; it is not required to resolve or play a
candidate and can be added behind the same personal-server seam.

## Review findings

Reviews found unsafe title fallback on conflicting ids, missing PlaybackInfo, ignored capability
flags, background required-header loss, redirect/root credential leakage and encoded URL bypasses.
All were fixed. Final Spec and Standards reviews report no remaining BLOCKING/IMPORTANT findings.

## Completion evidence

MockEngine tests cover Jellyfin/Emby SERIES, direct/HLS selection, identity conflict/ambiguity,
download policy, required headers, failure isolation, background rehydration and encoded
credential-scope bypasses. Focused tests and app Kotlin compilation pass.
