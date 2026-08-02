# Надёжное потоковое воспроизведение — Master Plan

## Workflow

Current workflow state: READY_FOR_FIELD_VERIFICATION
Current ticket: none
Last completed ticket: TICKET-04
Next eligible ticket: none
Last updated: 2026-07-31

## Goal

Устранить 10–20-минутные зависания на медленных зеркалах через наблюдаемость, conservative ABR,
stalled-chunk cancellation и buffer-aware смену источника с сохранением позиции.

## Canonical specification

[`spec.md`](./spec.md). Исходный пользовательский план:
`C:/Users/2004i/Downloads/AyuGram Desktop/vetro_streaming_reliability_plan-2.md`.

## Architecture review

[`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md).

## Global constraints

- Рабочее дерево уже грязное; не откатывать и не коммитить чужие изменения.
- `StreamingPlaybackSession.kt`, `StreamingPlaybackDiagnostics.kt` и player Activity уже содержат
  незакоммиченную связанную работу — сохранять её intent и достраивать поверх.
- Не менять local-player buffer/lifecycle.
- Media3 остаётся 1.4.1; новые streaming-only setters заменяются эквивалентными старыми только в
  remote factory.
- Один активный implementation ticket за раз.

## Non-goals

Level 2/3, dependency upgrade, persistent host scoring, UI redesign.

## Verification commands

### Fast checks

`.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.media.player.*"`

### Ticket checks

`.\gradlew.bat :app:compileDebugKotlin`

### Full checks

`.\gradlew.bat :app:testDebugUnitTest`

`.\gradlew.bat :app:assembleDebug`

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Коррелируемая телеметрия загрузок | DONE_WITH_DEVIATIONS | — | not committed¹ | pass |
| TICKET-02 | Консервативный streaming buffer и ABR | DONE_WITH_DEVIATIONS | 01 | not committed¹ | pass |
| TICKET-03 | Отмена неуспевающего adaptive-чанка | DONE_WITH_DEVIATIONS | 01, 02 | not committed¹ | pass |
| TICKET-04 | Buffer-aware watchdog и смена источника | DONE_WITH_DEVIATIONS | 01, 02, 03 | not committed¹ | pass |

## Ticket details

### TICKET-01 — Коррелируемая телеметрия загрузок

Status: DONE_WITH_DEVIATIONS  
Tracker reference: `issues/01-request-telemetry.md`  
Dependencies: none

Implementation summary: bounded transfer telemetry, identity correlation, safe chunk/allocator logs.  
Deviations: explicit unit names; runtime wiring moves with the session factory in TICKET-02.  
Verification evidence: 10 targeted tests and compile pass; two-axis review pass.  
Commit: not created¹.

### TICKET-02 — Консервативный streaming buffer и ABR

Status: DONE_WITH_DEVIATIONS  
Tracker reference: `issues/02-streaming-tuning.md`  
Dependencies: TICKET-01

Implementation summary: unified instrumented remote session, exact buffer and conservative ABR.  
Deviations: module renamed to reflect its deeper interface; Media3 remains 1.4.1.  
Verification evidence: 12 targeted tests/compile pass, two-axis review pass.  
Commit: not created¹.

### TICKET-03 — Отмена неуспевающего adaptive-чанка

Status: DONE_WITH_DEVIATIONS  
Tracker reference: `issues/03-adaptive-stall-cancellation.md`  
Dependencies: TICKET-01, TICKET-02

Implementation summary: bounded stalled-chunk cancellation with lower-track proof,
forecast/no-progress trigger, cooldown, and track exclusion.  
Verification evidence: targeted player tests and two-axis review pass.  
Commit: not created¹.

### TICKET-04 — Buffer-aware watchdog и смена источника

Status: DONE_WITH_DEVIATIONS  
Tracker reference: `issues/04-buffer-aware-recovery.md`  
Dependencies: TICKET-01, TICKET-02, TICKET-03

Implementation summary: buffer/HTTP recovery policy, ranked fallback, one-shot 8-second watchdog,
position transfer, session bad URLs, single-flight recovery and generation-owned fresh resolve.  
Deviations: persistent host health and Level 2/3 remain out of scope.  
Verification evidence: targeted/full unit tests, compile and assemble pass; two-axis review pass.  
Commit: not created¹.

## Decisions

- D-1: выполнять Level 1; Level 2/3 только после полевых метрик.
- D-2: не обновлять Media3 в этом пакете.
- D-3: adaptive track и независимый `VetroVideo` — разные recovery paths.
- D-4: 8 секунд непрерывного buffering — верхняя граница автоматического ожидания до recovery.

## Global deviations

- Streaming-only setters исходного плана отсутствуют в Media3 1.4.1. Используются старые setters
  внутри фабрики, которой пользуется только remote player; наблюдаемое поведение эквивалентно.
- ¹ Не создаются ticket commits поверх грязного пересекающегося дерева: это смешало бы ранее
  существовавшую пользовательскую работу с текущим тикетом.

## Known risks

- Нельзя подтвердить CDN-поведение без устройства.
- Текущее дерево содержит пересекающиеся пользовательские изменения.
- Полный debug unit suite сейчас проходит; ранее известное падение `StatsRatingBucketTest` устранено
  пересекающейся пользовательской работой.

## Deferred work

- Media3 upgrade + DASH #3326 fixture.
- Level 2/3 после сравнения телеметрии.

## Final acceptance checklist

- [x] Every required ticket completed
- [x] Full test suite or agreed equivalent run
- [x] Specification reviewed requirement by requirement
- [x] No unresolved blocking review findings
- [x] Migration and compatibility behavior verified
- [ ] User-visible behavior verified on a real device/CDN (NOT_VERIFIED)
- [x] Deferred work explicitly recorded
- [x] Final architecture checkpoint completed
