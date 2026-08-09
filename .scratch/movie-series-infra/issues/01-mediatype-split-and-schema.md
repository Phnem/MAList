# TICKET-01: Разделение MediaType.TV_SERIES → MOVIE/SERIES + схема БД

## Status

DONE

## Objective

Разделить `MediaType.TV_SERIES` на `MOVIE`/`SERIES` с legacy-совместимым парсингом на всех
границах, и добавить в схему БД поля под `tmdbId`/`kinopoiskId` + `ExternalIds`/`LookupResult`
контракты в `core/network`. Чисто enabling-тикет — без него ни один следующий не начинается.

## User or system value

Сейчас после добавления фильм и сериал неразличимы (`mediaType == TV_SERIES` для обоих), и
негде сохранить `tmdbId`/`kinopoiskId` — повторный точечный запрос к TMDB/Kinopoisk невозможен
в принципе. Это блокирует всё остальное: Details-refresh, gap-детектор, episode-tracking.

## Dependencies

—

## Scope

- `app/src/main/java/com/example/myapplication/data/models/Anime.kt` — `MediaType` enum:
  заменить `TV_SERIES` на `MOVIE`, `SERIES`; `fromCategoryType` — `"MOVIE"→MOVIE`,
  `"SERIES"/"TV"→SERIES`, legacy `"TV_SERIES"→SERIES`; новый `fromPersistedValue(raw: String): MediaType`.
  Новые поля на `Anime`: `tmdbId: Int? = null`, `kinopoiskId: Int? = null`,
  `tmdbNotFoundAt: Long? = null`, `kinopoiskNotFoundAt: Long? = null`.
- `app/src/main/sqldelight/com/example/myapplication/data/local/migrations/13.sqm` — data-миграция
  `TV_SERIES → MOVIE/SERIES` по `categoryType` (дефолт `SERIES` для неоднозначных) + `ALTER TABLE`
  на 4 новые колонки.
- `app/src/main/sqldelight/com/example/myapplication/data/local/Anime.sq` — `CREATE TABLE`,
  оба `getAllAnimeWithTagsConcat`/`getAnimeWithTagsConcatByType`, `insertAnime`, `updateAnime`,
  `updateAnimeFromSync`, `updateAnimeKeepingDirty`, `upsertFromSync` + новые
  `setTmdbId`/`setKinopoiskId`/`markTmdbNotFound`/`markKinopoiskNotFound`.
- `AnimeLocalDataSource` — мапперы (используя `MediaType.fromPersistedValue`, не `valueOf`) +
  новые сеттеры.
- **Найти и проверить каждое место, где `mediaType` парсится как уже сохранённое значение**
  (SQLDelight-маппер — уже покрыт выше; sync upload/download; backup/import, если есть) —
  везде заменить прямой `valueOf`/enum-десериализацию на `fromPersistedValue`.
- `SaveAnimeParams`/`SaveAnimeUseCase` — прокинуть 4 новых поля.
- `core/network/src/main/java/com/example/myapplication/network/ApiSearchResult.kt` — новый
  `data class ExternalIds(anilist, mal, shikimori, tmdb, kinopoisk: Int? = null)`, поле
  `externalIds: ExternalIds = ExternalIds()` на `ApiSearchResult` (аддитивно, старые поля не
  трогать).
- Новый файл `core/network/.../network/LookupResult.kt`:
  `sealed interface LookupResult<out T> { Found<T>; NoMatch; NotFoundById; Failure(cause, retryable) }`.
- Представительные места, ссылающиеся на старый `MediaType.TV_SERIES` (заменить на
  `MOVIE`/`SERIES` или `mediaType in {MOVIE, SERIES}`): `ui/home/HomeComponents.kt`,
  `ui/details/DetailsScreen.kt`, `sync/DuplicateTitleRule.kt`. Остальные — через
  `grep -r "MediaType.TV_SERIES"` по всему репозиторию.
- `data/models/MediaTypeFromCategoryTest.kt` — обновить/расширить.

## Out of scope

