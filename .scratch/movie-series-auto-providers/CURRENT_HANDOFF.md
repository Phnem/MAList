# Current handoff

## Original goal

Построить для MOVIE/SERIES provider-инфраструктуру уровня ANIME-каскада плюс пользовательские
источники, подключаемые конфигурацией без пересборки.

## Canonical artifacts

- `.scratch/movie-series-auto-providers/spec.md`
- `.scratch/movie-series-auto-providers/MASTER_PLAN.md`
- `.scratch/movie-series-auto-providers/research/MOVIE_SERIES_PROVIDER_RESEARCH.md`
- `.scratch/movie-series-auto-providers/architecture/INITIAL_REVIEW.md`
- `.scratch/movie-series-auto-providers/reviews/final-review.md`

## Current workflow state

COMPLETED для согласованного объёма.

## Completed tickets

01 `fcc2735`, 02 `beb039c`, 03 `3b4f966`, 04 `7f23901`, 05 `fe4e474`, 06 `1108938`, 07 `ea5b1b7`,
08 `49a5834`, 09 `edf2884`+`4630ea1`, 10 `5a9c074`, 12 `3da1459`, 13 `c5134ba`.
TICKET-11 — DEFERRED по решению пользователя.

## Next eligible ticket

None.

## Decisions that must be preserved

- Пиратские адаптеры из раздела 12 задания не реализуются.
- Локальный plugin-рантайм с исполнением стороннего кода не строится; расширяемость закрыта
  декларативным манифестом и Stremio-транспортом (логика — на сервере аддона).
- Из Stremio принимаются только http(s) `url`; torrent/usenet/архивы/ytId/externalUrl — нет.
- `ProviderResolution.NotFound` не считается сбоем и не портит health.
- `reliabilityRank()` в `VideoRanking` не трогать без отдельного пересмотра ANIME-ранжирования.
- Download остаётся default-deny.

## Current repository state

Ветка `main`, HEAD `c5134ba`, рабочее дерево чисто (кроме незакоммиченных артефактов `.scratch/`).

## Verification already performed

591 app unit tests, 60 core/network, 0 failures, 0 errors (форсированный прогон); debug APK
собирается; `git diff --check` чист.

## Known failures or blockers

Нет.

## Files most relevant to future work

`app/src/main/java/com/example/myapplication/media/source/movieseries/` — весь новый слой.

## Exact recommended next action

Реальный smoke: подключить свой источник через Настройки → Источники видео → Добавить источник и
проверить воспроизведение фильма и эпизода. При желании — TICKET-11 (Internet Archive/Wikimedia).
