# TICKET-01: Typed provider outcomes + capability contract

## Status

DONE

## Objective

Ввести capability-aware contract `MovieSeriesStreamingProvider` и типизированный `ProviderResolution`
с полным набором исходов из раздела 16 задания, заменив трёхсостоянийный `MovieSeriesSourceResult`.

## User or system value

Каскад начинает различать «фильма нет у источника», «источник временно лёг», «rate limit»,
«заблокировано», «битый ответ» и «не поддерживается». Это предусловие для health (TICKET-06),
ranking (TICKET-05) и внятных сообщений в picker UI (TICKET-10).

## Dependencies

None.

## Scope

- Новый пакет `media/source/movieseries/`.
- `MovieSeriesStreamingProvider`: `id`, `capabilities`, `suspend resolve(request)`.
- `ProviderCapability` enum: MOVIE, SERIES, RU, EN, DIRECT, HLS, SUBTITLES, MULTI_AUDIO, DOWNLOAD,
  TMDB_ID, IMDB_ID, KINOPOISK_ID.
- `ProviderResolution` sealed: Found, NotFound, TemporaryError, Blocked, RateLimited,
  InvalidResponse, Unsupported, NotConfigured.
- Capability-фильтр применимости до сетевого вызова.
- Адаптация Direct/WebDAV/Jellyfin/Emby к новому contract без изменения поведения.
- `resolveMovieSeriesSources` переводится на новые исходы, сохраняя текущий внешний результат.

## Out of scope

Ranking, health, RU/EN split, пользовательские источники, UI.

## Acceptance criteria

- [x] Провайдер объявляет capabilities; неприменимый не вызывается по сети.
- [x] Все восемь исходов представимы и различимы в каскаде.
- [x] HTTP 404 → NotFound, 429 → RateLimited, 5xx → TemporaryError, 403 → Blocked,
      битый JSON → InvalidResponse, таймаут → TemporaryError.
- [x] `CancellationException` пробрасывается.
- [x] Внешний `PlaybackResolution` для существующих сценариев не изменился.
- [x] Существующие тесты personal sources зелёные.

## Verification plan

Новые unit-тесты на applicability и на маппинг исходов; прогон существующих
`media.source.*` тестов; компиляция.

## TDD classification

REQUIRED

## Expected architecture impact

Устраняет пункт A из INITIAL_REVIEW. Создаёт пакет, в котором прячется вся новая сложность.

## Risks

Затрагивает четыре работающих адаптера — регресс personal sources.

## Implementation notes

Новый пакет `media/source/movieseries/` с contract, capabilities, исходами, чистой политикой
применимости и маппингом HTTP-статусов. Добавлены `ProviderHttpException` + `requireProviderSuccess`
+ `resolveTyped`: вложенные помощники адаптера теперь могут прервать резолв типизированным исходом,
а не анонимным броском, который каскад мог записать только как безымянный сбой.

Четыре адаптера мигрированы. `NoMatch` → `NotFound`; неверный media type → `Unsupported`
(раньше `NoMatch`).

## Deviations

- Planned: механическая миграция четырёх адаптеров.
- Actual: дополнительно введены `resolveTyped`/`requireProviderSuccess` и типизированы HTTP-ошибки
  Jellyfin/Emby.
- Reason: без этого AC «битый JSON → InvalidResponse» и «5xx → TemporaryError» не выполнялись бы для
  personal media servers — они бросали, и каскад видел безымянный сбой.
- Consequence: Jellyfin/Emby теперь различают отказ авторизации, троттлинг и сбой сервера.
- Follow-up: нет.

## Review findings

Самопроверка диффа дала две правки в собственном коде:

1. BLOCKING (исправлено) — WebDAV: 404 на listing root маппился в `NotFound`. Это скрывало неверно
   настроенный root и оставляло health чистым. Теперь `InvalidResponse` с явной причиной.
2. BLOCKING (исправлено) — Jellyfin/Emby: ошибки парсинга JSON и HTTP-сбои уходили броском и
   становились анонимным сбоем каскада вместо типизированного исхода.

Обе покрыты регрессионными тестами.

## Completion evidence

- Command: `gradlew :app:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL, 439 tests, 0 failures,
  0 errors (baseline 416; +23 новых).
- Command: `gradlew :core:network:test` → 55 tests, 0 failures.
- Command: `gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- Command: `git diff --check` → clean.
- Commit: `fcc2735`.
