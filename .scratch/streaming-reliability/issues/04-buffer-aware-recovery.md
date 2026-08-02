# TICKET-04: Buffer-aware watchdog и смена источника

## Status

DONE_WITH_DEVIATIONS

## Objective

Прерывать бесконечный `BUFFERING` и безопасно менять независимый URL/host с сохранением позиции.

## Acceptance criteria

- [x] 8 секунд непрерывного BUFFERING дают одно recovery action.
- [x] Retry следует buffer bands из spec.
- [x] 403/404/410/429/503 классифицируются согласно spec.
- [x] Fallback предпочитает другой host без повышения качества.
- [x] Позиция сохраняется, failed URL не выбирается снова в текущей сессии.

## Implementation notes

`StreamRecoveryPolicy` содержит чистые buffer/HTTP decisions, fallback ranking и monotonic watchdog.
Activity выполняет решение через player-keyed single-flight gate, переносит позицию, хранит bad URLs
до смены серии и использует generation-owned cancellable resolve job.

## Deviations

Retry-After не ожидается при малом буфере: coordinator сразу выбирает fallback, как требует Level 1
spec. Persistent host-health и Level 2/3 state machine не добавлялись.

## Review findings

Initial review found watchdog relatching, watchdog/error double action, stale resolve ownership and
stale READY. All were fixed. Final TICKET-04 standards/spec reviews: no findings.

## Completion evidence

Targeted `media.player.*` tests: PASS. Full `testDebugUnitTest`: PASS. `compileDebugKotlin`: PASS.
`assembleDebug`: PASS. Review: `reviews/04-buffer-aware-recovery.md`.
