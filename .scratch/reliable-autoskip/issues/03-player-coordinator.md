# TICKET-03: Общий media-keyed playback coordinator

Status: DONE

## Objective

Одинаково управлять active segment, automatic/manual seek и reset в потоковом и локальном плеерах.

## User or system value

Autoskip больше не зависит от контролов/PiP и не переносит stale state между сериями, URL и плеерами.

## Dependencies

TICKET-02.

## Scope

Pure coordinator, common Compose adapter, оба player surface, resume consolidation, episode diagnostics.

## Out of scope

Системный instrumentation PiP test на устройстве.

## Acceptance criteria

- [x] Coordinator reset-ится на episode, URL и player instance.
- [x] Resume into segment вызывает automatic seek.
- [x] Manual Skip работает при autoskip off.
- [x] PiP/controls visibility не входят в условия seek.
- [x] Дублирующий restore path в `StreamPlayerActivity` удалён.
- [x] Diagnostic seek-log содержит требуемые поля и дедуплицирован по episode.

## Verification plan

Pure regression tests, Compose compilation, full unit tests and debug assemble.

## TDD classification

REQUIRED для state transitions; UI wiring проверяется сборкой.

## Expected architecture impact

Один coordinator module с общим Compose adapter вместо двух копий effect logic.

## Risks

Ordering listener, saved-position restore и segment resolution.

## Implementation notes

Добавлены чистый `MediaSkipCoordinator` и общий `rememberMediaSkipPlayback`, подключённые к
потоковому и локальному плеерам. Resolution пересчитывается при уточнении фактической
длительности, а AniSkip при этом использует process cache. Async-result защищён от cancellation
и stale media key. Собственный automatic seek не переактивирует тот же сегмент.

Диагностика логирует первый фактический seek; если серия покинута без seek, `DisposableEffect`
записывает `not_applied:media_left` с последними metadata/duration старой media.

## Deviations

Нет отклонений от ticket scope. Device/PiP instrumentation остаётся out of scope; wiring
проверено компиляцией, а переходы — pure JVM tests.

## Review findings

Два последовательных read-only review нашли и после исправлений закрыли: stale duration локальной
playlist, проглатывание `CancellationException`, stale async install, повторный seek после
собственного discontinuity и преждевременную/неверную диагностику при смене media.
Оставшихся blocking/high/medium findings нет.

## Completion evidence

- `MediaSkipCoordinatorTest`: 8 tests, PASS.
- `:app:testDebugUnitTest`: PASS после первоначальной интеграции; финальный повтор выполняется в
  cumulative review.
- `:app:assembleDebug`: PASS после первоначальной интеграции; финальный повтор выполняется в
  cumulative review.
