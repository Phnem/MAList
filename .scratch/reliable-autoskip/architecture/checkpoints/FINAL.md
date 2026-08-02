# Final architecture checkpoint

Date: 2026-07-31

## Result

PASS. Реализация сохранила и углубила запланированные boundaries:

- `JutSuEpisodePageParser` и title-search являются чистыми deterministic modules; сеть и cookies
  остаются в `JutSuSource`.
- `VetroSkipReference` переносит episode metadata без знания player UI.
- `SkipSegmentResolver` является единственным владельцем priority, duration compatibility,
  validation и clipping.
- `AniSkipSegmentProvider` скрывает mapping, transport, strict decoding, in-flight coalescing и
  process cache.
- `MediaSkipCoordinator` скрывает state transitions и остаётся pure/JVM-testable; общий Compose
  adapter только связывает его с ExoPlayer lifecycle.

## Required corrections made during review

- Duration refinement теперь пересчитывает resolution, не сбрасывая dedup для той же media.
- Cancellation и stale async results не могут переустановить старую media.
- Собственный ExoPlayer discontinuity не переактивирует automatic seek.
- Local playlist transition сбрасывает provisional duration/position.
- Diagnostic lifecycle хранит snapshot отдельно для каждого media key.

## Stable modules preserved

Нет persistent schema migration и пропорционального timestamp scaling. Existing serialized
payloads совместимы благодаря nullable/default полям. Existing exact timestamps остаются
приоритетными.

## Remaining risk

Реальное поведение Activity/PiP и сетевой jut.su playback невозможно доказать JVM-тестами.
Подключение не зависит от controls/PiP branch и собирается, но ручная device-проверка остаётся
`NOT_VERIFIED`.

## Follow-up classification

OPTIONAL: instrumentation/device regression для PiP, смены озвучки и resume. Архитектурного
prefactoring или обязательного follow-up ticket не требуется.