- Любая сетевая логика TMDB/Kinopoisk (TICKET-02/03).
- Использование `ExternalIds`/`LookupResult` в реальном коде (следующие тикеты) — здесь только
  контракты.
- Repair/gap-detector изменения (TICKET-07).

## Acceptance criteria

- [ ] `MediaType` enum не содержит `TV_SERIES`; содержит `MOVIE`, `SERIES`.
- [ ] `MediaType.fromCategoryType("TV_SERIES")` возвращает `SERIES` (legacy-алиас).
- [ ] `MediaType.fromPersistedValue` существует, используется во всех местах чтения
      сохранённого `mediaType` (SQLDelight-маппер минимум; sync/backup — если существуют такие
      пути).
- [ ] Миграция 13 на копии реальной/тестовой БД: старые `TV_SERIES` с `categoryType='MOVIE'`
      или `'SERIES'` разделяются корректно; неоднозначные становятся `SERIES`; данные не
      теряются.
- [ ] `ApiSearchResult.externalIds` компилируется и не ломает ни одного существующего читателя
      старых полей (`externalId`, `malId`).
- [ ] `LookupResult` — компилируется, exhaustive `when` без `else` возможен на всех 4 вариантах.
- [ ] `grep -r "MediaType.TV_SERIES"` по репозиторию (кроме этого тикета/плана/спеки) — пусто.
- [ ] `MediaTypeFromCategoryTest` зелёный с новыми кейсами.
- [ ] `./gradlew :core:network:compileDebugKotlin :app:compileDebugKotlin` зелёный.

## Verification plan

```
./gradlew :core:network:compileDebugKotlin
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*MediaType*"
```

Ручная проверка миграции: временно вставить в тестовую БД строку с
`mediaType='TV_SERIES', categoryType='MOVIE'` и одну с `categoryType=''`, прогнать миграцию,
убедиться в ожидаемом результате (`MOVIE` и `SERIES` соответственно).

## TDD classification

REQUIRED — `fromCategoryType`/`fromPersistedValue` это ровно "parsers"/"data merging" из
критериев обязательного TDD в ticket-autopilot; миграция — тоже требует детерминированного теста
на нескольких legacy-состояниях до реализации.

## Expected architecture impact

Заявленный в architecture review REQUIRED_BEFORE_IMPLEMENTATION блок — `ExternalIds`/
`LookupResult` контракты, plus сам enum-split. Локальный, без структурных сюрпризов за
пределами перечисленного scope.

## Risks

- Пропущенное место чтения `mediaType` как persisted-значения (не через
  `fromPersistedValue`) упадёт с `IllegalArgumentException` на старых данных/старом клиенте
  синка — читать sync-код внимательно, не полагаться только на grep по имени enum-константы
  (может быть `MediaType.valueOf(...)` без явного упоминания `TV_SERIES`).
- Миграция 13 — необратимая операция на реальных пользовательских БД; тестировать на копии,
  не на проде.

## Implementation notes

- `MediaType.fromCategoryType`/`fromPersistedValue` — `fromPersistedValue` normalizes input
  (`trim().uppercase()`) before both the legacy-alias check and `valueOf`, so it's tolerant of
  incidental casing, not just the exact stored form.
- Миграция 13 — два `UPDATE` вместо одного `CASE`: сначала переносит однозначные `categoryType`
  (`MOVIE`/`SERIES`), затем всё оставшееся `TV_SERIES` дефолтит на `SERIES`. Читается яснее, чем
  один `CASE WHEN`.
