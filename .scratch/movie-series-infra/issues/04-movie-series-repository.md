# TICKET-04: MovieSeriesRepository + переключение VetroApiService

## Status

DONE

## Objective

Ввести `MovieSeriesRepository` как единственную точку оркестрации TMDB+Kinopoisk (поиск,
резолв id, дедуп), переключить `VetroApiService` на тонкий диспетчер для MOVIE/SERIES, удалить
старый инлайн-код TMDB.

## User or system value

Первая реально видимая пользователю подвижка: поиск фильмов/сериалов на RU впервые получает
Kinopoisk-данные вперемешку с TMDB-фоллбэком, дедуп больше не путает ремейки/омонимы, MOVIE-баг
(`/search/tv`) исчезает из реального пути, а не только из мёртвого кода.

## Dependencies

TICKET-01, TICKET-02, TICKET-03.

## Scope

- `core/network/.../network/movie/MovieSeriesRepository.kt` — **не в `app`** (см. architecture
  review, риск №2): `search`, `fetchDetails`, `resolveTmdbId`, `episodeState`. Работает только с
  `AppContentType`/`ApiSearchResult`/`ExternalIds`/DTO — никаких импортов `Anime`/
  `AnimeLocalDataSource`.
- `resolveTmdbId(externalIds, title, mediaType, year)` — лестница: `tmdbId` есть →
  `tmdb.checkExists` → `Found` использовать / `NotFoundById` резолвить заново /
  `Failure` не разрушать; `kinopoiskId` есть → `kinopoisk.details` → `externalId.tmdb`; иначе →
  `resolveByTitle` (прямой TMDB search, НЕ через `search()`).
- Приоритет источников по полю при merge (fill-gap, не replace): id → TMDB; RU название/описание
  → Kinopoisk; EN название/overview → TMDB; постер → лучший доступный; жанры → merge; структура
  серий/статус → TMDB; рейтинг → RU предпочитает Kinopoisk, EN предпочитает TMDB.
- Дедуп-каскад внутри `search()`: `tmdbId` exact → `kinopoiskId` exact → `originalTitle`
  нормализовано + год + `mediaType` exact → локализованное название + год + `mediaType` +
  высокий порог `TitleMatcher` (последняя, самая слабая ступень — сомнение → НЕ мержить).
- `VetroApiService`: конструктор получает `movieSeriesRepository: MovieSeriesRepository`;
  `searchApi`/`findTotalEpisodes` для `AppContentType.MOVIE/SERIES` делегируют туда.
- **Удалить старый инлайн-код TMDB** из `VetroApiService` (`searchTmdbMovie`, `searchTmdbTv`,
  `parseTmdbResults`, `checkTmdb`, `tmdbKey()`) — это единственный тикет, где происходит и
  добавление вызова нового класса, и удаление старого мёртвого кода одновременно (см. решение в
  TICKET-02).
- DI: `coreNetworkModule.kt` — `single { TmdbRemoteDataSource(...) }`,
  `single { KinopoiskRemoteDataSource(...) }`, `single { MovieSeriesRepository(get(), get()) }`,
  `VetroApiService(...)` получает новую зависимость.

## Out of scope

- `fetchDetails` для Details-экрана конкретно (TICKET-05 — здесь только контракт метода на
  `MovieSeriesRepository`, реальная проводка от `DetailsViewModel` — следующий тикет).
- `AddFromApiUseCase`/repair (TICKET-06/07).

## Acceptance criteria

- [ ] Поиск MOVIE/SERIES на RU языке приложения возвращает Kinopoisk-результаты первично, TMDB —
      добор/фоллбэк, без дублей одного и того же тайтла.
- [ ] Поиск MOVIE/SERIES на EN — TMDB, Kinopoisk не участвует.
- [ ] `resolveTmdbId` покрыт юнит-тестом на все 4 пути лестницы (id валиден / id протух /
      только kinopoiskId / ничего нет).
- [ ] Дедуп-тест: два разных тайтла с одинаковым локализованным названием, разными годами — НЕ
      мержатся.
