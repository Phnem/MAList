# Инфраструктура поиска/обогащения для фильмов и сериалов

Status: APPROVED (согласовано с пользователем в plan mode, три раунда архитектурной критики)

## Problem

Фильмы и сериалы (`MediaType.TV_SERIES`) обслуживаются одним TMDB-вызовом, зашитым инлайн в
`VetroApiService` (~900-строчный класс вперемешку с аниме/манга-кодом): без DTO (сырой
`JsonObject`), без сохранения `tmdbId` (повторный точечный запрос невозможен), с багом
(`checkTmdb` для MOVIE ищет через `/search/tv`). `MediaType.TV_SERIES` схлопывает фильм и
сериал в один тип записи. Details-экран не умеет обновлять/чинить такие записи (`fetchDetails`
не имеет TMDB-ветки). Каждый пайплайн обогащения аниме (`CollectionGapDetector`,
`BatchEpisodeCheckUseCase`, title-enrichment, `RecommendationEngine`, «доступно к просмотру»)
жёстко фильтрует `mediaType == ANIME` и пропускает фильмы/сериалы целиком.

## Desired outcome

Фильмы/сериалы получают архитектурный фундамент, сравнимый с аниме: типизированный
многоисточниковый сетевой слой (TMDB + новый RU-источник Kinopoisk), сохранённые внешние id,
рабочий Details-refresh, и два приоритетных пайплайна обогащения — детектор пробелов/починка
полей и отслеживание вышедших серий сериалов. Остальной паритет (рекомендации, web-links под
кино, AI-перевод, локальный плеер, season-level UI) — намеренно вне скоупа этой итерации,
зафиксирован как бэклог.

## Current behavior

- `VetroApiService.searchApi`/`fetchDetails`/`findTotalEpisodes` дергают TMDB инлайн-методами
  (`searchTmdbMovie`, `searchTmdbTv`, `parseTmdbResults`, `checkTmdb`) с ручным парсингом
  `JsonObject` через `kotlinx.serialization.json`.
- `MediaType` — `enum { ANIME, MANGA, TV_SERIES }`; `MediaType.fromCategoryType` схлопывает
  строки `"MOVIE"`/`"SERIES"`/`"TV"` в один `TV_SERIES`.
- В `Anime`/схеме БД нет `tmdbId`/`kinopoiskId` — только `anilistId`/`malId`/`shikimoriId`.
- `fetchDetails` не имеет ветки для MOVIE/SERIES — Details-экран для них не обновляется.
- `CollectionGapDetector.scan()`, `BatchEpisodeCheckUseCase`, `RecommendationEngine`,
  title-enrichment юзкейсы, Home «доступно к просмотру» — все фильтруют
  `mediaType == MediaType.ANIME`, пропуская MOVIE/SERIES целиком.
- `RepairAnimeDbUseCase.repairOne` уже частично готов к MOVIE/SERIES (при `categoryType`
  MOVIE/SERIES вызывает `searchApi` и прогоняет через `applyRepair`), но
  `externalIdsFrom`/`FieldGaps.missingExternalId` не знают TMDB/Kinopoisk — пробел никогда не
  закрывается.

## Required behavior

