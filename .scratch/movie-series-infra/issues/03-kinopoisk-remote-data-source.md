# TICKET-03: KinopoiskRemoteDataSource — RU-источник

## Status

DONE_WITH_DEVIATIONS

## Objective

Добавить Kinopoisk.dev как выделенный RU-источник фильмов/сериалов, по образцу
`ShikimoriRemoteDataSource`, с типизированными по контенту методами поиска.

## User or system value

Сейчас RU-пользователи получают только TMDB-переводы (посредственное качество RU-метаданных).
Kinopoisk — аналог роли Shikimori для аниме: качественные RU-названия/рейтинги/описания, плюс
мост `externalId.tmdb` для связки с каноническим TMDB id без отдельного поиска.

## Dependencies

TICKET-01 (`LookupResult` контракт).

## Scope

- `core/network/.../network/dto/KinopoiskDto.kt` — `id`, `externalId.tmdb`, `name`/
  `alternativeName`, `description`, `poster.url`, `rating.kp`, `genres[].name`, `type`, `year`,
  `seasonsInfo[]`, `movieLength`.
- `core/network/.../network/kinopoisk/KinopoiskRemoteDataSource.kt`:
  - `searchMovie(query, year: Int? = null): LookupResult<List<ApiSearchResult>>` (`type=movie`).
  - `searchSeries(query, year: Int? = null): LookupResult<List<ApiSearchResult>>` (`type=tv-series`).
    **Никакого единого `search(query)` без типа** — типизация на уровне сигнатуры, не на уровне
    параметра, чтобы ошибка типа была невозможна на этапе компиляции.
  - `details(id: Int): LookupResult<KinopoiskDetails>` — `NotFoundById` на явное «нет записи».
- `core/network/build.gradle.kts` — `buildConfigField("String", "KINOPOISK_API_KEY", ...)`.
- `local.properties.example` — `KINOPOISK_API_KEY=` с комментарием в стиле существующих записей
  (Shikimori/MAL/AniList) + ссылкой на регистрацию ключа.
- Комментарий рядом с полем в build.gradle.kts, документирующий известное ограничение
  (BuildConfig-ключ извлекаем декомпиляцией APK) — не блокирует тикет, просто фиксируется явно.

## Out of scope

- Серверный прокси для ключа (бэклог).
- Оркестрация TMDB+Kinopoisk вместе (TICKET-04).

## Acceptance criteria

- [ ] `searchMovie`/`searchSeries` — раздельные типизированные методы, оба фильтруют по `type`
      на уровне запроса к API (не постфильтрацией результата).
- [ ] Kinopoisk-результат в `ApiSearchResult.externalIds` несёт оба id сразу:
      `ExternalIds(kinopoisk = dto.id, tmdb = dto.externalId?.tmdb)`.
- [ ] `details(id)` различает `NotFoundById` (источник явно сказал «нет записи») от `Failure`
      (сеть/авторизация/парсинг).
- [ ] `KINOPOISK_API_KEY` доступен через `BuildConfig`, отсутствие ключа в `local.properties`
      даёт `Failure` (не крэш, не тихий пустой результат, замаскированный под `NoMatch`).
- [ ] `./gradlew :core:network:compileDebugKotlin` зелёный.

## Verification plan

```
./gradlew :core:network:compileDebugKotlin
./gradlew :core:network:testDebugUnitTest --tests "*Kinopoisk*"
```

Юнит-тесты на мок HTTP-ответах (без реального ключа/сети) — happy path обоих `searchMovie`/
`searchSeries`, `NotFoundById`, `Failure` на 401 (отсутствующий/неверный ключ).

## TDD classification

