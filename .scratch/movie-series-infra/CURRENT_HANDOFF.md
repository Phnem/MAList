# Current handoff

## Original goal

Довести MOVIE/SERIES инфраструктуру до типизированного TMDB+Kinopoisk поиска, сохранённых id,
рабочих details/repair и released-episode tracking без смешивания known/released эпизодов.

## Canonical artifacts

- `.scratch/movie-series-infra/spec.md`
- `.scratch/movie-series-infra/MASTER_PLAN.md`
- `C:/Users/2004i/.claude/plans/majestic-meandering-quiche.md`

## Current workflow state

READY_FOR_IMPLEMENTATION

## Completed tickets

TICKET-01 (`e73c65a`), TICKET-02 (`b1ea00d`), TICKET-03 (`140d048`), TICKET-04
(`3b88d85`), TICKET-05 (`e81c5bb`).

## Active ticket

None.

## Next eligible ticket

TICKET-06 — AddFromApiUseCase и дедуп по ExternalIds.

## Decisions that must be preserved

- `Anime.episodes` для SERIES означает только released episodes.
- `MovieSeriesRepository` остаётся в `core/network` и не импортирует app/DB модели.
- RU source priority: Kinopoisk fill-gap + TMDB canonical; EN: TMDB only.
- `Failure` не очищает сохранённый id; только `NotFoundById` запускает stale-id repair.

## Deviations that affect later work

HTTP MockEngine тесты data source отложены. Cloud sync новых id ждёт ручного применения
Supabase migration. `ApiSearchResult.originalTitle` добавлен для корректного дедупа.

## Current repository state

TICKET-05 завершён и должен быть чистым после коммита.

## Relevant commits

`e73c65a`, `b1ea00d`, `140d048`, `3b88d85`, `e81c5bb`.

## Verification already performed

Полные unit-тесты `core:network` + `app`: 401/401; компиляция обоих модулей; `git diff --check`.

## Known failures or blockers

Нет.

## Files most relevant to the next ticket

`AddFromApiUseCase.kt`, `DuplicateTitleRule.kt`, `SaveAnimeParams.kt`, `SaveAnimeUseCase.kt`,
`ApiSearchResult.kt`, `.scratch/movie-series-infra/issues/06-add-and-dedup.md`.

## Exact recommended next action

Прочитать TICKET-06 и существующие source-based ветки добавления/поиска дублей; сначала
добавить тесты на сохранение tmdb/kinopoisk id и разные годы ремейков.
