# TICKET-03: Increase stream resilience and add privacy-safe diagnostics

## Status

COMPLETED

## Objective

Use an explicit streaming buffer and capture enough telemetry to distinguish CDN throughput from
local contention.

## User or system value

Fewer short rebuffer cycles and actionable logs when a host is too slow.

## Dependencies

TICKET-02.

## Scope

Streaming load control, rate-limited AnalyticsListener, safe host/state formatting and tests.

## Out of scope

Automatic CDN switching.

## Acceptance criteria

- [x] Streaming uses a time-prioritized larger buffer with a 10-second rebuffer reserve.
- [x] State logs include position and buffered duration.
- [x] Rate-limited bandwidth logs include bitrate, bytes, resolution, and host only.
- [x] Load failure and audio underrun logs omit URLs/tokens.

## Verification plan

Formatter/rate-limiter JVM tests and app compilation.

## TDD classification

RECOMMENDED

## Expected architecture impact

Telemetry is isolated from the Activity and source model behind an AnalyticsListener factory.

## Risks

Over-buffering memory use and noisy logs.

## Implementation notes

- Streaming buffer is explicitly `60s/120s`, time-prioritized, with `10s` required after rebuffer.
- Analytics tracks the sanitized actual manifest/media resource host and rate-limits bandwidth logs.
- Pure formatters accept only safe structured fields; no full URL or exception message channel.

## Deviations

## Review findings

Reviews requested pure formatter coverage and actual resource-CDN attribution. Both were added.
Final standards/specification reviews: PASS.

## Completion evidence

`StreamingPlaybackDiagnosticsTest` and affected Kotlin compilation: BUILD SUCCESSFUL.
