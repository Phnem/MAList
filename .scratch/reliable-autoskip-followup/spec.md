# Autoskip and playback reliability follow-up

## Problem

The first reliable-autoskip implementation correctly rejected timestamps that did not satisfy its
strict duration rule, but a real Food Wars S3E6 playback therefore had no usable segments. jut.su
has exact metadata for that episode, yet its current JavaScript-backed search page is not readable
by the static title-result parser. Automatic web-link enrichment also continues issuing many
metadata requests while a player or manga reader is open. Playback logs do not expose enough
buffer and throughput data to distinguish a slow media host from local contention. Supabase
exceptions can include authorization headers in Logcat.

## Desired outcome

Opening autoskip works for the captured Food Wars S3E6 case without proportional timestamp
scaling; available native sources are discovered without selecting a wrong franchise season;
automatic web-link enrichment is suspended while any player or reader Activity exists and resumes
after the last one closes; streaming has a larger time-prioritized buffer and useful privacy-safe
diagnostics; authentication material is never printed through Supabase exception logging.

## Current behavior

- `/lookfor/<title>` may redirect to a JavaScript-only search page with no static candidates.
- A cross-source reference must be within both 1% and 15 seconds of the current duration.
- Later seasons without an exact `SeasonInfo.title` are not sent to AniLibria.
- `WebLinkEnrichmentWorker` runs whenever the network is connected, including during playback.
- streaming uses Media3 1.4.1 default load control and does not log state/buffer/bandwidth together.
- several Supabase error paths pass the original throwable to Android Log.

## Required behavior

1. jut.su discovery must have a non-JavaScript fallback and still require a title score of at least
   0.91.
2. Cross-source jut.su opening references may be used when the duration difference is no more than
   both 2% and 30 seconds. Exact timestamps remain highest priority and no timestamps are scaled.
   Ending and recap cross-source references retain the stricter 1% and 15-second rule.
3. A later season may be queried from AniLibria only through season-identifying input and must be
   rejected if the returned release cannot be validated as that selected season.
4. Entering StreamPlayerActivity, LocalPlayerActivity, or MangaReaderActivity acquires a
   process-local pause token for automatic web-link enrichment. The first token cancels current
   unique web-link work. New work and continuations are not scheduled while paused. Closing the
   last token schedules enrichment again.
5. PiP and configuration changes must not accidentally resume enrichment while a player still
   exists. Tokens therefore follow Activity creation/destruction, not foreground visibility.
6. Streaming uses an explicit time-prioritized load-control policy with a larger rebuffer reserve.
7. Diagnostics log media host (never full URL), selected resolution, playback state, position,
   buffered duration, estimated bandwidth, load failures, and audio underruns. High-frequency
   estimates are rate-limited.
8. Supabase-related error logging must sanitize messages and must not attach throwables whose
   string representation may contain headers or tokens.

## User-visible behavior

- Autoskip remains off by default.
- Food Wars S3E6 can use jut.su opening `75–165s` for the captured 1470.123-second Kodik rendition.
- AniLibria and jut.su appear only when they actually expose the selected season/episode.
- Player and reader network activity gets priority over automatic link enrichment.
- Enrichment resumes automatically after the last player/reader closes.

## Domain rules

- Opening cross-source compatibility: `difference <= currentDuration * 0.02` and
  `difference <= 30_000ms`.
- Ending/recap cross-source compatibility: `difference <= currentDuration * 0.01` and
  `difference <= 15_000ms`.
- No proportional timestamp scaling.
- Missing or unvalidated sources remain absent rather than falling back to a different season.
- A pause token is idempotently closeable; only the transition 0→1 cancels work and 1→0 resumes it.

## Non-functional requirements

- Pure discovery, compatibility, pause-counter, diagnostic-formatting, and log-redaction behavior
  is covered by JVM tests.
- Existing dirty user changes are preserved.
- Logs must not contain URL query strings, request headers, bearer values, JWTs, or API keys.

## Compatibility and migration constraints

- No persisted schema migration.
- Existing exact source timestamps retain their priority and behavior.
- Existing full/manual enrichment is not cancelled; this feature pauses the automatic web-link
  worker that caused the captured contention.

## Failure and fallback behavior

- If jut.su fallback discovery fails, resolution proceeds with other sources and AniSkip.
- If AniLibria season validation is inconclusive, AniLibria is omitted.
- If playback bandwidth remains inadequate after background work is paused, the diagnostics must
  identify the media host and measured estimate so a CDN-specific decision can be made.
- If a pause token is closed twice, the pause count is unchanged after the first close.

## Out of scope

- Proportional timecode scaling or audio/video fingerprinting.
- Guaranteeing that a third-party source carries every title.
- Pausing user-requested downloads or manually started full enrichment.
- Automatically switching a manually selected studio while it is still playable.

## Acceptance criteria

- Captured Food Wars durations accept jut.su opening but do not relax ending compatibility.
- Static tests cover JS-search fallback selection and score rejection.
- AniLibria later-season lookup cannot silently return season one.
- Three media Activities hold/release the shared enrichment pause.
- Web-link worker exits or refrains from continuing while paused and resumes afterward.
- Streaming player uses the explicit buffer policy and emits privacy-safe telemetry.
- Supabase error helper redacts representative bearer/JWT/API-key inputs.
- `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

## Open questions

None blocking. Exact AniLibria season-search fields are confirmed in the linked research note before
implementation.

## Test seams

- `JutSuTitleSearch`
- `SkipSegmentResolver`
- AniLibria release candidate selection/parser
- process-local enrichment pause counter
- playback diagnostic formatter/rate limiter
- Supabase safe error summary
