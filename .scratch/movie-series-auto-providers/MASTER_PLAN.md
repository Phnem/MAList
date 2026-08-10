# MOVIE/SERIES automatic provider infrastructure — Master Plan

## Workflow

Current workflow state: COMPLETED
Current ticket: None
Last completed ticket: TICKET-13
Next eligible ticket: None (TICKET-11 deferred)
Last updated: 2026-08-10 (plan revised: manifest widened, TICKET-11 deferred, plugin-runtime declined)

## Goal

Довести MOVIE/SERIES до философии ANIME-каскада: несколько независимых провайдеров за единым
contract, ID-based matching, fallback, ranking, health, нормализованный `VetroVideo`, единый плеер —
плюс возможность подключить совместимый источник конфигурацией без пересборки.

## Canonical specification

`.scratch/movie-series-auto-providers/spec.md`

## Research

`.scratch/movie-series-auto-providers/research/MOVIE_SERIES_PROVIDER_RESEARCH.md`

## Architecture review

`.scratch/movie-series-auto-providers/architecture/INITIAL_REVIEW.md`

## Global constraints

- ANIME-пути не меняют поведение (раздел 23 задания).
- Download остаётся default-deny; capability `DOWNLOAD` требует явного основания.
- Секреты: encrypted store, `credentialRef`/`credentialScope`; не в URL, логах, WorkManager, UI state.
- `Authorization` не уходит на другой origin; credentialed redirects не следуются.
- `CancellationException` всегда пробрасывается.
- Released-only семантика эпизодов сохраняется.
- Формат пользовательского манифеста описывает структурный API; без scraping/JS/iframe/анти-бот
  примитивов и без torrent/usenet.

## Non-goals

Сервисы нелицензионной раздачи из раздела 12 задания (Collaps, HDVB, Filmix, Rezka, VidSrc, VidLink,
Videasy, Embed.su, AutoEmbed), расширение Kodik на MOVIE/SERIES, обход защит, Plex/VK/RUTUBE,
DASH offline, миграция `StreamingSeasonDiscovery`.

## Verification commands

### Fast checks

```bash
./gradlew :app:compileDebugKotlin
```

