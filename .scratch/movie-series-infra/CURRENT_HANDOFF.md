# Current handoff

## Original goal

Довести MOVIE/SERIES инфраструктуру до типизированного TMDB+Kinopoisk поиска, сохранённых id,
рабочих details/repair и released-episode tracking без смешивания known/released эпизодов.

## Canonical artifacts

- `.scratch/movie-series-infra/spec.md`
- `.scratch/movie-series-infra/MASTER_PLAN.md`
- `C:/Users/2004i/.claude/plans/majestic-meandering-quiche.md`

## Current workflow state

COMPLETE

## Completed tickets

TICKET-01 (`e73c65a`), TICKET-02 (`b1ea00d`), TICKET-03 (`140d048`), TICKET-04
(`3b88d85`), TICKET-05 (`e81c5bb`), TICKET-06 (`4810e38`), TICKET-07 (`46bc4f1`),
TICKET-08 (`1777d36`).

## Active / next eligible ticket

Нет. Все обязательные тикеты завершены, финальные Spec и Architecture/Standards checkpoints
пройдены без нерешённых BLOCKING/IMPORTANT.

## Decisions that must be preserved

- `Anime.episodes` для SERIES означает только released episodes.
- `MovieSeriesRepository` остаётся в `core/network` и не импортирует app/DB модели.
- RU source priority: Kinopoisk fill-gap + TMDB canonical; EN: TMDB primary.
- `Failure` не очищает сохранённый id; только `NotFoundById` запускает stale-id repair.
- Provider id gaps не подавляются generic `EnrichmentGapJournal`.
- Новая SERIES получает normalization marker атомарно при вставке; legacy/import rows — нет.
- Home и Worker запускают anime→SERIES проверки через один `EpisodeUpdateCheckCoordinator`.

## Verification performed

- `:core:network:testDebugUnitTest` + `:app:testDebugUnitTest`: 435/435
  (51 core + 384 app), failures=0, errors=0.
- `:app:assembleDebug`: успешно.
- `git diff --check`: успешно.
- Migration 13/14, production SERIES insertion marker, legacy normalization, stale-id recovery,
  Failure semantics и shared update-feed покрыты тестами.

## Deferred / manual work

- Добавить Ktor `MockEngine` тесты HTTP mapping для TMDB/Kinopoisk.
- Вручную применить
  `supabase/migrations/20260809000000_anime_tmdb_kinopoisk_ids.sql`, затем добавить новые id и
  not-found timestamps в cloud sync DTO/push/pull. До этого provider ids остаются локальными.
- При доступе к устройству и live API выполнить smoke: RU/EN add/details и locale switch,
  remake dedup, stale-id recovery, migration 13 на копии реальной БД, offline repair и ongoing
  SERIES notification/auto-apply.
- Out of scope остаются recommendations, where-to-watch, AI title translation, SERIES player,
  season UI, status cadence, Kinopoisk proxy и TMDB animation filtering.

## Known failures or blockers

Нет. Ручные/live проверки выше не выполнялись, но не являются блокером code acceptance.

## Current repository state

Код всех тикетов закоммичен. После финального docs-коммита рабочее дерево должно быть чистым.
