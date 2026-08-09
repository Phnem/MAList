# Инфраструктура поиска/обогащения для фильмов и сериалов — Master Plan

## Workflow

Current workflow state: WAITING_FOR_USER_DECISION
Current ticket: None
Last completed ticket: TICKET-01 (implemented + verified, NOT committed — see decision below)
Next eligible ticket: TICKET-02, TICKET-03 (оба разблокированы, независимы друг от друга)
Last updated: 2026-08-09

**Ожидает решения пользователя**: рабочее дерево содержит существенный объём несвязанных
незакоммиченных правок (dock navigation, PiP, card menu, workspace UI), включая ДВА файла,
которые TICKET-01 тоже правит: `HomeScreen.kt` (285 изменённых строк всего, из них TICKET-01 —
только 3 точечных hunk'а) и `UiStrings.kt` (TICKET-01 — только добавление `typeMovie`, удаление
`notifAccept`/`notifDecline`-полей — не моё). Коммит по правилам ticket-autopilot ("один
фокусный коммит на тикет") для этих двух файлов неизбежно захватит и несвязанные правки —
безопасно разделить средствами, доступными в этой сессии, нельзя (нет `git add -p`). Спросить
пользователя, как поступить, прежде чем коммитить и продолжать TICKET-02.

## Goal

Довести инфраструктуру фильмов/сериалов (сейчас — один инлайн TMDB-вызов в `VetroApiService`)
до архитектурного паритета с аниме: типизированные многоисточниковые сетевые слои (TMDB +
Kinopoisk), сохранённые внешние id, рабочий Details-refresh, детектор пробелов/починка полей,
отслеживание вышедших серий. Рекомендации/web-links/AI-перевод/локальный плеер/season-UI — вне
скоупа, зафиксированы как бэклог.

## Canonical specification

- Спека: [`spec.md`](./spec.md)
- Архитектурный план (первоисточник, три раунда ревью): `C:\Users\2004i\.claude\plans\majestic-meandering-quiche.md`
- Архитектурный обзор фазы 5: [`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md)
- Журнал: [`EXECUTION_LOG.md`](./EXECUTION_LOG.md)
- Handoff: создаётся после первого завершённого тикета — [`CURRENT_HANDOFF.md`](./CURRENT_HANDOFF.md)

Трекер — локальный markdown (`docs/agents/issue-tracker.md`), тикеты в `issues/`.

## Architecture review

См. `architecture/INITIAL_REVIEW.md`. Три решения помечены REQUIRED_BEFORE_IMPLEMENTATION:
`MovieSeriesRepository` как отдельный слой в `core/network` (не в `app`, не размазан по
`VetroApiService`), `LookupResult` как единый not-found/failure контракт, `ExternalIds` вместо
очередного bonus-поля по образцу `malId`. Все три реализуются в TICKET-01/04.

Главный инвариант, за которым следит ревью каждого тикета: `Anime.episodes` для SERIES = только
вышедшие серии, никогда заявленное/общее (риск №1 architecture review).

## Global constraints

- Ветка `main`. **Рабочее дерево содержит существенный объём несвязанных незакоммиченных правок**
  (dock navigation, PiP, card menu, workspace UI) — не трогать, не коммитить вместе с тикетами
  этой фичи.
- `BatchEpisodeCheckUseCase.kt`, `AnimeNotifier.kt`, `ui/home/updates/EpisodeUpdateStack.kt`,
  `worker/AnimeUpdateWorker.kt` уже несут незакоммиченные (но релевантные — тот же аниме
  update-feed пайплайн) изменения; коммиты TICKET-08, затрагивающие эти файлы, неизбежно
  захватят их — зафиксировано как принятое отклонение, не блокирует работу.
- `MediaType.TV_SERIES` удаляется из enum — breaking на уровне кода, компенсируется
  legacy-алиасом `fromCategoryType`/`fromPersistedValue`.
- `MovieSeriesRepository` — только в `core/network`, не зависит от `Anime`/`AnimeLocalDataSource`.
- Provider-specific рейтинг-колонки не добавляются в этой итерации.

## Non-goals

Рекомендации для MOVIE/SERIES, web-links кино-каталог, AI-перевод названий для MOVIE/SERIES,
локальный плеер для SERIES, season-level UI, cadence-оптимизация по `SeriesStatus`, прокси для
Kinopoisk-ключа, пост-фильтрация anime-жанра внутри TMDB SERIES. Полный список — `spec.md` →
Out of scope.

## Verification commands

### Fast checks

```
./gradlew :core:network:compileDebugKotlin
./gradlew :app:compileDebugKotlin
```

### Ticket checks

```
./gradlew :app:testDebugUnitTest --tests "*MediaType*"
./gradlew :core:network:testDebugUnitTest --tests "*Tmdb*"
./gradlew :core:network:testDebugUnitTest --tests "*Kinopoisk*"
./gradlew :core:network:testDebugUnitTest --tests "*MovieSeries*"
./gradlew :app:testDebugUnitTest --tests "*AddFromApiUseCase*"
./gradlew :app:testDebugUnitTest --tests "*CollectionGapDetector*" --tests "*RepairAnimeDb*"
./gradlew :app:testDebugUnitTest --tests "*SeriesEpisodeCheck*"
```

### Full checks

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | MediaType split + схема БД + ExternalIds/LookupResult контракты | DONE | — | не коммичен | code-review (Standards+Spec), без блокеров после фикса |
| TICKET-02 | TmdbRemoteDataSource — типизированный DTO-слой TMDB | PENDING | 01 | — | — |
| TICKET-03 | KinopoiskRemoteDataSource — RU-источник | PENDING | 01 | — | — |
| TICKET-04 | MovieSeriesRepository + переключение VetroApiService | PENDING | 01,02,03 | — | — |
| TICKET-05 | Details-экран для MOVIE/SERIES | PENDING | 04 | — | — |
| TICKET-06 | AddFromApiUseCase — ExternalIds вместо source-веток | PENDING | 04 | — | — |
| TICKET-07 | Детектор пробелов + починка для MOVIE/SERIES | PENDING | 04,06 | — | — |
| TICKET-08 | SeriesEpisodeCheckUseCase — отслеживание вышедших серий | PENDING | 01,02,04 | — | — |

Порядок: 01 → (02, 03 параллельно допустимы, но реализуются последовательно) → 04 → (05, 06
параллельно допустимы) → 07 → 08.

## Ticket details

### TICKET-01 — MediaType split + схема БД + ExternalIds/LookupResult контракты

Status: DONE
Tracker reference: [`issues/01-mediatype-split-and-schema.md`](./issues/01-mediatype-split-and-schema.md)
Dependencies: —
Verification evidence: `compileDebugKotlin` (оба модуля) + `testDebugUnitTest` зелёные,
tests=352 failures=0 errors=0.
Deviations: `upsertFromSync` защищён self-select подзапросами от затирания новых id при
sync-pull (обнаружено ревью, не в исходном Scope); добавлен `Migration13Test.kt` +
test-зависимость `sqldelight-sqlite-driver` (TDD-требование тикета, изначально не выполнено).
Подробности — в самом тикете.

### TICKET-02 — TmdbRemoteDataSource

Status: PENDING
Tracker reference: [`issues/02-tmdb-remote-data-source.md`](./issues/02-tmdb-remote-data-source.md)
Dependencies: 01

### TICKET-03 — KinopoiskRemoteDataSource

Status: PENDING
Tracker reference: [`issues/03-kinopoisk-remote-data-source.md`](./issues/03-kinopoisk-remote-data-source.md)
Dependencies: 01

### TICKET-04 — MovieSeriesRepository

Status: PENDING
Tracker reference: [`issues/04-movie-series-repository.md`](./issues/04-movie-series-repository.md)
Dependencies: 01, 02, 03

### TICKET-05 — Details-экран

Status: PENDING
Tracker reference: [`issues/05-details-screen-wiring.md`](./issues/05-details-screen-wiring.md)
Dependencies: 04

### TICKET-06 — AddFromApiUseCase

Status: PENDING
Tracker reference: [`issues/06-add-and-dedup.md`](./issues/06-add-and-dedup.md)
Dependencies: 04

### TICKET-07 — Gap detector + repair

Status: PENDING
Tracker reference: [`issues/07-gap-detector-and-repair.md`](./issues/07-gap-detector-and-repair.md)
Dependencies: 04, 06

### TICKET-08 — SeriesEpisodeCheckUseCase

Status: PENDING
Tracker reference: [`issues/08-series-episode-check.md`](./issues/08-series-episode-check.md)
Dependencies: 01, 02, 04

## Decisions

- Полный паритет с аниме как долгосрочная цель, эта итерация — фундамент + два приоритетных
  пайплайна (обогащение полей/детектор пробелов, отслеживание сезонов/серий). Решение
  пользователя, зафиксировано при выборе scope в plan mode.
- Kinopoisk добавляется как RU-источник, TMDB остаётся EN/каноническим endpoint'ом. Решение
  пользователя.
- `MovieSeriesRepository` — в `core/network`, не в `app` (циклическая зависимость). Решение
  зафиксировано во втором раунде архитектурной критики плана.
- `Anime.episodes` для SERIES = released, никогда known/planned. Решение из третьего раунда
  критики — центральный инвариант всей фичи.

## Global deviations

Пока нет — заполняется по ходу выполнения.

## Known risks

См. architecture review → «Архитектурные риски». Кратко: (1) протекание known→episodes,
(2) `MovieSeriesRepository` обрастает app-зависимостью, (3) not-found проставляется на Failure,
(4) дедуп трактует low-confidence совпадение как точное.

## Deferred work

См. `spec.md` → Out of scope. Не создаются тикеты в этой итерации; при возврате к фиче —
отдельный проход `/ticket-autopilot`, отдельная спека.

- **Cloud sync для tmdb_id/kinopoisk_id** (обнаружено при реализации TICKET-01, не было в
  исходной спеке явным пунктом, но логически то же семейство "сначала SQL-миграция на живом
  Supabase, потом код"): `supabase/migrations/20260809000000_anime_tmdb_kinopoisk_ids.sql`
  создан и готов, но не применён на живом проекте (нужен доступ к Supabase Dashboard — за
  пользователем). После применения — добавить `tmdb_id`/`kinopoisk_id`/`*_not_found_at` в
  `AnimeRemoteDto` и оба маппинга (`push`/`pull`) в `SyncRepository.kt`. До этого момента новые
  id остаются только локальными (не синкаются между устройствами пользователя) —
  `upsertFromSync` защищён self-select подзапросами, чтобы pull их хотя бы не стирал.

## Final acceptance checklist

- [ ] Every required ticket completed (01–08)
- [ ] Full test suite or agreed equivalent run
- [ ] Specification reviewed requirement by requirement
- [ ] No unresolved blocking review findings
- [ ] Migration and compatibility behavior verified
- [ ] User-visible behavior verified
- [ ] Deferred work explicitly recorded
- [ ] Final architecture checkpoint completed
