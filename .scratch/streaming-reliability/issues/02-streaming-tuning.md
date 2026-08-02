# TICKET-02: Консервативный streaming buffer и ABR

## Status

DONE_WITH_DEVIATIONS

## Objective

Настроить только remote player на 60/90/2/6 и adaptive 25/10/25, 0×0, 0.60.

## User or system value

Даёт запас перед просадкой и предотвращает агрессивный рост/выбрасывание полезного буфера.

## Dependencies

TICKET-01.

## Scope

Единая session factory, allocator wiring, LoadControl, DefaultTrackSelector, конфигурационные тесты.

## Out of scope

Media3 upgrade и local player.

## Acceptance criteria

- [ ] Streaming config соответствует spec.
- [ ] Реальный MediaSource использует тот же instrumented DataSource factory.
- [ ] Local player не изменён.

## Verification plan

Unit tests, compile, diff audit localplayer.

## TDD classification

RECOMMENDED

## Expected architecture impact

Заменяет две несвязанные фабрики одним глубоким streaming-session seam.

## Risks

Повторная установка MediaSource или изменение lifecycle player.

## Implementation notes

- Создан `StreamingPlaybackSessionFactory`: общий instrumented DataSource, source, player,
  allocator, diagnostics и track selector.
- Activity получает готовую session и больше не строит второй неинструментированный MediaSource.

## Deviations

- Старый module/file переименован по требованию review, чтобы interface соответствовал роли.
- Media3 1.4.1 generic setters используются только в remote session factory.

## Review findings

- Spec review без findings; standards finding о misleading module name исправлен.

## Completion evidence

- 12 targeted player tests PASS; compile PASS.
- Local player files этим тикетом не менялись.
- Commit не создан из-за пересекающегося dirty tree.
