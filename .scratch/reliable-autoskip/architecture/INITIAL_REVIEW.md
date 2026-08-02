# Initial architecture review

## Existing seams

- `VetroHoster`/`VetroVideo` уже являются сериализуемой границей SourceEngine → playback.
- `SourceEngine` уже собирает все RU-источники параллельно и подходит для episode-wide enrichment.
- `SkipSegmentProvider` является подходящим местом по смыслу, но его interface слишком беден для exact/reference context, а mutable adapter нарушает locality.
- Оба плеера используют один controls overlay, но дублируют resolution/active/seek state.

## Required before implementation

- Добавить backward-compatible reference model.
- Выделить чистый parser и injected transport seam для детерминированных JVM-тестов.

## Required during implementation

- Заменить provider cluster одним `SkipSegmentResolver` interface: один request → один resolution с origin metadata.
- Сосредоточить duration validation, clipping и priority в resolver.
- Сосредоточить media reset, active-segment и seek deduplication в общем coordinator.
- Сохранить сеть в adapters (`JutSuSource`, AniSkip transport), а Compose — только как наблюдатель состояния плеера.

## Stable modules

- `PlayerControlsOverlay`, media source factory, progress persistence, title matcher algorithm.
- Источники AniLiberty/Kodik/AnimeGo/AnimeHeaven кроме автоматического enrichment в SourceEngine.

## Risks to watch

- reference-only jut.su hoster не должен остановить direct fallback и не должен попасть в playable list.
- `intro_start = 0` нельзя спутать с отсутствующим значением.
- Старое значение `stored` не должно примениться к новой серии.
- Существующие незакоммиченные loading/season-source изменения пересекаются с редактируемыми файлами и должны сохраниться.

## Follow-up only

- Перенос всех playback progress flows в отдельный presenter.
- Instrumentation-тест реального Android PiP; unit coordinator проверяет независимость механизма от PiP, но не системное окно.