### Ticket checks

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.myapplication.media.source.*"
```

### Full checks

```bash
./gradlew :core:network:test :app:testDebugUnitTest :app:assembleDebug
```

Baseline at `dd20d8f`: 416 app unit tests, 55 core/network tests, 0 failures.

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Typed provider outcomes + capability contract | DONE_WITH_DEVIATIONS | — | `fcc2735` | self-review, 2 blocking findings fixed |
| TICKET-02 | RU/EN cascade routing split | DONE | 01 | `beb039c` | self-review, no blocking findings |
| TICKET-03 | IMDb ID in domain model and persistence | DONE_WITH_DEVIATIONS | — | `3b4f966` | Migration13Test fragility fixed |
| TICKET-04 | Shared ID-first matcher | DONE | 01, 03 | `7f23901` | self-review, no blocking findings |
| TICKET-05 | Multi-candidate aggregation and ranking | DONE_WITH_DEVIATIONS | 01, 04 | `fe4e474` | hosterUrl fabrication caught and fixed |
| TICKET-06 | Provider health persistence | DONE_WITH_DEVIATIONS | 01 | `1108938` | penalty-snapshot defect caught and fixed |
| TICKET-07 | Vetro custom source manifest (expressive) | DONE_WITH_DEVIATIONS | 01, 04 | `ea5b1b7` | unsatisfiable-template outcome fixed |
| TICKET-08 | Stremio addon import | DONE | 03, 07 | `49a5834` | self-review, no blocking findings |
| TICKET-09 | Custom source settings UI | DONE | 07, 08 | `edf2884`, `4630ea1` | InstalledSourceStore extracted for testability |
| TICKET-10 | MOVIE/SERIES source picker screen | DONE | 02, 05 | `5a9c074` | self-review, no blocking findings |
| TICKET-11 | Lawful automatic providers (Internet Archive/Wikimedia) | DEFERRED | 01, 04 | | |
| TICKET-12 | Legacy seam cleanup | DONE | 01, 05 | `3da1459` | self-review, no blocking findings |
| TICKET-13 | Final regression and audit | DONE | all except 11 | `c5134ba` | see reviews/final-review.md |

## Ticket details

### TICKET-01 — Typed provider outcomes + capability contract

Enabling. Вводит `MovieSeriesStreamingProvider` с `id`, `capabilities`, `resolve`, и типизированный
`ProviderResolution` (`FOUND`, `NOT_FOUND`, `TEMPORARY_ERROR`, `BLOCKED`, `RATE_LIMITED`,
`INVALID_RESPONSE`, `UNSUPPORTED`, `NOT_CONFIGURED`). Четыре существующих адаптера адаптируются без
изменения наблюдаемого поведения. Провайдер без применимой capability не вызывается по сети.

AC: исходы различимы в каскаде; capability-фильтр отсекает без сетевого вызова; существующие
personal-source тесты зелёные; `CancellationException` пробрасывается. TDD: REQUIRED.

### TICKET-02 — RU/EN cascade routing split

`PlaybackRoute.DirectOnly` → раздельные MOVIE/SERIES маршруты по языку; переименование в осмысленное
имя. Локаль задаёт приоритет, ручной выбор языка возможен. ANIME-ветки не трогаются.

AC: RU и EN дают разные наборы провайдеров; ANIME-маршруты идентичны прежним; политика — чистая
функция под тестами. TDD: REQUIRED.

### TICKET-03 — IMDb ID in domain model and persistence

`imdbId` доходит от Kinopoisk/TMDB до `Anime`, `PlaybackIdentity` и БД; миграция схемы; старые
строки валидны с `null`.

AC: миграция применяется на существующей БД; IMDb ID сохраняется и читается; `PlaybackIdentity`
переносит его. TDD: REQUIRED.

### TICKET-04 — Shared ID-first matcher

Чистый matcher с уровнями точности TMDB → IMDb → Kinopoisk → normalized title+year, плюс
season/episode. Конфликтующий ID не выбирается автоматически. Jellyfin/Emby переводятся на него без
изменения поведения.

AC: уровни точности покрыты тестами; конфликт ID отвергается; неоднозначный title-only не
принимается автоматически. TDD: REQUIRED.

### TICKET-05 — Multi-candidate aggregation and ranking

Каскад собирает кандидатов со всех провайдеров. Единый scoring: точность матча, язык, качество,
прямота потока, health, latency. Заменяет захардкоженный `reliabilityRank()`.

AC: `A error + B not found + C found` → C; несколько кандидатов доходят до вызывающего;
ranking детерминирован и покрыт тестами. TDD: REQUIRED.

### TICKET-06 — Provider health persistence

`lastSuccess`, `lastFailure`, `consecutiveFailures`, `averageLatency`, `temporaryDisabledUntil`.
Файловый стор, часы подставляются. Временное отключение с восстановлением, без вечного бана.

AC: health переживает перезапуск; отключённый источник пропускается без сетевого ожидания; одна
ошибка не банит навсегда. TDD: REQUIRED.

### TICKET-07 — Vetro custom source manifest (expressive)

Версионированный декларативный JSON — остаётся декларативным, без исполнения кода. Расширяется
сверх исходного плана, чтобы покрывать больше реальных структурных API без плагинов:
capabilities, шаблоны путей с подстановками, JSON-pointer маппинг ответа, **цепочки из нескольких
запросов** (например: сначала resolve id по названию, затем запрос потока по найденному id),
**пагинация/повтор с backoff**, **несколько auth-схем** (header/query/bearer), опциональный auth
через encrypted store. Валидация отвергает смену host через подстановку.

Явная граница (не пересматривается в этом тикете): манифест не исполняет JS, не парсит HTML, не
разворачивает iframe, не обходит анти-бот защиту. Расширение — в выразительности декларативных
шагов, не в добавлении рантайма произвольного кода.

AC: валидный манифест резолвит фильм и эпизод на MockEngine, включая двухшаговый сценарий; невалидный
отвергается с причиной; секрет не попадает в URL/логи; SSRF через подстановки невозможен. TDD: REQUIRED.

### TICKET-08 — Stremio addon import

`/manifest.json` → внутренний дескриптор; `/stream/{type}/{id}.json`; id `tt…` и `tt…:{s}:{e}`.
Принимается только http(s) `url`; `infoHash`/`nzbUrl`/архивы/`ytId`/`externalUrl` отклоняются;
манифест с `behaviorHints.p2p` отклоняется.

AC: фикстуры манифеста и потоков транслируются; каждая отклоняемая форма покрыта тестом;
`proxyHeaders` проходят через существующий allowlist. TDD: REQUIRED.

### TICKET-09 — Custom source settings UI

`Настройки → Источники видео → Добавить источник`: вставка ссылки или импорт файла, проверка,
включение/выключение, обновление, удаление. Секрет не возвращается в UI/ViewModel.

AC: полный CRUD; test connection не течёт секретами; выключенный источник не участвует в каскаде.
TDD: RECOMMENDED.

### TICKET-10 — MOVIE/SERIES source picker screen

Отдельный экран «Смотреть → Русский / English / Мои источники» с провайдером, переводом и качеством.
ANIME остаётся на существующем меню эпизода.

AC: несколько кандидатов сгруппированы и выбираемы; запуск выбранного потока работает; причина
пустого результата отображается; ANIME-меню не изменилось. TDD: RECOMMENDED.

### TICKET-11 — Lawful automatic providers (DEFERRED)

Internet Archive и Wikimedia Commons как automatic-провайдеры. Понижено пользователем 2026-08-10:
покрывает узкий срез public-domain контента и почти не двигает основной сценарий «посмотреть
обычный фильм/сериал». Силы перенаправлены на TICKET-07 (выразительность манифеста) и TICKET-08
(Stremio-импорт), которые дают реальное покрытие через пользовательские источники без встроенного
whitelist. Делать только если появится отдельный запрос; не блокирует TICKET-13.

### TICKET-12 — Legacy seam cleanup

`DownloadQuality` из `ui/details` в media-слой; `resolveHosters` перестаёт терять причину пустоты.

AC: media-слой не зависит от UI-пакета; причина доходит до вызывающего; поведение не изменилось.
TDD: RECOMMENDED.

### TICKET-13 — Final regression and audit

Полные суиты, сборка, аудит спеки по требованиям, финальный архитектурный чекпоинт.

## Decisions

1. Пиратские адаптеры из раздела 12 не реализуются; вместо них — инфраструктура, пользовательские
   источники и легальные automatic-провайдеры. Подтверждено пользователем 2026-08-10.
2. Формат пользовательского источника: свой Vetro-манифест **и** импорт Stremio-аддонов.
3. Пользовательские источники поддерживают auth через существующий encrypted store.
4. Для MOVIE/SERIES делается отдельный экран выбора источника; ANIME остаётся на своём меню.
5. Манифест описывает структурный API; scraping/JS/iframe/анти-бот примитивов в формате нет.
6. Из Stremio принимаются только http(s) `url`-потоки; torrent/usenet/архивы/ytId/externalUrl — нет.
7. Пользовательские источники расширяются двумя декларативными путями: выразительный Vetro-манифест
   (многошаговые запросы, пагинация, несколько auth-схем) и Stremio-импорт, где произвольная логика
   получения потока живёт на сервере аддона, а не в коде, исполняемом внутри Vetro. Локальный
   plugin-рантайм с исполнением стороннего кода (sandbox/permissions/network isolation) для этой цели
   не строится. Подтверждено пользователем 2026-08-10 после явного возражения: такой рантайм убирает
   структурное ограничение манифеста (нет scraping/JS/anti-bot bypass) и на практике в экосистемах
   Stremio/Kodi становится основным способом доставки пиратских скрейперов через community-плагины —
   то есть воспроизводит именно то, что раздел 12 задания и это же решение (#1) исключают, только
   через community, а не через встроенный код Vetro.
8. TICKET-11 (Internet Archive/Wikimedia) понижен до DEFERRED; не блокирует TICKET-13.

## Global deviations

None yet.

## Known risks

- Миграция схемы БД для `imdbId` (TICKET-03) затрагивает существующие данные пользователя.
- Расширение contract (TICKET-01) касается четырёх работающих адаптеров.
- SSRF-поверхность в пользовательских манифестах (TICKET-07/08).
- Раздел 31 задания в буквальном виде недостижим законным путём; принято пользователем.

## Deferred work

- Миграция `StreamingSeasonDiscovery` на общий механизм.
- Plex, VK, RUTUBE.
- DASH offline download.
- Health в БД вместо файлового стора.

## Final acceptance checklist

- [x] Every required ticket completed (TICKET-11 deferred by the user)
- [x] Full test suite run: 591 app + 60 core/network, 0 failures, forced re-run
- [x] Specification reviewed requirement by requirement (reviews/final-review.md)
- [x] No unresolved blocking review findings (7 found by self-review, all fixed)
- [x] Migration and compatibility behavior verified (Migration15Test, sync-wipe test)
- [x] User-visible behavior verified at the state/service layer; Compose has no test harness here
- [x] Deferred work explicitly recorded
- [x] Final architecture checkpoint completed (INITIAL_REVIEW A-G closed, H deferred)
