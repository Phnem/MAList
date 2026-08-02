# TICKET-04: Prevent Supabase credentials from reaching Logcat

## Status

COMPLETED

## Objective

Sanitize Supabase failure summaries and stop logging raw throwable representations.

## User or system value

Diagnostic logs can be shared without exposing the current cloud session token.

## Dependencies

TICKET-03.

## Scope

Central safe summary helper, Supabase error call sites, JVM redaction tests.

## Out of scope

Rotating already exposed user sessions.

## Acceptance criteria

- [x] Bearer values, JWTs and `apikey` query/header values are redacted.
- [x] Supabase repository/auth/passphrase paths do not attach raw throwables to Android Log.
- [x] User-visible failure text is sanitized where it originates from the same exception.

## Verification plan

Redaction unit tests, repository grep, full unit tests and debug assembly.

## TDD classification

REQUIRED

## Expected architecture impact

One shared safe-error formatting seam for all Supabase adapters.

## Risks

Over-redaction can reduce diagnostics; exception class and safe status/message remain.

## Implementation notes

- The Supabase SDK logger is disabled because its Realtime error path can include an API key URL.
- A bounded, single-line summary redacts Bearer/JWT and header/query/JSON credential assignments.
- Supabase adapters no longer print or attach raw throwables; user-visible failures use safe copies.

## Deviations

## Review findings

Initial review found unbracketed Bearer and quoted JSON bypasses. Regex order/forms and regression
tests were corrected. Final security and specification reviews: PASS.

## Completion evidence

`SafeSyncLogTest`, repository grep, and affected Kotlin compilation: BUILD SUCCESSFUL.