RECOMMENDED — в основном сетевой маппинг, но `NotFoundById` vs `Failure` различение достаточно
важно (используется в TICKET-04's `resolveTmdbId`), стоит покрыть тестом до интеграции.

## Expected architecture impact

Изолированное добавление, тот же паттерн, что существующие data source'ы. Нулевой структурный
риск за пределами нового кода.

## Risks

- Kinopoisk.dev — неофициальный сторонний API, формат ответа может отличаться от документации/
  меняться без предупреждения — не полагаться только на happy-path тест, покрыть unexpected
  field absence graceful degradation (не крэш на отсутствующем `externalId`).

## Implementation notes

- Мапперы (`KinopoiskMovieDto.toApiSearchResult`/`.toDetails`) вынесены в отдельный
  `KinopoiskMappers.kt` как чистые функции — тестируются без HTTP (тот же подход, что
  `TmdbEpisodeCalculator` в TICKET-02), закрывает часть TDD-обязательства без полного
  mock-харнесса.
- `rating.kp` — уже 0..10 у Kinopoisk; конвертирован в общую 0..100 шкалу (`×10`, как TMDB
  `vote_average×10`), чтобы `RatingScale`/repair дальше по конвейеру не знали о разнице
  источников. `toDetails()` намеренно оставляет `ratingKp` НЕ конвертированным — на будущее,
  если понадобится показать "чистый" Kinopoisk-рейтинг отдельно.
- `type=movie`/`type=tv-series` — query-параметр на уровне запроса, а не постфильтрация
  результата: `searchMovie`/`searchSeries` физически не могут перепутать тип по конструкции
  сигнатур.
- `local.properties.example` заодно задокументировал `TMDB_API_KEY` (был в коде с TICKET-02,
  но нигде не описан для нового разработчика) — маленькое, но оправданное расширение по месту,
  не отдельная задача.

## Deviations

- **Planned**: Acceptance criteria включали HTTP-mock тесты (`NotFoundById` на 401, happy path
  через мок).
  **Actual**: как и в TICKET-02, HTTP-слой (`KinopoiskRemoteDataSource.runRequest`) не покрыт
  mock-тестами — только компиляцией. Вместо этого добавлен `KinopoiskMappersTest.kt` (8 тестов)
  на чистые функции маппинга (`toApiSearchResult`/`toDetails`), включая явный тест на
  `externalId.tmdb`-мост (главная причина, ради которой Kinopoisk вообще участвует в
  id-резолве в TICKET-04).
  **Reason**: тот же, что в TICKET-02 — единый follow-up на Ktor `MockEngine` для обоих data
  source'ов сразу эффективнее, чем заводить харнесс дважды по частям.
  **Consequence/Follow-up**: см. TICKET-02 → Deviations и MASTER_PLAN → Deferred work (уже
  зафиксировано, здесь просто подтверждён тот же паттерн).

## Review findings

Самопроверка (тот же непрерывный проход, что TICKET-01/02). Мапперы протестированы явно;
HTTP-слой — компиляция + структурное соответствие TMDB-паттерну из TICKET-02 (одинаковый
`runRequest`, что снижает риск расхождения в обработке ошибок между источниками).

## Completion evidence

- Command: `./gradlew.bat :core:network:compileDebugKotlin :core:network:testDebugUnitTest` →
  `BUILD SUCCESSFUL`, `KinopoiskMappersTest` 8/8 зелёных (плюс `TmdbEpisodeCalculatorTest` 9/9
  из TICKET-02, регрессий нет).
- Command: `./gradlew.bat :app:compileDebugKotlin` → `BUILD SUCCESSFUL` (новый код изолирован в
  `core/network`, `app` не затронут).

Файлы: `core/network/.../dto/KinopoiskDto.kt`, `core/network/.../kinopoisk/KinopoiskMappers.kt`,
`core/network/.../kinopoisk/KinopoiskRemoteDataSource.kt`,
`core/network/src/test/.../kinopoisk/KinopoiskMappersTest.kt`, `core/network/build.gradle.kts`
(новое `buildConfigField`), `local.properties.example` (новая секция).

Не выполнено: HTTP-mock тесты (см. Deviations, тот же follow-up что TICKET-02); DI-регистрация
и подключение — часть TICKET-04.