- **Home-фильтр** (`ui/home/HomeComponents.kt` + `AnimeRepository.observeAnimeList`) — единая
  плитка "Сериалы" (`MediaType.SERIES`) сохраняет поведение "показывает фильмы и сериалы
  одновременно" через in-memory доотфильтровку (`dbFilterType = null` при выборе SERIES, затем
  `list.filter { MOVIE || SERIES }`), а не через DB-уровня exact-match. Отдельной плитки под
  MOVIE не добавлено — сознательно, чтобы не расширять скоуп фундаментального тикета новой
  UI-фичей; поисковый фильтр (`HomeViewModel`/`HomeScreen`'s `searchMediaTypeFilter`) по той же
  причине тоже маппит единственную "Сериалы"-опцию на `AppContentType.SERIES` — раздел MOVIE
  через Home-поиск пока не достижим (тот же предсуществующий разрыв, что был у TV_SERIES).
- **`typeMovie`** добавлен как computed extension-property в `UiStrings.kt` (по образцу уже
  существующих `typeManga`/`typeSeries`), НЕ как поле конструктора — `UiStrings` уже около
  267 `val`-полей, документированный проектный лимит (255) уже под угрозой, новых
  constructor-полей избегать.
- **Sync намеренно не тронут** для `tmdb_id`/`kinopoisk_id` push/pull (`SyncRepository.kt`,
  `AnimeRemoteDto`) — соответствующих колонок ещё нет на живом Supabase-проекте; включать push
  раньше применения `supabase/migrations/20260809000000_anime_tmdb_kinopoisk_ids.sql` уронило
  бы весь upsert («Could not find the column…») для всех synced-пользователей.

## Deviations

- **Planned**: `updateAnimeFromSync`/`upsertFromSync` явно перечислены в Scope тикета как
  требующие правки наравне с остальными query.
  **Actual**: обе оставлены БЕЗ новых столбцов в списке колонок/параметров — но `upsertFromSync`
  получил self-select-подзапросы (`(SELECT tmdb_id FROM anime WHERE id = :id)` и аналоги),
  сохраняющие текущее локальное значение через `INSERT OR REPLACE`, вместо приёма нового
  параметра.
  **Reason**: обнаружено ревью (Spec-axis) — `upsertFromSync` вызывается из
  `SyncRepository.pullRemoteChanges()` через `INSERT OR REPLACE`, которое переписывает строку
  ЦЕЛИКОМ; столбец, отсутствующий в списке INSERT, стал бы `NULL` при первом же pull после
  того, как TICKET-02+ начнут писать туда реальные значения — это тихо стирало бы
  `tmdb_id`/`kinopoisk_id` при любой синхронизации. `updateAnimeFromSync` оставлен как есть
  (используется тем же файлом, но, в отличие от `upsertFromSync`, это `UPDATE ... SET`, не
  `REPLACE` — отсутствующие в SET-списке столбцы не трогаются вообще, значит риска нет; плюс
  вызывающий Kotlin-код (`SyncRepository.kt`) не может передать значения, которых нет в
  `AnimeRemoteDto`, — трогать эту query сейчас значило бы либо оставить параметр висящим без
  реального значения, либо начать пробрасывать `AnimeRemoteDto`-поля, которых пока нет).
  **Consequence**: `tmdb_id`/`kinopoisk_id` остаются chisto локальными (не синкаются между
  устройствами) до отдельного follow-up (применить SQL-миграцию на Supabase + добавить поля в
  `AnimeRemoteDto`/оба маппинга) — это уже было предусмотрено спекой как Out of scope, но
  конкретный механизм защиты от порчи данных при этом пришлось добавить сверх изначально
  описанного в Scope тикета.
  **Follow-up**: зафиксировано в MASTER_PLAN → Deferred work.
- **Planned**: не упоминалось.
  **Actual**: добавлен `Migration13Test.kt` + новая test-only зависимость
  `app.cash.sqldelight:sqlite-driver` (алиас `sqldelight-sqlite-driver` в version catalog).
  **Reason**: Spec-ревью отметило, что ticket сам заявляет TDD=REQUIRED для миграции
  («миграция — тоже требует детерминированного теста на нескольких legacy-состояниях»), но
  реализация изначально не включала такой тест — только ручной шаг в Verification plan.
  **Consequence**: тест поймал реальную ошибку в понимании SQLDelight-версионирования (файл
  `N.sqm` срабатывает на переходе version N → N+1, не "к версии N" — так что вызов
  `Schema.migrate(driver, 12, 13)` не запускал `13.sqm` вовсе; исправлено на `(13, 14)`).
  Без этого теста расхождение осталось бы незамеченным до первого реального прогона на
  устройстве.
  **Follow-up**: нет — тест самодостаточен.

## Review findings

`code-review` skill, две параллельные оси (Standards/Spec), диффы просмотрены с учётом
энтанглмента `HomeScreen.kt`/`UiStrings.kt` с несвязанными незакоммиченными правками (см.
`EXECUTION_LOG.md`).

**Standards** — 4 находки, все judgement call, не блокирующие: (1) `Repeated Switches` —
`mediaTypeLabel` when-блок дублирован в 2 местах `HomeScreen.kt` + аналогичный в
`HomeViewModel.kt`; принято как есть (соответствует уже существующему до этого тикета
паттерну, схлопывание — отдельная работа вне скоупа). (2) `Primitive Obsession/Data Clump` —
`ExternalIds` не переиспользован для `Anime`-домен-модели, там остаются плоские поля;
осознанное решение из плана (SQLDelight — не документная БД, отдельные колонки естественны),
не случайность. (3) `Duplicated Code` — новые сеттеры (`setTmdbId` и т.д.) копируют форму
`setShikimoriId`; соответствует явному указанию в Scope тикета ("копия setAnilistId"). (4)
`Divergent Change` в `UiStrings.kt` (удаление `notifAccept`/`notifDecline` полей) — проверено:
это НЕ мой diff, часть предсуществующих несвязанных незакоммиченных правок в рабочем дереве
(см. `EXECUTION_LOG.md` → Constraints discovered); мой единственный вклад в этот файл —
добавление `typeMovie` extension property.

**Spec** — 2 находки, обе устранены (см. Deviations выше): (a) `upsertFromSync` без защиты от
затирания новых id при sync-pull — **исправлено** self-select подзапросами; (b) отсутствие
теста на миграцию 13 вопреки собственной TDD=REQUIRED классификации тикета — **исправлено**,
добавлен `Migration13Test.kt` (5 тестов). Ни одной находки о scope creep за пределами
`UiStrings.kt`-энтанглмента (не мой diff, см. выше).

Блокирующих замечаний не осталось после исправлений.

## Completion evidence

- Command: `./gradlew.bat :core:network:compileDebugKotlin :app:compileDebugKotlin` →
  `BUILD SUCCESSFUL`
- Command: `./gradlew.bat :app:testDebugUnitTest` → `BUILD SUCCESSFUL`, агрегат по всем
  тест-сьютам: `tests=352 failures=0 errors=0` (было 347 до этого тикета, +5 новых тестов
  `Migration13Test`; `MediaTypeFromCategoryTest` расширен новыми кейсами в рамках тех же 8
  тестов файла).

Файлы (только относящиеся к этому тикету — рабочее дерево содержит и другие несвязанные
незакоммиченные правки, см. `EXECUTION_LOG.md`):
`data/models/Anime.kt`, `data/local/AnimeLocalDataSource.kt`,
`sqldelight/.../Anime.sq`, `sqldelight/.../migrations/13.sqm`,
`data/repository/AnimeRepository.kt`, `domain/addedit/SaveAnimeParams.kt`,
`domain/addedit/SaveAnimeUseCase.kt`, `data/models/UiStrings.kt` (только `typeMovie`),
`ui/home/HomeScreen.kt` (только 3 точечных hunk'а), `ui/home/HomeViewModel.kt`,
`ui/home/HomeComponents.kt`, `data/models/MediaTypeFromCategoryTest.kt` (новый),
`data/local/migrations/Migration13Test.kt` (новый),
`core/network/.../ApiSearchResult.kt`, `core/network/.../LookupResult.kt` (новый),
`supabase/migrations/20260809000000_anime_tmdb_kinopoisk_ids.sql` (новый, не применена к живому
проекту — см. Deviations), `gradle/libs.versions.toml`, `app/build.gradle.kts` (новая
test-зависимость).

Не выполнено (за пределами возможностей этой сессии): применение
`supabase/migrations/20260809000000_anime_tmdb_kinopoisk_ids.sql` на живом Supabase-проекте —
требует доступа к Dashboard, за пользователем.
