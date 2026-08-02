# TICKET-03: Отмена неуспевающего adaptive-чанка

## Status

DONE_WITH_DEVIATIONS

## Objective

Для настоящих multi-variant manifests отменять только доказуемо неуспевающую загрузку с cooldown.

## Acceptance criteria

- [x] Отмена невозможна без lower track или при safe buffer >10 секунд.
- [x] No-progress/forecast trigger работает.
- [x] Один сегмент не отменяется повторно 15 секунд.
- [x] Current+higher tracks исключаются на 25 секунд.

## Implementation notes

Custom `AdaptiveTrackSelection` читает live transfer progress, проверяет доступный lower track и
исключает current/higher tracks перед отменой. Partial retries сопоставляются по точному bounded
resource descriptor, если Media3 заменил исходный `DataSpec`.

## Deviations

Для partial retry добавлен безопасный descriptor fallback; identity/load id остаются основными путями.

## Review findings

Исправлены позднее определение Content-Length, сравнение bitrate при одинаковой высоте, excluded
lower tracks, partial-retry correlation и stale waiting source. Финальные ревью: замечаний нет.

## Completion evidence

`gradlew :app:testDebugUnitTest --tests "com.example.myapplication.media.player.*"`: PASS.
Media3 1.4.1 compile: PASS. Review: `reviews/03-adaptive-stall-cancellation.md`.
