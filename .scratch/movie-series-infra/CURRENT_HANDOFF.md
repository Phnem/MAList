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
(`3b88d85`).

## Active ticket

None.

## Next eligible ticket

TICKET-05 — Details-экран для MOVIE/SERIES. TICKET-06 также разблокирован, но выбран 05.

## Decisions that must be preserved

- `Anime.episodes` для SERIES означает только released episodes.
- `MovieSeriesRepository` остаётся в `core/network` и не импортирует app/DB модели.
- RU source priority: Kinopoisk fill-gap + TMDB canonical; EN: TMDB only.
- `Failure` не очищает сохранённый id; только `NotFoundById` запускает stale-id repair.

## Deviations that affect later work

HTTP MockEngine тесты data source отложены. Cloud sync новых id ждёт ручного применения
Supabase migration. `ApiSearchResult.originalTitle` добавлен для корректного дедупа.

## Current repository state

TICKET-04 завершён и должен быть чистым после коммита; следующий тикет может менять app слой.

## Relevant commits

`e73c65a`, `b1ea00d`, `140d048`, `3b88d85`.

## Verification already performed

Полные unit-тесты `core:network` + `app`: 383/383; компиляция обоих модулей; boundary grep;
inline-TMDB grep; `git diff --check`.

## Known failures or blockers

Нет.

## Files most relevant to the next ticket

`ApiService.kt`, `VetroApiService.kt`, `AnimeRepository.kt`, `DetailsViewModel.kt`,
`MovieSeriesRepository.kt`, `.scratch/movie-series-infra/issues/05-details-screen-wiring.md`.

## Exact recommended next action

Прочитать TICKET-05 и существующие `fetchDetails` вызовы, добавить TDD/контрактные тесты на
MOVIE/SERIES details wiring и отсутствие записи known episodes.
