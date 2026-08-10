# TICKET-05: Playback source settings UI

Status: PENDING

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

- [ ] Connections can be added without rebuilding the APK.
- [ ] Secrets are masked and stored only in encrypted preferences.
- [ ] Invalid configuration produces a clear non-secret error.

## Verification plan

ViewModel/state tests, app tests and debug assembly.

## TDD classification

REQUIRED
