# MOVIE/SERIES catalog and lawful playback — Master Plan

## Workflow

Current workflow state: FINAL_REVIEW
Current ticket: None
Last completed ticket: TICKET-05
Next eligible ticket: None
Last updated: 2026-08-10

## Goal

Restore Kinopoisk catalog results with the user's actual API provider and replace misleading
anime-only SERIES playback attempts with a capability-aware cascade of lawful playable sources.

## Canonical specification

[`spec.md`](./spec.md)

## Architecture review

[`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md)

## Global constraints

- Secrets are local-only, never committed or logged.
- Preserve released-only SERIES episode semantics.
- Do not add unlicensed embed/CDN extraction or access-control bypass.
- Player and downloader keep consuming normalized `VetroVideo` candidates.
- One active ticket at a time.

## Non-goals

See specification Out of scope.

## Verification commands

### Fast checks

`./gradlew :core:network:compileDebugKotlin :app:compileDebugKotlin`

### Ticket checks

`./gradlew :core:network:testDebugUnitTest --tests "*Kinopoisk*" --tests "*MovieSeriesRepository*"`

`./gradlew :app:testDebugUnitTest --tests "*Source*" --tests "*Playback*"`

### Full checks

`./gradlew :core:network:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Switch Kinopoisk catalog adapter | DONE | — | 94c2505 | accepted |
| TICKET-02 | Capability-aware playback routing | DONE | 01 | a78499d | accepted |
| TICKET-03 | Direct HTTP and WebDAV adapters | DONE | 02, research | 20c328c | accepted |
| TICKET-04 | Jellyfin and Emby adapters | DONE | 03 | 3e64adb | accepted |
| TICKET-05 | Playback source settings UI | DONE | 03, 04 | pending | accepted |

## Ticket details

### TICKET-01 — Switch Kinopoisk catalog adapter

Status: DONE
Tracker reference: [`issues/01-kinopoisk-unofficial-api.md`](./issues/01-kinopoisk-unofficial-api.md)
Dependencies: —

### TICKET-02 — Capability-aware playback routing

Status: DONE
Tracker reference: [`issues/02-provider-capabilities.md`](./issues/02-provider-capabilities.md)
Dependencies: 01

### TICKET-03 — Direct HTTP and WebDAV adapters

Status: DONE
Tracker reference: [`issues/03-lawful-playback-adapters.md`](./issues/03-lawful-playback-adapters.md)
Dependencies: 02, research

### TICKET-04 — Jellyfin and Emby adapters

Status: DONE
Tracker reference: [`issues/04-personal-media-servers.md`](./issues/04-personal-media-servers.md)
Dependencies: 03

### TICKET-05 — Playback source settings UI

Status: DONE
Tracker reference: [`issues/05-playback-source-settings.md`](./issues/05-playback-source-settings.md)
Dependencies: 03, 04

## Decisions

- The supplied key belongs to `kinopoiskapiunofficial.tech`; migrate the adapter rather than
  asking the user for a second provider key.
- Kinopoisk videos/external sources are metadata/trailers, not episode playback candidates.
- Personal/direct/public permitted media is the playback scope.

## Global deviations

- Unlicensed public embed/CDN resolvers from the initial discussion were not implemented. The
  delivered cascade is limited to sources the user controls or is explicitly authorized to use.
- Real-device connection smokes require user-owned WebDAV/Jellyfin/Emby endpoints and remain a
  manual verification item; adapter contracts, routing and security boundaries are automated.

## Known risks

- Kinopoisk quota: 500 requests/day for new accounts.
- Without personal-source configuration, commercial SERIES may still have no playable stream.
- The current generic episode UI exposes functionality before a capable SERIES provider exists.

## Deferred work

- Provider health persistence and long-term ranking.
- Plex and additional personal media servers.
- Curated public-domain Internet Archive/Wikimedia Commons adapters.

## Final acceptance checklist

- [x] Every required ticket completed
- [x] Full test suite or agreed equivalent run
- [ ] Specification reviewed requirement by requirement
- [x] No unresolved blocking review findings
- [x] User-visible behavior verified by ViewModel/UI-state tests and debug assembly
- [x] Deferred work explicitly recorded
- [ ] Final architecture checkpoint completed
