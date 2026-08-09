# Execution Log — movie-series-infra

## 2026-08-09 — Initial codebase discovery

Проведено в plan mode (два Explore-агента + прямое чтение файлов) до входа в ticket-autopilot;
здесь зафиксировано ретроактивно, чтобы не терять контекст между сессиями.

### Relevant modules

- `core/network/src/main/java/com/example/myapplication/network/` — `VetroApiService.kt`
  (god-class фасад), `ApiSearchResult.kt`, `AppContentType.kt`, `ApiService.kt` (интерфейс),
  data source'ы по образцу: `ShikimoriRemoteDataSource.kt`, `AniListRemoteDataSource.kt`,
  `KitsuRemoteDataSource.kt`, `AnilibriaRemoteDataSource.kt`, `mangadex/`, `remanga/`.
- `core/network/.../network/di/coreNetworkModule.kt` — Koin DI, единый `HttpClient` (Ktor 3.x +
  OkHttp), `TokenBucketRateLimiter` именованные инстансы, `ApolloClient` для AniList GraphQL.
- `app/src/main/java/com/example/myapplication/data/models/Anime.kt` — доменная модель,
  `MediaType` enum + `fromCategoryType`.
- `app/src/main/sqldelight/com/example/myapplication/data/local/Anime.sq` — схема, последняя
  миграция `migrations/12.sqm`.
- `app/.../domain/enrichment/` — `CollectionEnrichmentCoordinator`, `CollectionGapDetector`,
  `EnrichmentGapJournal`, `EnrichmentModels` (`GapKind`), `LiveMaintenanceWorker`,
  `FullEnrichmentWorker`.
- `app/.../domain/settings/RepairAnimeDbUseCase.kt` — уже частично готов к MOVIE/SERIES
  (generic `searchApi`-путь при `categoryType` MOVIE/SERIES), но `externalIdsFrom`/
  `FieldGaps.missingExternalId` не знают TMDB/Kinopoisk.
- `app/.../updates/BatchEpisodeCheckUseCase.kt` — эталон для `SeriesEpisodeCheckUseCase`, но
  сильно сложнее (франшиза AniList PREQUEL/SEQUEL) — для TMDB TV не нужно, там один `tv id`
  на все сезоны.
- `app/.../domain/search/AddFromApiUseCase.kt`, `app/.../domain/addedit/SaveAnimeParams.kt`/
  `SaveAnimeUseCase.kt` — точки сохранения записи, id-ветвление по `result.source` строкой.

### Existing behavior

- TMDB зашит инлайн в `VetroApiService` (`searchTmdbMovie`, `searchTmdbTv`,
  `parseTmdbResults`, `checkTmdb` — строки ~754–891), сырой `kotlinx.serialization.json.JsonObject`,
  без DTO.
- Баг: `checkTmdb` для **любого** `AppContentType` (включая MOVIE) ищет через `/search/tv` —
  строка 862.
- `AppContentType.categoryTypeName()` уже пишет `categoryType` строками `"MOVIE"`/`"SERIES"` при
  поиске — надёжный сигнал типа записи.
- `MediaType.fromCategoryType` схлопывает `"SERIES"/"TV_SERIES"/"TV"/"MOVIE"` в один
  `TV_SERIES` (комментарий в коде уже документирует это как осознанный компромисс).
- `RepairAnimeDbUseCase.repairOne` (строки ~147–160) уже вызывает `searchApi` для не-ANIME
  `categoryType`, прогоняет через общий `applyRepair` — инфраструктура есть, просто
  `externalIdsFrom` (строка 511) не знает TMDB/Kinopoisk и возвращает `Triple(null,null,null)`
  всегда → `missingExternalId` навечно `true` для MOVIE/SERIES.
- `CollectionGapDetector.scan()` фильтрует `mediaType == MediaType.ANIME` — MOVIE/SERIES
  никогда не попадают в фоновую починку вообще, несмотря на то что `RepairAnimeDbUseCase` их
  бы обработал.
- SQLDelight `anime` таблица: единая для всех типов, `anilist_id`/`mal_id`/`shikimori_id` +
  `*_not_found_at` — нет TMDB/Kinopoisk аналогов.

### Existing terminology

- `AppContentType` (ANIME/MOVIE/SERIES/MANGA) — раздел поиска, источник истины типа при
  добавлении.
- `MediaType` (ANIME/MANGA/TV_SERIES→MOVIE,SERIES) — тип хранимой записи, решает Details-UI.
- `ApiSearchResult` — унифицированная карточка результата поиска для ЛЮБОГО источника, поле
  `source: String` (не enum) для различения.
- `Gap`/`GapKind`/`FieldGaps` — вокабуляр детектора пробелов (`domain/enrichment/`).
- `Resolution`/`AiringProgress` — вокабуляр `BatchEpisodeCheckUseCase` для аниме-эквивалента
  released-серий; для SERIES вводится параллельный, но более простой вокабуляр
  (`SeriesEpisodeState`, `SeriesStatus`, `LookupResult`).

### Existing tests

- `app/src/test/java/.../data/models/MediaTypeFromCategoryTest.kt` — покрывает текущее
  схлопывание TV_SERIES, требует обновления под split.
- Тесты источников (`*ShikimoriTest`, `*AniListTest` и т.п.) — паттерн для будущих
  `TmdbRemoteDataSourceTest`/`KinopoiskRemoteDataSourceTest`.

### Constraints discovered

