# Initial architecture review

## Existing helpful seams

- `MovieSeriesRepository` already hides TMDB/Kinopoisk ordering, merge and failure policy behind a
  small catalog interface.
- `MediaGateway` is the shared player/downloader seam and `VetroVideo` is the normalized candidate.
- `SourceEngine.safeResolve` already provides bounded, failure-isolated adapter execution.

## Obstructions

- `SourceEngine` owns concrete anime adapters and language branches, so applicability is implicit.
- `StreamingSeasonDiscovery` duplicates a second hard-coded source list and rejects SERIES.
- Kinopoisk HTTP behavior lacks a MockEngine seam, which allowed an incompatible provider/key pair
  to compile and ship silently.

## Required during implementation

1. Replace the Kinopoisk adapter implementation behind the existing repository seam; do not leak
   the new DTOs into app/UI.
2. Introduce one capability-aware playback-provider interface used by both episode resolution and
   season discovery where supported. Keep adapter-specific search/resolve details behind it.
3. Preserve `MediaGateway` and `VetroVideo` as the downstream player/download interface.

## Follow-up only

- Type-enforce secrets instead of BuildConfig strings.
- Persist provider health/latency ranking across sessions.
- Move all legacy anime adapters to the new interface after MOVIE/SERIES behavior is proven.

## Review watchpoints

- No catalog metadata API presented as a video source.
- No anime-only request for MOVIE/SERIES.
- No token in logs, URLs, test snapshots or committed files.
- Download permission represented explicitly.