См. полный архитектурный план (canonical architecture reference для этой спеки):
[`C:\Users\2004i\.claude\plans\majestic-meandering-quiche.md`](file:///C:/Users/2004i/.claude/plans/majestic-meandering-quiche.md).
Кратко:

1. `MediaType.TV_SERIES` → `MOVIE` + `SERIES`, с legacy-совместимым парсингом на границе
   сериализации (`MediaType.fromPersistedValue`) и на границе категории поиска
   (`MediaType.fromCategoryType`).
2. Схема БД (миграция 13): `tmdb_id`, `tmdb_not_found_at`, `kinopoisk_id`,
   `kinopoisk_not_found_at`.
3. `TmdbRemoteDataSource` (типизированный DTO-слой, `core/network/.../network/tmdb/`) —
   заменяет инлайн-парсинг, чинит баг MOVIE→`/search/tv`, вводит разделение
   `releasedEpisodesForTv()` (вышедшие) vs `knownEpisodesForTv()` (заявлено).
4. `KinopoiskRemoteDataSource` (`core/network/.../network/kinopoisk/`) — RU-источник,
   типизированные `searchMovie`/`searchSeries` (без общего `search`), API-ключ через
   `BuildConfig.KINOPOISK_API_KEY`.
5. `MovieSeriesRepository` (`core/network/.../network/movie/`) — единая точка оркестрации
   TMDB+Kinopoisk (поиск, детали, резолв id, released episode state), НЕ в `app` (циклическая
   зависимость), НЕ размазана обратно по `VetroApiService`.
6. `ExternalIds` value object на `ApiSearchResult` — заменяет source-based ветвление по строке.
7. `LookupResult<T>` (`Found`/`NoMatch`/`NotFoundById`/`Failure`) — единый контракт для всех
   MOVIE/SERIES lookup'ов, определяет когда ставить `*_not_found_at` и когда чистить протухший id.
8. `CollectionGapDetector`/`RepairAnimeDbUseCase` расширяются на MOVIE/SERIES с
   critical(`missingTmdb`)/optional(`missingKinopoisk`) разделением пробелов.
9. `SeriesEpisodeCheckUseCase` — новый юзкейс отслеживания вышедших серий SERIES, с
   центральным инвариантом: `Anime.episodes` для SERIES = только вышедшие серии.

## User-visible behavior

- Поиск фильма/сериала на RU и EN языке приложения возвращает постер/рейтинг/жанры/описание.
- Добавленная запись сохраняет `tmdbId` (и `kinopoiskId` на RU) — повторный точечный запрос
  работает.
- Открытие Details для ранее добавленного фильма/сериала подгружает/обновляет данные (раньше —
  пусто).
- Фоновая починка коллекции (Live Maintenance / «Исправить БД») дозаполняет поля у
  фильмов/сериалов так же, как у аниме.
- Онгоинг-сериалы с сохранённым `tmdbId` получают уведомление «вышла новая серия» в существующей
  ленте обновлений, когда серия действительно вышла (не когда TMDB просто знает будущую).

## Domain rules

- **`Anime.episodes` для SERIES = количество вышедших серий, никогда заявленное/общее.**
  Единственный писатель — `SeriesEpisodeCheckUseCase`, единственный источник —
  `releasedEpisodesForTv()`. `AddFromApiUseCase`, `RepairAnimeDbUseCase.applyRepair`,
  `MovieSeriesRepository.fetchDetails` не имеют права записать туда
  `knownEpisodesForTv()`/`number_of_episodes`.
- **`Anime.episodes` для MOVIE = 1** всегда.
- **TMDB — канонический id**, Kinopoisk — вспомогательный/RU-обогащающий.
- **`NoMatch` vs `Failure` vs `NotFoundById`** — только `NoMatch` (поиск выполнился, кандидатов
  нет) ставит `*_not_found_at`; `Failure` никогда не ставит и не очищает id; `NotFoundById`
  (точечный запрос по сохранённому id вернул явное «нет записи») очищает протухший id и
  запускает разовый повторный резолв по названию.
- **Fill-gap, не blind replace** — как и в существующем `RepairAnimeDbUseCase.applyRepair`,
  починка никогда не перезаписывает уже непустое поле худшим значением.

## Functional requirements

Перечислены по разделам плана (см. ссылку выше, разделы 1–10) — не дублируются здесь построчно,
чтобы не разойтись с canonical-источником при правках. Тикеты (`issues/`) ссылаются на
конкретные разделы плана.

## Non-functional requirements

- `MovieSeriesRepository` живёт в `core/network`, не создаёт циклическую зависимость
  `app → core:network → app`.
- `VetroApiService` остаётся тонким диспетчером для MOVIE/SERIES — вся RU/EN-маршрутизация,
  фоллбэки, дедуп, merge полей и резолв id — внутри `MovieSeriesRepository`.
- API-ключ Kinopoisk через `BuildConfig` — известное ограничение (декомпилируемо), не
  блокирует итерацию, задокументировано как принятый риск.

## Compatibility and migration constraints

- Легаси `mediaType = 'TV_SERIES'` в БД мигрируется в `MOVIE`/`SERIES` по `categoryType`;
  неоднозначные записи — дефолт `SERIES`.
- Legacy-парсинг `"TV_SERIES"` сохраняется как алиас `SERIES` на всех границах десериализации
  (SQLDelight-маппер, sync, backup/import) — не только в `fromCategoryType`.
- Легаси-записи SERIES: первый успешный `episodeState(tmdbId)` после миграции **не** создаёт
  `anime_update`-событие (нельзя доказать released-семантику старого значения) — молча
  нормализует `episodes`.

## Failure and fallback behavior

См. `LookupResult` контракт в п. Domain rules. Transient-сбои (таймаут/сеть/401/403/429/5xx/
парсинг) никогда не меняют `*_not_found_at` и не очищают сохранённые id.

## Out of scope

- Рекомендации для MOVIE/SERIES (`RecommendationEngine`).
- Web-links «где смотреть» под кино-каталог.
- AI-перевод названий для MOVIE/SERIES.
- Локальный плеер — «доступно к просмотру» для SERIES.
- Season-level UI (чипы «Сезоны»).
- Cadence-оптимизация по `SeriesStatus` (`lastEpisodeCheckAt`/scheduler-поле).
- Provider-specific рейтинг-колонки (`tmdb_rating`/`kinopoisk_rating`) — существующее `rating`
  используется через merge-policy.
- Прокси-сервис для Kinopoisk API-ключа.
- Пост-фильтрация anime-жанра внутри TMDB SERIES-поиска.

## Acceptance criteria

- [ ] `MediaTypeFromCategoryTest` обновлён и проходит с новым разделением + legacy-алиасом.
- [ ] Легаси-парсинг на границе сериализации использует `fromPersistedValue`, не `valueOf`.
- [ ] Регресс-тест: легаси SERIES (`episodes` = заявленное) не создаёт ложный `anime_update` на
      первом проходе, нормализуется тихо.
- [ ] Регресс-тест: `RepairAnimeDbUseCase`/Details-refresh не перезаписывает `episodes` для
      SERIES значением `knownEpisodes`.
- [ ] Ручное добавление фильма/сериала на RU и EN сохраняет `tmdbId`/`kinopoiskId`, Details не
      пустой.
- [ ] Дедуп не схлопывает реальный тайтл-ремейк с другим годом в одну карточку.
- [ ] Восстановление протухшего `tmdbId` через `NotFoundById` работает.
- [ ] Миграция 13 проходит на копии реальной БД без потери данных.
- [ ] Live Maintenance/«Исправить БД» дозаполняет `tmdb_id`/`kinopoisk_id`; сетевой сбой не
      ставит `*_not_found_at`; отсутствие только Kinopoisk не блокирует повторную попытку.
- [ ] Проверка обновлений на реальном онгоинг-сериале даёт **вышедшие**, не заявленные серии.
- [ ] `./gradlew :core:network:compileDebugKotlin :app:compileDebugKotlin` зелёный.

## Open questions

Нет BLOCKING-вопросов — все спорные архитектурные решения закрыты тремя раундами ревью плана
(см. историю решений в самом плане). Один пункт помечен «уточнить при реализации»: фактическая
модель хранения `titleEn`/`titleRu` для MOVIE/SERIES (раздел 7 плана) — проверяется в TICKET-05.

## Test seams

- `MediaType.fromCategoryType`/`fromPersistedValue` — чистые функции, юнит-тестируемые.
- `TmdbRemoteDataSource.releasedEpisodesForTv(id, clock: Clock)` — принимает `Clock`, границы
  дат тестируемы без реальной сети.
- `LookupResult` — sealed interface, все ветки исчерпывающе тестируемы.
- `MovieSeriesRepository.resolveTmdbId`/дедуп-каскад — чистая логика поверх моков data source'ов.
- `SeriesEpisodeCheckUseCase` — легаси-нормализация и released-vs-known инвариант, ключевые
  регресс-тесты этой итерации.
