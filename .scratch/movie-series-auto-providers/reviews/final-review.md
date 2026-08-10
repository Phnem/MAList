# Final review — MOVIE/SERIES automatic provider infrastructure

Base: `dd20d8f`. Head: `c5134ba`. Дата: 2026-08-10.

## Верификация

| Проверка | Результат |
|---|---|
| `gradlew :app:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL, **591 tests, 0 failures, 0 errors** (база 416) |
| `gradlew :core:network:test` | **60 tests, 0 failures** (база 55) |
| `gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `git diff --check` | clean |
| Кумулятивный дифф | 59 файлов, +5477 / −281 |

Прогон тестов форсированный (`--rerun-tasks`), не из кэша.

## Аудит требований спеки

| # | Требование | Статус | Доказательство |
|---|---|---|---|
| 1 | Единый contract с capabilities | PASS | `MovieSeriesStreamingProvider`; `ProviderContractTest` |
| 2 | Раздельные RU/EN каскады | PASS | `PlaybackRoute.MovieSeriesRu/En`; `MovieSeriesProviderSelectionTest` |
| 3 | ID-first: TMDB → IMDb → KP → title+year | PASS | `MediaIdentityMatcherTest` (20 тестов) |
| 4 | 8 типизированных исходов | PASS | `ProviderResolution`; `ProviderContractTest` |
| 5 | Мульти-кандидаты, не первый попавшийся | PASS | `MovieSeriesRankingTest`; `MovieSeriesSourceOptionsTest` |
| 6 | Ranking по точности/языку/качеству/health | PASS | `MovieSeriesRanking`; 14 тестов |
| 7 | Provider health персистится и влияет | PASS | `ProviderHealthPolicyTest` (13); `MovieSeriesSourceCascadeTest` |
| 8 | Ограниченные таймауты, быстрый переход | PASS | `runPlaybackProviderCascade`; тест пропуска «мёртвого» провайдера |
| 9 | Установка источника без пересборки | PASS | `CustomSourceInstallerTest`, `CustomSourceSettingsServiceTest` |
| 10 | Экран выбора источника | PASS | `MovieSeriesSourceSheet`; `MovieSeriesSourceOptionsTest` |
| 11 | IMDb ID в доменной модели | PASS | миграция 15; `Migration15Test`, `TmdbImdbIdTest` |

### Non-functional

| Требование | Статус | Доказательство |
|---|---|---|
| ANIME не изменилось | PASS | Аниме-маршруты и адаптеры не тронуты; `rankVideosForResolution` намеренно оставлен; все существующие тесты зелёные |
| Download default-deny | PASS | `grep "downloadAllowed = true"` по `app/src/main` — 0 совпадений |
| Секреты не в логах | PASS | В новом коде только `Log.w` со статическими сообщениями |
| Секреты не в URL | PASS | Тест «a header secret is sent and never appears in the url» |
| SSRF через подстановки | PASS | Перепроверка origin после подстановки + percent-кодирование; 4 теста |
| `CancellationException` пробрасывается | PASS | `resolveTyped` + тест каскада |
| Released-only семантика | PASS | Не трогалась; сезоны/эпизоды приходят из существующего слоя |
| Нет отладочного мусора | PASS | `grep TODO/FIXME/println/Log.d` по новому пакету — пусто |

## Отклонения (все зафиксированы)

1. **`reliabilityRank()` не удалён** (TICKET-05). `VideoRanking` общий с аниме-каскадом, предпочтение
   `libria.fun` — аниме-специфичное; замена изменила бы порядок источников в ANIME (запрещено
   разделом 23). Для MOVIE/SERIES хардкода нет — работает новый слой.
2. **`{year}` убран из плейсхолдеров манифеста** (TICKET-07). У записи библиотеки нет года выпуска;
   реклама плейсхолдера сделала бы любой использующий его манифест вечно `Unsupported`.
3. **TICKET-11 (Internet Archive/Wikimedia)** — DEFERRED по решению пользователя.
4. **Локальный plugin-рантайм не построен** — отклонено с обоснованием, заменено расширением
   декларативного манифеста и Stremio-транспортом. Согласовано с пользователем.

## Находки самопроверки, исправленные по ходу

| Тикет | Находка | Исправление |
|---|---|---|
| 01 | WebDAV: 404 на listing root → `NotFound`, скрывало неверный root и оставляло health чистым | → `InvalidResponse` с причиной |
| 01 | Jellyfin/Emby: ошибки JSON/HTTP уходили броском как безымянный сбой | `resolveTyped`/`requireProviderSuccess` |
| 03 | `Migration13Test` читал полумигрированную БД сгенерированными запросами | переведён на raw SQL |
| 05 | Round-trip кандидатов выдумывал `hoster.url` из URL видео | дословный перенос `hosterUrl` |
| 06 | Penalty считался ПОСЛЕ записи → health-измерение ранжирования было бы мёртвым кодом | снимок penalties до записи |
| 07 | Неудовлетворимый шаблон → `NotFound` (ложь: запрос не отправлялся) | → `Unsupported` до сети |
| 13 | 7 файлов переписаны целиком из-за конверсии CRLF→LF | восстановлены исходные окончания; дифф −2590 строк шума |

## Известные ограничения

- Раздел 31 задания в буквальной формулировке недостижим законным путём: бесплатного API с правом
  на прямую раздачу коммерческого тайтла не существует. Автопокрытие обеспечивается источниками,
  которые подключает сам пользователь.
- Live smoke против реальных пользовательских источников не выполнялся: нужны реальные endpoint'ы.
  Все контракты покрыты MockEngine-фикстурами.
- UI-тестов (Compose) нет — в проекте нет такой инфраструктуры; покрыты ViewModel/сервисный слой.
- `StreamingSeasonDiscovery` не мигрирован (осознанно, см. INITIAL_REVIEW пункт H).

## Финальный архитектурный чекпоинт

Пункты A–G из `INITIAL_REVIEW.md` закрыты. Пункт H (`StreamingSeasonDiscovery`) осознанно оставлен:
он затрагивает исключительно ANIME, а новый каскад в него не упирается.

`SourceEngine` не превратился в god-object: вся новая сложность живёт в `media/source/movieseries/`
за узкими интерфейсами (`MovieSeriesStreamingProvider`, `InstalledSourceStore`,
`ProviderHealthRegistry`), каждый из которых тестируется без Android и без сети.

## Статус

COMPLETED для согласованного объёма.
