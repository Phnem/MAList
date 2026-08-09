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

## 2026-08-09 — TICKET-02 завершён

### Outcome

DONE_WITH_DEVIATIONS

### Work completed

`TmdbDto.kt` (типизированные DTO поиска/деталей/сезонов), `TmdbModels.kt`
(`TmdbSeasonSummary`/`TmdbEpisodeAirDate`/`SeriesStatus`/`SeriesEpisodeState`),
`TmdbEpisodeCalculator` (чистая released/known-логика + маппер статуса, 9 TDD-тестов),
`TmdbRemoteDataSource` (search/details/season/episodeState, HTTP→`LookupResult` через единый
`runRequest`). MOVIE использует `/search/movie`/`/movie/{id}` с первого дня — старый баг
`/search/tv`-для-MOVIE не воспроизведён в новом коде.

### Decisions made

TDD-бюджет направлен на `TmdbEpisodeCalculator` (чистая логика, самый рискованный участок по
итогам архитектурного ревью плана), а не на HTTP-слой — у `core/network` не было тестовой
инфраструктуры вообще, и полноценный Ktor `MockEngine`-харнесс — отдельный по объёму кусок
работы. Осознанное сужение TDD-обязательства тикета, зафиксировано как Deviation.

### Root causes discovered

Нет (в отличие от TICKET-01, здесь не было skeletons-в-коде сюрпризов — только заранее
известный компромисс по объёму тестового покрытия).

### Verification

`compileDebugKotlin` (оба модуля) + `testDebugUnitTest` (оба модуля) — зелёные.
`TmdbEpisodeCalculatorTest` 9/9.

### Review result

Самопроверка (без отдельного `/code-review` прогона — эффективно продолжение сессии TICKET-01).

### New risks

HTTP-слой `TmdbRemoteDataSource` не верифицирован живым/замоканным TMDB-ответом — реальная
форма JSON может разойтись с DTO-предположениями (например, отсутствующие поля, неожиданные
`null`). Проявится либо на ручной smoke-проверке в TICKET-04, либо на follow-up
mock-тестировании.

### Follow-up work

Ktor `MockEngine` тесты для `TmdbRemoteDataSource` (и будущего `KinopoiskRemoteDataSource`,
TICKET-03) — см. MASTER_PLAN.md → Deferred work.

### Next eligible ticket

TICKET-03 (независим от TICKET-02, разблокирован TICKET-01).

## 2026-08-09 — TICKET-03 завершён

### Outcome

DONE_WITH_DEVIATIONS

### Work completed

`KinopoiskDto.kt`, `KinopoiskMappers.kt` (чистые мапперы + `KinopoiskDetails`, 8 TDD-тестов —
включая явную проверку `externalId.tmdb`-моста), `KinopoiskRemoteDataSource`
(`searchMovie`/`searchSeries` типизированы по контенту на уровне сигнатуры, `details`),
`KINOPOISK_API_KEY` в `core/network/build.gradle.kts` + документирован в
`local.properties.example` (заодно — `TMDB_API_KEY`, который был не задокументирован с
TICKET-02).

### Decisions made

Тот же TDD-компромисс, что TICKET-02: чистые функции покрыты тестами, HTTP I/O — нет (единый
follow-up на оба источника сразу).

### Verification

`compileDebugKotlin`/`testDebugUnitTest` (`core:network`) зелёные, `app:compileDebugKotlin`
зелёный (не затронут).

### Follow-up work

См. TICKET-02 (Ktor MockEngine) — общий для обоих источников.

### Next eligible ticket

TICKET-04 (MovieSeriesRepository) — теперь разблокирован полностью (01, 02, 03 готовы).

## 2026-08-09 — TICKET-04 завершён

### Outcome

DONE

### Work completed

`MovieSeriesRepository` стал единственной точкой оркестрации TMDB+Kinopoisk для MOVIE/SERIES:
RU/EN routing, fill-gap merge, консервативный дедуп, details, `resolveTmdbId`, released-only
`findTotalEpisodes`/`episodeState`. `VetroApiService` делегирует поиск/число серий и больше не
содержит inline-TMDB код. DI зарегистрировал новые data source/repository.

