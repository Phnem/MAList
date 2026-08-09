# TICKET-07: Детектор пробелов + починка для MOVIE/SERIES

Status: DONE

## Status

DONE

## Objective

Расширить `CollectionGapDetector`/`RepairAnimeDbUseCase` на MOVIE/SERIES с
critical(`missingTmdb`)/optional(`missingKinopoisk`) разделением пробелов, чтобы фоновая починка
(Live Maintenance / «Исправить БД») действительно дозаполняла поля у фильмов/сериалов.

## User or system value

Первый из двух приоритетных пайплайнов, которые пользователь выбрал в первую очередь. Сейчас
`CollectionGapDetector` вообще не видит MOVIE/SERIES; `RepairAnimeDbUseCase.repairOne` частично
готов, но `externalIdsFrom` не умеет писать TMDB/Kinopoisk id — пробел `missingExternalId`
никогда не закрывается для этих записей.

## Dependencies

TICKET-04 (нужен `MovieSeriesRepository`/`ExternalIds` в реальном пути поиска),
TICKET-06 (согласованность с `episodes`-инвариантом при заполнении полей).

## Scope

- `app/.../domain/enrichment/CollectionGapDetector.kt` — фильтр `scan()`: `mediaType == ANIME`
  → `mediaType in {ANIME, MOVIE, SERIES}` (MANGA не трогать). Title-gap запросы
  (`selectNeedingTitleEn/Ru`) остаются ANIME-only.
- `app/.../domain/settings/RepairAnimeDbUseCase.kt`:
  - `FieldGaps` для MOVIE/SERIES — раздельные `missingTmdb` (critical) / `missingKinopoisk`
    (optional). ANIME/MANGA поведение `missingExternalId` не меняется.
  - Новый метод/поле `hasCriticalGaps()`/`hasRetryableOptionalGaps()` — запись включается в
    проход, если `hasCriticalGaps() || hasRetryableOptionalGaps()`, где optional-пробел
    "retryable" если `kinopoiskNotFoundAt` пуст либо старше 14 дней.
  - `externalIdsFrom` — читает `candidate.externalIds.tmdb`/`.kinopoisk` вместо ветвления по
    `result.source` для MOVIE/SERIES-кандидатов.
  - `applyRepair`/`SaveAnimeParams` — прокинуть `tmdbId`/`kinopoiskId`; рейтинг по merge-policy
    (RU-контент предпочитает Kinopoisk, EN — TMDB, не перезаписывать непустое значение); **для
    SERIES `episodes` не трогается вовсе** (только для MOVIE — `episodes = 1`, если ещё не
    проставлено).
  - `*_not_found_at` простановка — через `LookupResult` (TICKET-01/02/03), не эвристикой по
    исключению.

## Out of scope

- `SeriesEpisodeCheckUseCase` (TICKET-08) — episode-tracking логика отдельно.
- Cadence-оптимизация по `SeriesStatus` — бэклог.

## Acceptance criteria

- [x] `CollectionGapDetector.scan()` включает MOVIE/SERIES записи с пробелами.
- [x] Запись `tmdbId != null, kinopoiskId == null` — не выпадает из повторных проходов
      (retryable optional gap), но и не считается "сломанной" (`needsRepair` не форсит
      бесконечный critical-путь).
- [x] Live Maintenance/«Исправить БД» на реальной/тестовой коллекции с недозаполненными
      MOVIE/SERIES дозаполняет `tmdb_id`/`kinopoisk_id`, постер/рейтинг/жанры.
- [x] Регресс-тест: смоделированный сетевой сбой (Failure) НЕ проставляет `*_not_found_at`.
- [x] Регресс-тест: `applyRepair` для SERIES не изменяет `episodes` ни при каком входе.
- [x] `LiveMaintenanceWorker`/`FullEnrichmentWorker` не требуют изменений (уже вызывают
      `CollectionGapDetector`/`RepairAnimeDbUseCase` универсально) — проверить, что это
      действительно так после правок, не предполагать.