- [ ] `grep` подтверждает отсутствие импортов `com.example.myapplication.data.models.Anime` и
      `AnimeLocalDataSource` в `core/network/.../network/movie/`.
- [ ] Старый инлайн TMDB-код физически отсутствует в `VetroApiService` (`grep -c "search/tv"` в
      файле = 0 либо только внутри нового класса).
- [ ] `./gradlew :core:network:compileDebugKotlin :app:compileDebugKotlin` зелёный.

## Verification plan

```
./gradlew :core:network:compileDebugKotlin
./gradlew :app:compileDebugKotlin
./gradlew :core:network:testDebugUnitTest --tests "*MovieSeries*"
```

Ручная проверка: поиск «Dune» (или другого тайтла с несколькими экранизациями/годами) на RU и EN
— карточки не схлопываются в одну с чужим годом.

## TDD classification

REQUIRED — дедуп-каскад и `resolveTmdbId` это ровно "fallback selection"/"data merging" из
обязательного TDD-списка; это самая рискованная логика после `releasedEpisodesForTv`
(TICKET-02).

## Expected architecture impact

Новая граница `core/network/.../network/movie/` — ключевое решение всей фичи (см. architecture
review п.6). `VetroApiService` сокращается (удаление ~140 строк инлайн-кода), становится ближе к
чистому диспетчеру.

## Risks

- Соблазн засунуть RU/EN-роутинг обратно в `VetroApiService` "чтобы было проще" — ревью тикета
  явно проверяет, что вся ветвящаяся логика осталась в `MovieSeriesRepository`.
- Удаление старого кода одновременно с добавлением нового — больше риска сломать компиляцию
  посередине работы; коммитить только после зелёной сборки.

## Implementation notes

- Добавлен глубокий модуль `MovieSeriesRepository` с внутренними gateway seam'ами: публичный
  вызывающий код видит операции поиска/деталей/id-resolution/episode-state, а тесты подменяют
  только сетевые адаптеры.
- RU-поиск объединяет Kinopoisk (первичный) и TMDB (fill-gap/fallback), EN-поиск обращается
  только к TMDB. `MovieResultDeduper` объединяет поля без blind replace.
- `resolveTmdbId` реализует лестницу saved TMDB → Kinopoisk bridge → прямой TMDB title/year
  search; `Failure` сохраняет существующий id, `NotFoundById` запускает повторный резолв.
- `VetroApiService` переключён на репозиторий для `searchApi`/`findTotalEpisodes`; старые
  `searchTmdbMovie`/`searchTmdbTv`/`parseTmdbResults`/`checkTmdb`/`tmdbKey` удалены.
- DI регистрирует оба data source и `MovieSeriesRepository`.

## Deviations

- Плановый `TitleMatcher` из `app` нельзя импортировать в `core/network` без циклической
  зависимости. Реализован эквивалентный internal `MovieTitleMatcher` с тем же порогом 0.85,
  token/Jaccard/Levenshtein policy; он общий для дедупа и id-resolution.
- Для строгой original-title ступени `ApiSearchResult` расширен полем `originalTitle`, а TMDB
  DTO — `original_title`/`original_name`; Kinopoisk использует `alternativeName`.

## Review findings

- Первое двухосевое ревью нашло два BLOCKING дефекта: отсутствующую original-title ступень и
  склейку одинакового Kinopoisk id при конфликтующих канонических TMDB id. Оба исправлены и
  защищены регрессионными тестами.
- Standards-ось нашла дублирование нормализации/Levenshtein между дедупом и резолвером;
  устранено общим `MovieTitleMatcher`.
- Повторное ревью: все три находки RESOLVED, новых блокеров нет.

## Completion evidence

- `./gradlew.bat :core:network:testDebugUnitTest :app:testDebugUnitTest
  :core:network:compileDebugKotlin :app:compileDebugKotlin` → `BUILD SUCCESSFUL`;
  383 теста, failures=0, errors=0.
- `grep`/`rg`: в `network/movie` нет импортов `Anime`/`AnimeLocalDataSource`; в
  `VetroApiService` нет inline-TMDB методов/URL.
- `git diff --check` → без ошибок.
- Commit: `3b88d85`.
