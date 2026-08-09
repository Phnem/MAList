# TICKET-03: KinopoiskRemoteDataSource — RU-источник

## Status

PENDING

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

Empty before implementation.

## Deviations

Empty before implementation.

## Review findings

Empty before review.

## Completion evidence

Empty before completion.