- [x] `./gradlew :app:compileDebugKotlin` зелёный.

## Verification plan

```
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*CollectionGapDetector*" --tests "*RepairAnimeDb*"
```

Ручная проверка: коллекция с несколькими недозаполненными фильмами/сериалами → запуск «Исправить
БД» → поля дозаполнились, `*_not_found_at` не проставился при отключённой сети.

## TDD classification

REQUIRED — critical/optional gap классификация и merge-policy это "state transitions"/"data
merging" из обязательного TDD-списка; это самый рискованный тикет с точки зрения "тихо сломать
существующий ANIME/MANGA repair-путь".

## Expected architecture impact

Расширение существующих структур (`FieldGaps`, `CollectionGapDetector.scan()`), без новых
модулей. Риск — увеличение сложности `RepairAnimeDbUseCase`, уже достаточно большого класса;
architecture review этого тикета явно проверяет, не пора ли выделить MOVIE/SERIES-ветку в
отдельный класс (follow-up-кандидат, не обязательный сейчас).

## Risks

- `RepairAnimeDbUseCase` уже большой класс — риск, что MOVIE/SERIES-ветки, добавленные inline,
  сделают его ещё менее читаемым. Если diff тикета станет непропорционально большим,
  рассмотреть выделение `MovieSeriesRepairStrategy`-подобной структуры как follow-up (не
  блокирует этот тикет, если merge-policy и без того укладывается в разумный объём).
- Легко случайно тронуть ANIME/MANGA `missingExternalId`-путь при рефакторинге общей функции —
  держать ветки MOVIE/SERIES и ANIME/MANGA раздельными, не объединять "для симметрии".

## Implementation notes

- `MovieSeriesRepository.lookupForRepair` сохраняет отдельные `LookupResult` провайдеров,
  валидирует сохранённые id и только на `NotFoundById` помечает их stale. Полный repair очищает
  stale id и выполняет title re-resolution в том же проходе; `Failure` id не очищает.
- `FieldGaps` разделяет critical TMDB и optional Kinopoisk. Kinopoisk retry использует 14 дней,
  а provider gaps намеренно не пишутся в общий файловый journal.
- `RepairAnimeDbPolicy` содержит чистые правила классификации, episode-инвариант и merge typed/
  legacy external ids. SERIES всегда сохраняет исходный released count, MOVIE инициализируется 1.
- `updateAnime` теперь действительно сохраняет `titleEn`/`titleRu`; AddEdit переносит TMDB/
  Kinopoisk id и timestamps через UI state, не стирая результат repair при ручном редактировании.

## Deviations

- `LiveMaintenanceWorker` всё же получил небольшую правку: без разделения journalable/provider
  gaps сетевой `Failure` скрывал бы critical TMDB gap на TTL файлового журнала. Оркестрация
  воркера не менялась.
- Ручная проверка на реальной коллекции не выполнялась в CLI-сессии; тот же путь покрыт policy,
  repository и SQLDelight compilation тестами, а debug APK успешно собран.

## Review findings

- Первое Spec review нашло два BLOCKING: stale id не доходил до `NotFoundById`, а generic journal
  мог скрыть TMDB gap после `Failure`. Оба исправлены и закрыты повторным ревью.
- Standards review нашло дублирование provider orchestration и двух gap projections. Поиск
  сведён в `lookupProviders`, journalable-набор вычисляется один раз; финальная проверка чистая.
- IMPORTANT по покрытию merge-policy закрыт тестами stale/failure, journal, typed ids,
  ANIME/MANGA, SERIES/MOVIE episodes и RU rating priority.

## Completion evidence

- `./gradlew :core:network:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` →
  BUILD SUCCESSFUL; 426 тестов, failures=0, errors=0.
- `git diff --check` → без ошибок.
- Code commit: `46bc4f1`.
