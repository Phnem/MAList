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
(`3b88d85`), TICKET-05 (`e81c5bb`), TICKET-06 (`4810e38`), TICKET-07 (`46bc4f1`).

## Active ticket

None.

## Next eligible ticket

TICKET-08 — SeriesEpisodeCheckUseCase.

## Decisions that must be preserved

- `Anime.episodes` для SERIES означает только released episodes.
- `MovieSeriesRepository` остаётся в `core/network` и не импортирует app/DB модели.
- RU source priority: Kinopoisk fill-gap + TMDB canonical; EN: TMDB primary.
- `Failure` не очищает сохранённый id; только `NotFoundById` запускает stale-id repair.
- Provider id gaps не подавляются generic `EnrichmentGapJournal`.

## Deviations that affect later work

HTTP MockEngine тесты data source отложены. Cloud sync новых id ждёт ручного применения
Supabase migration. Полный repair валидирует все сохранённые movie ids. Ручная проверка на
реальной коллекции не выполнялась, debug APK и 426 unit tests зелёные.

## Current repository state

Код TICKET-07 закоммичен; после docs-коммита рабочее дерево должно быть чистым.

## Relevant commits

`e73c65a`, `b1ea00d`, `140d048`, `3b88d85`, `e81c5bb`, `4810e38`, `46bc4f1`.

## Verification already performed

Полные unit-тесты `core:network` + `app`: 426/426; `app:assembleDebug`; `git diff --check`.

## Known failures or blockers

Нет.

## Files most relevant to the next ticket

`updates/BatchEpisodeCheckUseCase.kt`, `domain/enrichment/FullEnrichmentWorker.kt`,
`worker/AnimeUpdateWorker.kt`, `core/network/.../movie/MovieSeriesRepository.kt`,
`.scratch/movie-series-infra/issues/08-series-episode-check.md`.

## Exact recommended next action

Прочитать TICKET-08 и существующий anime update-feed; сначала написать тесты на legacy
normalization (12 known → 7 released без update, затем 7 → 8 с update) и Failure/NotFoundById.