### Decisions made

Тестовый seam оставлен internal: production-конструктор принимает реальные data source, тесты
— два маленьких gateway adapter. Общий `MovieTitleMatcher` живёт рядом с orchestration module,
порог 0.85 и правила эквивалентны существующему app `TitleMatcher`, но зависимости `core → app`
нет.

### Deviations

Для выполнения original-title ступени добавлено явное `ApiSearchResult.originalTitle` и поля
TMDB DTO `original_title`/`original_name`; исходный черновик пытался использовать только
`title`/`altTitle`, что ревью признало недостаточным.

### Verification

`./gradlew.bat :core:network:testDebugUnitTest :app:testDebugUnitTest
:core:network:compileDebugKotlin :app:compileDebugKotlin` → BUILD SUCCESSFUL, 383/383 теста.
Boundary grep, удаление inline-TMDB и `git diff --check` подтверждены отдельно.

### Review result

Двухосевое code-review: два spec BLOCKING + один standards IMPORTANT исправлены; повторное
ревью подтвердило RESOLVED и отсутствие новых блокеров.

### Architecture observations

Новый module глубокий: вся изменчивая source/fallback/dedup логика скрыта за малым интерфейсом,
`app` модели и локальная БД не протекли в `core/network`.

### Next eligible ticket

TICKET-05 (Details wiring) и TICKET-06 (add/dedup) разблокированы; следующий — TICKET-05.

## 2026-08-09 — TICKET-05 завершён

### Outcome

DONE

### Work completed

Details lookup для MOVIE/SERIES проведён end-to-end через единый `DetailsLookupRequest`:
`DetailsViewModel → AnimeRepository → ApiService → VetroApiService → MovieSeriesRepository`.
External ids и content type сохраняются, legacy запись резолвится по title, EN anime guard не
блокирует фильмы/сериалы.

### Decisions made

SERIES details всегда возвращает `episodesAired=0`/`episodesTotal=null`; этот UI-refresh не
является владельцем released episode count. Счётчик остаётся только в stored `Anime.episodes`
и будет обновляться TICKET-08.

### Deviations

Два новых параметра не добавлены к длинной сигнатуре по плану буквально; вместо этого весь
lookup bundle оформлен value object, что устранило data clump без изменения поведения.

### Verification

Полные `core:network` + `app` unit tests: 401/401, compilation обоих модулей, diff check.

### Review result

Двухосевое ревью и повторная проверка после исправлений: обязательных находок не осталось.

### Known limitations

Legacy RU запись без `titleEn` показывает RU fallback-заголовок и в EN режиме, но не теряет
название и получает EN details. Заполнение обеих локалей при добавлении — TICKET-06.

### Next eligible ticket

TICKET-06 — AddFromApiUseCase и дедуп по ExternalIds.

## 2026-08-09 — TICKET-06 завершён

### Outcome

DONE

### Work completed

Добавление MOVIE/SERIES сохраняет оба catalog id, обе доступные локали и строго `episodes=1`.
Save/duplicate probe используют одну identity projection. Локальный duplicate rule учитывает
TMDB/Kinopoisk, canonical conflicts и absorb новых id.

### Decisions made

Локаль больше не угадывается по алфавиту. TMDB mapper отмечает язык запроса, Kinopoisk
использует `name`/`enName`, а RU search делает дополнительный TMDB EN lookup и fill-gap merge.

### Deviations

RU search получил второй TMDB запрос ради корректного titleEn; это увеличивает один поиск, но
устраняет систематически неверные locale aliases и соответствует требованию заполнить обе
доступные локали.

### Verification

Полные unit tests `core:network` + `app`: 412/412; compilation обоих модулей; diff check.

### Review result

