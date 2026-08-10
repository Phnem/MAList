# Final architecture checkpoint

Date: 2026-08-10
Fixed point: `ab230b4`
Final code: `a5e6500`

## Verdict

PASS. No remaining BLOCKING or IMPORTANT architecture finding.

## Stable boundaries

- `core/network` hides Kinopoisk/TMDB wire contracts and catalog orchestration.
- MOVIE/SERIES playback enters one typed request/outcome and bounded source cascade; anime-only
  adapters are excluded by route.
- Direct, WebDAV, Jellyfin and Emby terminate in the shared `VetroVideo` player/downloader model.
- Credential ownership remains below UI state. Origin/root/user-bound references control player
  and worker rehydration; redirects and adaptive child requests cannot cross the auth scope.
- Offline download is default-deny. Only user-controlled configured sources may opt in, and
  Jellyfin/Emby additionally require server `CanDownload` on progressive media.

## Deferred architecture risks

- Move `DownloadQuality` out of the UI package and retire the lossy legacy `resolveHosters` seam.
- Migrate legacy `StreamingSeasonDiscovery` to the shared playback cascade.
- Rename `PlaybackRoute.DirectOnly`, which now means the broader controlled-source route.

These items do not block the delivered feature and do not weaken its security invariants.