- Рабочее дерево на `main` содержит существенный объём **несвязанных** незакоммиченных правок
  (dock navigation, PiP controller, card menu, workspace UI) — НЕ трогать, не коммитить вместе
  с тикетами этой фичи.
- `BatchEpisodeCheckUseCase.kt`, `AnimeNotifier.kt`, `ui/home/updates/EpisodeUpdateStack.kt`,
  `worker/AnimeUpdateWorker.kt` уже несут незакоммиченные изменения — но эти изменения
  оказались релевантными (тот же анимешный auto-apply/update-feed пайплайн, который
  `SeriesEpisodeCheckUseCase` расширяет), не конфликтующим посторонним фичам. План уже
  построен поверх текущего (dirty) состояния этих файлов. Коммиты по тикетам, затрагивающим
  эти файлы, неизбежно захватят и эти предсуществующие изменения — фиксируется как известное
  отклонение от «чистого diff на тикет», не блокирует работу.
- Нет `CONTEXT.md`/`docs/adr/` в репозитории — по `docs/agents/domain.md` это ожидаемо
  («создаются лениво»), не создаются проактивно в рамках этой фичи если не возникнет реальной
  потребности зафиксировать устоявшийся домен-факт.
- `core/database` и `feature/statistics` — модули-заглушки (только манифест), реальная
  персистентность через SQLDelight в `app`.

### Questions answerable from code

- Как схлопнут MediaType сейчас → да, подтверждено чтением `Anime.kt` и тестом.
- Есть ли DTO-слой у TMDB → нет, подтверждено чтением `VetroApiService.kt`.
- Работает ли `RepairAnimeDbUseCase` вообще для MOVIE/SERIES → частично да (title-search путь),
  но id-часть сломана — подтверждено чтением `externalIdsFrom`.

### Remaining material uncertainties

- Точное место существующей точки вызова `BatchEpisodeCheckUseCase.detectAndStore`
  (периодический воркер vs ручная кнопка) — не локализовано в plan mode, оставлено на
  TICKET-08 (см. `worker/AnimeUpdateWorker.kt` как вероятного кандидата).
- Фактическая модель хранения названий (`titleEn`/`titleRu`) для MOVIE/SERIES записей —
  требует проверки в TICKET-05 (см. spec.md «Open questions»).

## 2026-08-09 — TICKET-01 завершён

### Outcome

DONE

### Work completed

`MediaType.TV_SERIES` → `MOVIE`/`SERIES` (enum + `fromCategoryType` legacy-алиас + новый
`fromPersistedValue` для границы десериализации), миграция 13 (data-сплит по `categoryType` +
4 новые колонки: `tmdb_id`/`kinopoisk_id`/`*_not_found_at`), `AnimeLocalDataSource` (мапперы,
4 новых сеттера), `ExternalIds`/`LookupResult` контракты в `core/network`, `SaveAnimeParams`/
`SaveAnimeUseCase` прокидывают новые поля, Home-фильтр (`HomeComponents`/`HomeScreen`/
`HomeViewModel`/`AnimeRepository.observeAnimeList`) обновлён под новый enum с сохранением
"одна плитка = MOVIE+SERIES"-поведения, `UiStrings.typeMovie` (extension property, не
constructor-поле), Supabase SQL-миграция подготовлена (не применена).

### Decisions made

- Sync (`SyncRepository`/`AnimeRemoteDto`) намеренно не тронут для push/pull новых id — до
  применения Supabase-миграции это сломало бы весь upsert. `upsertFromSync` тем не менее
  защищён self-select подзапросами (см. Deviations в тикете) — иначе REPLACE стирал бы то, что
  TICKET-02+ начнут писать локально.
- Home-фильтр остаётся одной комбинированной плиткой "Сериалы" (MOVIE+SERIES), а не двумя
  раздельными — чтобы не расширять фундаментальный тикет новой UI-фичей; комбинированное
  сопоставление реализовано in-memory в `AnimeRepository`.

### Deviations

См. `issues/01-mediatype-split-and-schema.md` → Deviations (upsertFromSync self-select fix,
добавленный Migration13Test).

### Root causes discovered

SQLDelight-файл `N.sqm` срабатывает на переходе version N → N+1 (не "до версии N"), что для
этого репозитория (файлы 2..13) даёт итоговую `Schema.version = 14`. Первая версия
`Migration13Test` использовала `migrate(driver, 12, 13)` и молча не запускала миграцию вовсе —
поймано тестом, не ручной проверкой.

### Verification

`./gradlew.bat :core:network:compileDebugKotlin :app:compileDebugKotlin` → зелёный.
`./gradlew.bat :app:testDebugUnitTest` → зелёный, `tests=352 failures=0 errors=0`.

### Review result

`code-review` skill (Standards + Spec, параллельно). Spec-ось нашла 2 реальные проблемы (обе
устранены): незащищённый `upsertFromSync` (риск данных), отсутствующий тест миграции вопреки
собственной TDD=REQUIRED классификации тикета. Standards-ось — 4 judgement-call находки, ни
одна не блокирующая (подробности в тикете).

### New risks

Нет новых, за пределами уже описанных в architecture review.

### Follow-up work

Cloud sync для tmdb_id/kinopoisk_id (см. MASTER_PLAN.md → Deferred work) — требует ручного
применения Supabase-миграции пользователем, затем код в `SyncRepository.kt`.

### Next eligible ticket

TICKET-02 и TICKET-03 (оба разблокированы TICKET-01, независимы друг от друга).