Двухосевое ревью: BLOCKING locale inference и IMPORTANT duplication исправлены; финальное
повторное ревью подтвердило отсутствие блокеров.

### Next eligible ticket

TICKET-07 — gap detector + repair MOVIE/SERIES.

## 2026-08-09 — TICKET-07 завершён

### Outcome

DONE

### Work completed

Gap detector теперь видит ANIME/MOVIE/SERIES, а repair дозаполняет TMDB/Kinopoisk ids,
локализованные названия и обычные metadata gaps. Critical TMDB и optional Kinopoisk имеют
разную retry-семантику; provider `Failure` не превращается в not-found и не попадает в generic
journal. Сохранённые id валидируются, stale id очищается только по `NotFoundById` и сразу
резолвится заново.

### Decisions made

Provider-id gaps управляются DB timestamps/`LookupResult`, не файловым gap journal. Полный
repair включает записи с сохранёнными movie ids для их валидации. SERIES episode count остаётся
под исключительным владением TICKET-08; repair его не меняет.

### Deviations

`LiveMaintenanceWorker` пришлось изменить локально, чтобы он журналировал только legacy field
gaps. Также исправлена обнаруженная граница сохранения: `updateAnime` пишет title locales, а
AddEdit сохраняет movie provider ids/timestamps.

### Verification

Полные unit tests `core:network` + `app`: 426/426; `app:assembleDebug`; `git diff --check`.

### Review result

Двухосевое ревью: оба Spec BLOCKING и все Standards/coverage IMPORTANT устранены; финальные
повторные проверки не нашли новых обязательных замечаний.

### Next eligible ticket

TICKET-08 — SeriesEpisodeCheckUseCase.

## 2026-08-09 — TICKET-08 завершён

### Outcome

DONE_WITH_DEVIATIONS

### Work completed

Добавлен `SeriesEpisodeCheckUseCase`: он проверяет все SERIES, получает и сохраняет только
`releasedEpisodes`, молча нормализует legacy-значение при первом успешном проходе и затем
создаёт обычные `AnimeUpdate` при росте. Протухший TMDB id восстанавливается через title
resolve; сетевой `Failure` не очищает id и не меняет локальное состояние.

Новые SERIES рождаются нормализованными: `AnimeDatabase.insertNewAnime(anime)` сам определяет
media type и атомарно вставляет запись вместе с marker в `series_episode_normalization`.
Миграция 14 создаёт marker-table без timestamp; импортированные/мигрированные legacy-записи
остаются без marker и проходят одноразовую нормализацию.

Общая политика merge/dedup/auto-apply ленты вынесена в `EpisodeUpdateFeed`, а
`EpisodeUpdateCheckCoordinator` последовательно запускает anime и SERIES проверки из ручного
Home trigger и фонового Worker.

### Verification

Полные unit tests `core:network` + `app`: 435/435 (51 + 384), failures=0, errors=0;
`:app:assembleDebug`; `git diff --check`. Code commit: `1777d36`.

### Review result

Исправлены BLOCKING по маркировке новых SERIES и IMPORTANT по дублированию feed/orchestration;
production insertion seam покрыт in-memory SQLite тестом. Финальные Spec и Standards re-review
не нашли нерешённых BLOCKING/IMPORTANT.

### Deviations and manual checks

Реальная проверка ongoing SERIES, уведомления на устройстве и live TMDB/Kinopoisk не запускались;
эквивалентное поведение покрыто детерминированными тестами и чтением consumers. Ktor MockEngine
и применение Supabase migration остаются записанными follow-up.

### Next eligible ticket

Нет — TICKET-01…08 завершены.

## 2026-08-09 — финальный feature checkpoint

Spec review принял реализацию requirement-by-requirement. Architecture/Standards checkpoint
подтвердил, что `MovieSeriesRepository` остаётся глубоким `core/network` модулем без app/DB
зависимостей, `LookupResult` сохраняет различия outcome, а SERIES episode ownership не дрейфовал.
Обязательных замечаний нет; feature workflow переведён в COMPLETE.
