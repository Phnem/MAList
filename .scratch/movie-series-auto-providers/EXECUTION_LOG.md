# Execution log — MOVIE/SERIES automatic provider cascade

## Initial codebase discovery

Date: 2026-08-10. Base commit: `dd20d8f`. Working tree clean, branch `main`.

### Relevant modules

- `app/.../media/source/` — playback source package.
  - `MovieSeriesPlaybackSource.kt` — current MOVIE/SERIES contract (`sourceName` + `resolve`),
    result type `Found | NoMatch | NotConfigured`, and `resolveMovieSeriesSources` fan-out.
  - `PlaybackProviderCascade.kt` — generic bounded-timeout fan-out; `supervisorScope` + `async`
    + `withTimeoutOrNull`; rethrows `CancellationException`; records `failed`, `timedOut`, `elapsedMs`.
  - `PlaybackResolution.kt` — `PlaybackIdentity`, `PlaybackRequest`, `PlaybackResolution`,
    `PlaybackRoute`, `PlaybackRoutingPolicy`.
  - `VetroModels.kt` — `VetroVideo`, `VetroHoster`, credential-ref/scope, header persistence
    allowlist, sensitive-query detection.
  - `VideoRanking.kt` — resolution-first ranking with a small hardcoded `reliabilityRank()`.
  - Personal sources: `DirectHttpPlaybackSource`, `WebDavPlaybackSource`,
    `PersonalMediaServerPlaybackSource` (Jellyfin/Emby), plus settings/credentials/probe services.
  - Anime sources: `AniLibriaSource`, `AnimeGoSource`, `JutSuSource`, `KodikSource`,
    `AnimeHeavenSource`, `CvhResolver`, `UrlSource`.
- `SourceEngine.kt` — dispatches strictly through `PlaybackRoutingPolicy`; `DirectOnly` route
  delegates to `resolveMovieSeriesSources`.
- `domain/seasons/StreamingSeasonDiscovery.kt`, `SeasonEpisodesResolver.kt` — season discovery.
- UI: `ModernDetailsEpisodesPage.kt`, `EpisodeMenuViewModel.kt`, `StreamWatchViewModel.kt`,
  `DownloadWizardViewModel.kt`, `StreamPlayerActivity.kt`.

### Existing behavior

- MOVIE/SERIES already routed away from anime-only adapters (`PlaybackRoute.DirectOnly`).
- The fan-out is already fault tolerant: one provider failure/timeout does not cancel siblings,
  and a later success wins over an earlier failure (`playbackResolution`).
- `VetroVideo.downloadAllowed` defaults to `false`; legacy anime sources no longer force it true.
- Results already normalize to `VetroHoster`/`VetroVideo` before reaching player/downloader.

### Existing terminology

`PlaybackIdentity`, `PlaybackRequest`, `PlaybackResolution`, `PlaybackRoute`, `VetroHoster`,
`VetroVideo`, `SeasonInfo`, `AppLanguage`, `MediaType`.

### Existing tests

`app/src/test/java/com/example/myapplication/media/source/` plus core/network suites.
Baseline at `dd20d8f`: 416 app unit tests, 55 core/network tests, 0 failures.

### Constraints discovered

- Download policy is global default-deny (`a5e6500`); must not be weakened.
- Secrets: encrypted store, `credentialRef`/`credentialScope`, header allowlist for persistence,
  no secrets in URL/logs/WorkManager.
- Released-only episode invariant on `Anime.episodes` must be preserved.
- ANIME resolution paths must not change behavior.

### Questions answerable from code (not asked)

- Where automatic providers plug in — `movieSeriesSources` in `SourceEngine`, behind
  `MovieSeriesPlaybackSource`.
- Whether a cascade/timeout/normalization layer already exists — yes, in basic form.
- Whether RU/EN separation exists for MOVIE/SERIES — no; `AppLanguage` reaches `PlaybackRequest`
  but `PlaybackRoutingPolicy` collapses both to `DirectOnly`.
- Architectural debts named in the task all confirmed present: `PlaybackRoute.DirectOnly`,
  `SourceEngine.resolveHosters`, `StreamingSeasonDiscovery`, `DownloadQuality` in a UI package.

### Remaining material uncertainties

