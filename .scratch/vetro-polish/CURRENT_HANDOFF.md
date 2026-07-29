# Current handoff

## Original goal

Семь пунктов правок пользователя от 2026-07-29 — хвосты после прогонов `vetro-todo` и
`vetro-player`. Дословный исходник: [`spec-source.md`](./spec-source.md).

## Canonical artifacts

- [`MASTER_PLAN.md`](./MASTER_PLAN.md) — состояние, решения, риски.
- [`spec.md`](./spec.md) — FR-1…FR-7, AC-1…AC-11.
- [`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md).
- [`EXECUTION_LOG.md`](./EXECUTION_LOG.md), тикеты в `issues/`.

## Current workflow state

READY_FOR_IMPLEMENTATION. Активного тикета нет.

## Completed tickets

- TICKET-01 (п. 1, меню качества) — DONE, `70f83cf`.
- TICKET-02 (п. 6, жест разворота) — DONE_WITH_DEVIATIONS, `63dd9df`.
- TICKET-03 (п. 2, автопереход серий) — DONE_WITH_DEVIATIONS, `2152f98`.

## Active ticket

Нет.

## Next eligible ticket

**TICKET-04** — прогресс манги по границе прочитанного (`issues/04-manga-progress-frontier.md`).
TDD REQUIRED. Единственный тикет пакета, который необратимо пишет данные пользователя.

## Decisions that must be preserved

D-1…D-7 в `MASTER_PLAN.md`. Ключевые для оставшейся работы:

- D-4: отметки глав проставляются задним числом, в том числе для уже читаемых тайтлов при открытии
  меню глав. Необратимо, пользователь подтвердил.
- D-6: «вправо» = LTR; цикл вертикальный → LTR → RTL.
- D-7: автоопределение режима ридера убрать, вертикальный — дефолт.

## Deviations that affect later work

- TICKET-02 заменил `onCycleFit` на `onSetFit: (VideoFit) -> Unit` в `PlayerControlsOverlay` — любой
  новый хост плеера обязан передавать его.
- TICKET-03: в локальном плеере автопереход реализован гашением штатного перехода ExoPlayer
  (`pauseAtEndOfMediaItems`), а не собственным переключением.

## Current repository state

Ветка `vetro-todo`. В дереве незастейдженными лежат 27 удалённых файлов под `.claude/skills/` —
они были там до начала работы, **трогать нельзя**.

## Relevant commits

`70f83cf`, `63dd9df`, `2152f98` (+ докоммиты плана).

## Verification already performed

- `.\gradlew.bat :app:compileDebugKotlin` — зелёный после каждого тикета.
- `.\gradlew.bat :app:testDebugUnitTest` — 138 тестов, 1 падение: `StatsRatingBucketTest`
  (существовавший дефект, `vetro-player` TICKET-05, не регрессия).
- `assembleDebug` ещё не прогонялся — он нужен перед финальным ревью.

## Known failures or blockers

Блокеров нет. Единственное падение теста — известный дефект вне объёма.

## Files most relevant to the next ticket

- `manga/domain/MangaReadingSummary.kt` — правило счёта, которое меняется.
- `app/src/test/.../manga/domain/MangaReadingSummaryTest.kt` — 10 существующих тестов.
- `manga/ui/MangaChaptersPage.kt` (1094 строки) — открытие вкладки, порядок и группировка глав.
- `manga/data/MangaReadingStore.kt` — снимок прогресса в DataStore, пакетная запись.
- `ui/home/HomeViewModel.kt` — реактивный шов `mangaReading` для карточки.

## Exact recommended next action

Начать TICKET-04 с тестов: дописать в `MangaReadingSummaryTest` случаи «прочитана только 16-я из
92», «прочитана последняя», «дырки в отметках», «чужие ключи» — и только потом менять
`summarizeMangaReading`. Порядок глав брать тот же, по которому строится список во вкладке.
