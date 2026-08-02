# TICKET-01: Коррелируемая телеметрия загрузок

## Status

DONE_WITH_DEVIATIONS

## Objective

Достроить начатую telemetry до per-request метрик и progress snapshot без секретов.

## User or system value

Позволяет доказать причину stall и даёт вход watchdog/adaptive cancellation.

## Dependencies

Нет.

## Scope

Transfer accumulator/listener, load start/end/cancel logs, allocator/loading logs, unit tests.

## Out of scope

Автоматическое recovery и изменение выбора качества.

## Acceptance criteria

- [ ] Поля из FR-1/2 доступны или явно помечены unknown.
- [ ] Rolling 1s/3s, TTFB, no-progress детерминированно тестируются.
- [ ] URL/query/header/error text не логируются.
- [ ] История ограничена.

## Verification plan

Targeted diagnostics tests и `:app:compileDebugKotlin`.

## TDD classification

REQUIRED

## Expected architecture impact

Углубляет diagnostics module; создаёт внутренний seam progress snapshot.

## Risks

Конкурентные transfers и неверная корреляция одинаковых URI.

## Implementation notes

- Добавлены чистый progress accumulator, identity-based load correlator и bounded transfer monitor.
- Analytics логирует start/completed/canceled/error chunk events и allocator state.
- Единицы throughput/bitrate названы явно, signed URL остаются только в in-memory identity.

## Deviations

- Имена rolling-полей уточнены до `BytesPerSecond`, declared bitrate — до
  `BitsPerSecond`, чтобы исключить ошибку ×8. Семантика исходного плана сохранена.
- Runtime wiring monitor/allocator остаётся в TICKET-02, где создаётся единый session seam.

## Review findings

- Два первичных blocking findings исправлены; финальные Standards и Spec reviews без blockers.

## Completion evidence

- `:app:testDebugUnitTest --tests "...StreamingPlaybackDiagnosticsTest"`: PASS, 10 tests.
- `:app:compileDebugKotlin`: PASS как часть targeted run.
- Review: `.scratch/streaming-reliability/reviews/01-request-telemetry.md`.
- Commit отсутствует: рабочее дерево до задачи содержало пересекающиеся незакоммиченные изменения.
