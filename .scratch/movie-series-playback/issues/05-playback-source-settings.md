# TICKET-05: Playback source settings UI

Status: DONE

## Objective

Let the user configure, validate and remove WebDAV/Jellyfin/Emby connections from Settings.

## Dependencies

TICKET-03, TICKET-04.

## Scope

- Settings entry and source sheet.
- URL/user/token/app-password fields with masked secrets.
- Test connection and encrypted save/remove actions.
- Clear configured/unconfigured status.

## Acceptance criteria

- [x] Connections can be added without rebuilding the APK.
- [x] Secrets are masked and stored only in encrypted preferences.
- [x] Invalid configuration produces a clear non-secret error.

## Verification plan

ViewModel/state tests, app tests and debug assembly.

## TDD classification

REQUIRED

## Implementation notes

- Added a Settings sheet for WebDAV/Nextcloud, Jellyfin and Emby with configured status,
  connection test, encrypted save and removal.
- Kept decrypted credentials inside the media-source service; UI state contains only a public
  draft and a temporary masked replacement value.
- Bound stored-secret reuse to the normalized server/root/user scope. Changing that scope requires
  a new password or token and produces an explicit non-secret `SECRET_REQUIRED` message.
- Protected connection probes against stale coroutine results after edit, close, save or removal.

## Deviations

None.

## Review findings

Spec and Standards re-reviews found no remaining BLOCKING or IMPORTANT findings.

## Completion evidence

- `./gradlew --offline :app:testDebugUnitTest --tests "*PlaybackSourcesSettingsViewModelTest" --tests "*KtorPlaybackSourceConnectionTesterTest" :app:compileDebugKotlin`
- `./gradlew --offline :core:network:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
- Both commands completed successfully on 2026-08-10.
