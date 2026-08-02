# TICKET-04 review — buffer-aware source recovery

## Standards

PASS. Player-keyed single-flight recovery prevents double actions; generation-owned resolve jobs are
cancelled on session changes; stale player callbacks cannot reset the active session. No URL-bearing
exceptions are logged.

## Specification

PASS. One-shot 8-second watchdog, buffer-aware single retry, required HTTP classification, ranked
fallback order, position transfer, and session bad-URL exclusion are present.

## Evidence

Targeted tests and compilation pass. Final standards/spec review reports no unresolved findings.
