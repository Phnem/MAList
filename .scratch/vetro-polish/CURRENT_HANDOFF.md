# Current handoff

## Original goal

Семь пунктов правок пользователя от 2026-07-29 — хвосты после прогонов `vetro-todo` и
`vetro-player`. Дословный исходник: [`spec-source.md`](./spec-source.md).

## Canonical artifacts

- [`MASTER_PLAN.md`](./MASTER_PLAN.md) — состояние, решения, риски.
- [`spec.md`](./spec.md) — FR-1…FR-7, AC-1…AC-11.
- [`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md).
- [`reviews/final-review.md`](./reviews/final-review.md) — аудит требование за требованием.
- [`EXECUTION_LOG.md`](./EXECUTION_LOG.md), тикеты в `issues/`.

## Current workflow state

**COMPLETED по коду.** Визуальная и поведенческая приёмка за пользователем — пройденной не
считается.

## Completed tickets

| Тикет | Пункт | Коммит | Итог |
|---|---|---|---|
| TICKET-01 | 1 меню качества | `70f83cf` | DONE |
| TICKET-02 | 6 жест разворота | `63dd9df` | DONE_WITH_DEVIATIONS |
| TICKET-03 | 2 автопереход серий | `2152f98` | DONE_WITH_DEVIATIONS |
| TICKET-04 | 3 прогресс манги | `074be39` | DONE |
| TICKET-05 | 4 анимация томов | `b13168e` | DONE |
| TICKET-06 | 5 оформление меню глав | `d711da8` | DONE |
| TICKET-07 | 7 порядок раскладок | `76c0b66` | DONE |

## Active ticket

Нет.

## Next eligible ticket

В этом пакете — нет. Незакрытыми остаются хвосты прошлых прогонов: `vetro-todo` TICKET-10
(вычистка существующих дубликатов), TICKET-11 (фоновое обновление оглавлений манги),
`vetro-player` TICKET-04 (переделка загрузки, ждёт описания), TICKET-05 (падающий
`StatsRatingBucketTest`).

## Decisions that must be preserved

D-1…D-7 в `MASTER_PLAN.md`. Два из них отменяют более ранние решения:

- **D-5 отменяет D-2 прогона `vetro-player`**: свободного зума в плеере больше нет, у кадра два
  положения. Возвращать свободный зум без нового решения пользователя нельзя.
- **D-2 отменяет решение `vetro-todo` TICKET-03**: фон меню качества `#1C1C1E` α0.96, а не `#333333`.
- **D-4**: дописывание отметок глав необратимо; снятия скопом нет и не планируется.
- **D-7**: автоопределение режима ридера удалено намеренно, а не потеряно.

## Deviations that affect later work

- `PlayerControlsOverlay` принимает `pinchState: PlayerPinchState` и `onSetFit: (VideoFit) -> Unit`
  вместо `zoomState` и `onCycleFit` — любой новый хост плеера обязан их передавать.
- В локальном плеере автопереход реализован гашением штатного перехода ExoPlayer
  (`pauseAtEndOfMediaItems`), а не собственным переключением.
- Прогресс манги считается по **номеру** главы: главы без номера в отрезок не входят.
- Градиент экранов живёт только в `IosDesign.screenGradient` — новые экраны обязаны брать его
  оттуда, а не копировать `colorStops`.

## Current repository state

Ветка `vetro-todo`. В дереве незастейдженными лежат 27 удалённых файлов под `.claude/skills/` —
они были там до начала работы, **трогать нельзя**.

## Relevant commits

`70f83cf`, `63dd9df`, `2152f98`, `074be39`, `b13168e`, `d711da8`, `76c0b66` + докоммиты плана.

## Verification already performed

- `.\gradlew.bat :app:compileDebugKotlin` — зелёный после каждого тикета.
- `.\gradlew.bat :app:testDebugUnitTest` — 156 тестов (было 133 до пакета), 1 падение:
  `StatsRatingBucketTest.buckets_continuous_noGaps` — существовавший дефект, не регрессия.
- `.\gradlew.bat :app:assembleDebug` — `BUILD SUCCESSFUL in 46s`.

## Known failures or blockers

Блокеров нет. Единственное падение теста — известный дефект вне объёма (`vetro-player` TICKET-05).

## Files most relevant to the next ticket

Зависит от того, какой хвост будет взят следующим. Для `vetro-todo` TICKET-11 (фоновое обновление
оглавлений) отправная точка — `manga/data/MangaChapterCacheStore.kt` и
`domain/BatchEpisodeCheckUseCase`, который сегодня фильтрует `mediaType == ANIME`.

## Exact recommended next action

Дать пользователю посмотреть сборку и собрать замечания по семи пунктам. Из них семь визуальных и
жестовых проверок я выполнить не могу — они перечислены в `reviews/final-review.md` как
`NOT_VERIFIED`. После его ответа — либо итерация по конкретным пунктам, либо переход к хвостам
прошлых прогонов.