- BLOCKING: the provider shortlist in the task brief (Collaps, HDVB, Filmix, Rezka, VidSrc,
  VidLink, Videasy, Embed.su, AutoEmbed) consists of unlicensed redistribution services. The
  previous stage of this same project already researched and formally excluded them
  (`.scratch/movie-series-playback/research/safe-playback-sources.md`, section "Что сознательно
  исключено"), and that exclusion is part of the accepted spec. Raised with the user before
  specification.

## 2026-08-10 — TICKET-01

### Outcome

DONE_WITH_DEVIATIONS

### Work completed

Новый пакет `media/source/movieseries/`: `MovieSeriesStreamingProvider`, `ProviderId`,
`ProviderCapability`, `ProviderResolution` (8 исходов), `ProviderApplicability`,
`providerResolutionForStatus`, `ProviderHttpException`, `requireProviderSuccess`, `resolveTyped`.
Каскад `resolveMovieSeriesSources` фильтрует по capabilities до сетевого вызова. Direct/WebDAV/
Jellyfin/Emby мигрированы.

### Decisions made

- Провайдер без объявленных RU/EN считается language-agnostic: персональная библиотека содержит
  то, что в неё положил пользователь, и отвечает для обоих языков.
- Capability `DOWNLOAD` — это потенциал, а не разрешение; фактическое право остаётся на
  `VetroVideo.downloadAllowed` (default-deny не ослаблен).
- `NotFound` не считается сбоем и не будет портить health в TICKET-06.

### Deviations

Добавлены `resolveTyped`/`requireProviderSuccess` сверх плана — без них AC по InvalidResponse и
5xx не выполнялись для Jellyfin/Emby.

### Root causes discovered

Нет.

### Verification

`gradlew :app:testDebugUnitTest --rerun-tasks` → 439 tests, 0 failures, 0 errors.
`gradlew :core:network:test` → 55 tests, 0 failures. `gradlew :app:assembleDebug` → SUCCESSFUL.
`git diff --check` → clean.

### Review result

Две BLOCKING находки самопроверки исправлены и покрыты тестами (WebDAV 404-root; типизация
ошибок Jellyfin/Emby).

### Architecture observations

Пункт A из INITIAL_REVIEW закрыт. `SourceEngine` не вырос. Пункты B/C остаются на TICKET-02/03.

### New risks

Нет.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-02 (RU/EN split) или TICKET-03 (IMDb ID) — независимы друг от друга.

## 2026-08-10 — Plan revision (post-TICKET-01, pre-TICKET-02/03)

### Trigger

Пользователь предложил заменить TICKET-07/08 исполняемым plugin-рантаймом для пользовательских
источников: «provider сам реализует получение MOVIE/SERIES», Vetro отвечает за
«sandbox, permissions, network isolation», без whitelist доменов.

### Decision

Отклонено. Это не декларативный формат, а локальное исполнение стороннего кода. В экосистемах
Stremio/Kodi именно такой универсальный «provider/plugin без whitelist» на практике становится
основным каналом доставки пиратских скрейперов через community-плагины — то есть воспроизводит
категорию, уже исключённую решением #1 (raised 2026-08-10, до TICKET-01), только через сторонний
код вместо кода Vetro.

Вместо plugin-рантайма: TICKET-07 (Vetro-манифест) расширен — многошаговые запросы (`resolveVia`:
lookup → stream), пагинация, retry/backoff, несколько auth-схем. Остаётся декларативным: без
исполнения кода, HTML-scraping, JS, iframe, анти-бот примитивов. TICKET-08 (Stremio-импорт)
остаётся основным каналом для «сервис сам решает, как искать поток» — но логика живёт на сервере
аддона, не в коде, исполняемом внутри Vetro.

Пользователь согласился с этой альтернативой после объяснения.

### Secondary change

TICKET-11 (Internet Archive/Wikimedia) понижен пользователем до DEFERRED: узкий охват public-domain
контента, почти не двигает основной сценарий. Не блокирует TICKET-13.

### Artifacts updated

`MASTER_PLAN.md` (ticket table, TICKET-07/11 details, Decisions #7-8), `spec.md` (functional
requirements, out of scope, open questions), `research/MOVIE_SERIES_PROVIDER_RESEARCH.md` (транспорт
1 — уточнение и расширенный пример манифеста), `CURRENT_HANDOFF.md`.

### Outcome

READY_FOR_IMPLEMENTATION. Next eligible ticket unchanged: TICKET-02 or TICKET-03.

## 2026-08-10 — TICKET-03

### Outcome

DONE_WITH_DEVIATIONS

### Work completed

Миграция `15.sqm` (`anime.imdb_id TEXT`), проброс через `Anime`, `PlaybackIdentity`,
`PlaybackRequest`, `AnimeLocalDataSource` (+`setImdbId`), `AnimeInsertion`. Сеть: `ExternalIds.imdb`,
`AnimeDetails.imdbId`, `TmdbMovieDetailsDto.imdb_id`, `TmdbExternalIdsDto` +
`append_to_response=external_ids` для TV.

### Decisions made

- TEXT, не INTEGER: `tt0412142` теряет смысл как число.
- Колонка локальная (прецедент `tmdb_id`), переносится через pull self-select'ом.
- Пустая строка → null и в сети, и в `setImdbId`.

### Deviations

Исправлен `Migration13Test`: он читал наполовину мигрированную БД через сгенерированные запросы,
которые всегда описывают последнюю схему. Переведён на raw SQL.

### Root causes discovered

`Migration13Test` был системно хрупок — ломался бы на любой следующей миграции, не только на этой.

### Verification

app 444 tests / core-network 60 tests, 0 failures; debug APK собирается; `git diff --check` чист.
Число тестов `TmdbImdbIdTest` подтверждено по XML-отчёту.

### Review result

Одна регрессия поймана прогоном и исправлена.

### Architecture observations

Пункт C из INITIAL_REVIEW закрыт. TICKET-04 и TICKET-08 разблокированы.

### New risks

Нет.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-02.

## 2026-08-10 — TICKET-02

### Outcome

DONE

### Work completed

`PlaybackRoute.DirectOnly` → `MovieSeriesRu`/`MovieSeriesEn`; `PlaybackRoutingPolicy` на
исчерпывающем `when (mediaType)`; `movieSeriesLanguage`; извлечена чистая
`selectMovieSeriesProviders`; `SourceEngine` диспатчит по новым маршрутам.

### Decisions made

- Исчерпывающий `when` вместо `else`: новый media type должен ломать компиляцию, а не тихо
  становиться `None`.
- Персональные источники остаются language-agnostic.

### Deviations

Нет.

### Root causes discovered

Нет.

### Verification

app 452 tests, 0 failures; core/network 60; debug APK собирается; `DirectOnly` в коде отсутствует.

### Review result

Блокирующих находок нет.

### Architecture observations

Пункт B из INITIAL_REVIEW закрыт. Порядок/ранжирование намеренно оставлены TICKET-05.

### New risks

Нет.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-04 (shared ID-first matcher) — зависимости 01 и 03 закрыты.

## 2026-08-10 — TICKET-04

### Outcome

DONE

### Work completed

`MediaIdentityMatcher` + `MediaIdentity` + `MatchAccuracy` + `IdentityMatch`; общие
`normalizeTitle`/`normalizeImdbId`/`episodeMatches`. Jellyfin/Emby переведены на общий matcher.

### Decisions made

- Ранжирование задаётся порядком объявления enum, а не отдельной таблицей весов.
- Конфликтующий ID дисквалифицирует кандидата даже при совпадении другого ID.
- Рассматривается только сильнейший уровень совпадений.
- Одинаковое название в другом году — отказ (ремейки).

### Deviations

Нет.

### Root causes discovered

Нет.

### Verification

app 472 tests, 0 failures; core/network 60; debug APK собирается. 10 тестов Jellyfin/Emby не
менялись и проходят — подтверждение отсутствия регресса.

### Review result

Блокирующих находок нет.

### Architecture observations

Пункт D из INITIAL_REVIEW закрыт. `MatchAccuracy` готов к использованию в ранжировании TICKET-05.

### New risks

Нет.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-05 (multi-candidate + ranking) или TICKET-06 (health). TICKET-05 логичнее: он потребляет
`MatchAccuracy`, а TICKET-06 затем подключается к уже существующему слою ранжирования.

## 2026-08-10 — TICKET-05

### Outcome

DONE_WITH_DEVIATIONS

### Work completed

`MovieSeriesCandidate`, `MovieSeriesRanking`, `buildCandidates`, `toHosters`; `ProviderResolution.Found`
получил опциональные `accuracy`/`language`; каскад строит, ранжирует и перегруппировывает кандидатов.

### Decisions made

- Порядок — явная цепочка сравнений, не единый скрытый скор.
- Отсутствующая точность = слабейшая улика.
- Служебные поля живут на кандидате, а не на `VetroVideo`.
- `healthPenalty` инжектируется, чтобы TICKET-06 не переделывал ранжирование.

### Deviations

`reliabilityRank()` НЕ удалён: `VideoRanking` общий с аниме, и его изменение нарушило бы раздел 23.
Новый слой действует только на MOVIE/SERIES.

### Root causes discovered

Round-trip кандидатов подставлял `hoster.url` из URL видео — выдумывание данных за провайдера.
Исправлено дословным переносом `hosterUrl`.

### Verification

app 486 tests, 0 failures; core/network 60; debug APK собирается.

### Review result

Одна находка (`hosterUrl`) исправлена; изменение атрибуции `sourceName` признано желательным и
закреплено в тестах.

### Architecture observations

Пункт E из INITIAL_REVIEW закрыт для MOVIE/SERIES; для ANIME осознанно оставлен.

### New risks

Нет.

### Follow-up work

Удаление `reliabilityRank` — только вместе с отдельным пересмотром ANIME-ранжирования.

### Next eligible ticket

TICKET-06 (health) — подключается к готовому `healthPenalty`.
