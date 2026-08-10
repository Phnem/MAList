# TICKET-03: Direct HTTP and WebDAV playback adapters

Status: DONE

## Objective

Turn direct HTTPS/S3 and WebDAV/Nextcloud media into explicit, permission-aware playback sources.

## User or system value

MOVIE/SERIES can play and, where explicitly permitted, download from user-owned or public sources.

## Dependencies

TICKET-02 and `research/safe-playback-sources.md`.

## Scope

- Add a shared playback-source capability contract.
- Keep direct HTTPS/S3 URLs as the first source.
- Add WebDAV PROPFIND discovery and authenticated GET candidates.
- Add encrypted local configuration storage.
- Mark download permission explicitly on every candidate.

## Out of scope

Unlicensed embed/CDN extraction, DRM bypass and sources without a primary permitted media contract.

## Acceptance criteria

- [x] Direct HTTPS/S3 and WebDAV resolve SERIES episodes to playable candidates.
- [x] The same candidate reaches playback and download only when explicitly allowed.
- [x] Secrets/configuration remain local and redacted.
- [x] One provider failure does not cancel other applicable adapters.

## Verification plan

Mocked adapter tests, cascade tests, player/download candidate tests and debug assembly.

## TDD classification

REQUIRED

## Expected architecture impact

Adapters behind the capability-aware playback seam; no provider-specific branches in UI.

## Risks

WebDAV hierarchy/search depth differs across servers; first version uses a configured root.

## Implementation notes

Added a common typed source contract and wired Direct HTTPS plus authenticated WebDAV into the
bounded cascade. WebDAV credentials are encrypted, origin/root-bound and rehydrated only for a
matching background candidate. Download capability is default-deny and enforced before enqueue.
Authenticated WebDAV is progressive-only in v1 to prevent credentials reaching manifest children.

## Deviations

Adaptive WebDAV manifests are intentionally deferred until player/downloader headers can be bound
per child origin.

## Review findings

Initial reviews found default-allow download, origin/ref leakage, pre-cascade config probes,
persisted secret values and adaptive-child credential leakage. All were fixed. Final Spec and
Standards reviews report no remaining BLOCKING/IMPORTANT findings.

## Completion evidence

WebDAV request/mapping/origin/adaptive-bypass tests, direct capability tests, download permission
and persistence/rehydration tests, and a failing-source/successful-sibling production cascade test
pass. Full app unit suite and app compilation pass.
