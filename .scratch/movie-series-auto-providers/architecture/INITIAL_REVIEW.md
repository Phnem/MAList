# Initial architecture review — MOVIE/SERIES automatic providers

Scope: playback provider layer for MOVIE/SERIES. Read-only analysis at `dd20d8f`.

## 1. Boundaries that help

- **`runPlaybackProviderCascade`** — уже правильный seam. Generic по `T`, `supervisorScope`,
  per-call timeout, `CancellationException` пробрасывается, собирает `failed`/`timedOut`/`elapsedMs`.
  Health и ranking получают нужную телеметрию без переписывания механики.
- **`PlaybackRoutingPolicy`** — чистая функция, тестируется без сети. Расширение на RU/EN каскады
  делается здесь, а не в `SourceEngine`.
- **`VetroVideo`/`VetroHoster`** — стабильная нормализованная модель с готовыми security-инвариантами
  (`credentialRef`, `credentialScope`, header allowlist, `containsSensitiveQuery`). Новые провайдеры
  не должны её менять, только заполнять.
- **`PlaybackSourceCredentialsStore` + `PlaybackAuthScope`** — готовый механизм для секретов
  пользовательских источников; переиспользуется как есть.

## 2. Boundaries that obstruct

| # | Проблема | Классификация |
| --- | --- | --- |
| A | `MovieSeriesSourceResult` — 3 состояния; раздел 16 требует 8. Health не может отличить «нет такого фильма» от «источник лежит». | REQUIRED_BEFORE_IMPLEMENTATION |
| B | `PlaybackRoute.DirectOnly` схлопывает MOVIE+SERIES+оба языка в один маршрут. Имя вводит в заблуждение и после добавления automatic-провайдеров станет неверным. | REQUIRED_BEFORE_IMPLEMENTATION |
| C | IMDb ID не доходит до `Anime`/`PlaybackIdentity`/БД. Блокирует Stremio-транспорт и раздел 13. | REQUIRED_BEFORE_IMPLEMENTATION |
| D | Правила точности ID-сопоставления заперты внутри Jellyfin/Emby адаптера. | REQUIRED_DURING_IMPLEMENTATION |
| E | `rankVideosForResolution` знает только разрешение; `reliabilityRank()` хардкодит `libria.fun`. Нужен слой, принимающий точность матча/язык/health. | REQUIRED_DURING_IMPLEMENTATION |
| F | `DownloadQuality` объявлен в `ui/details/DownloadWizardViewModel.kt`, но используется из `media/MediaGateway` и `media/download/SeasonBatchDownloader` — media-слой зависит от UI-пакета. | REQUIRED_DURING_IMPLEMENTATION |
| G | `SourceEngine.resolveHosters` — lossy seam: схлопывает типизированный `PlaybackResolution` в голый список, теряя причину пустоты. Picker UI нуждается в причине. | REQUIRED_DURING_IMPLEMENTATION |
| H | `StreamingSeasonDiscovery` жёстко связан с `AnimeHeavenSource`/`JutSuSource`/`KodikDirectSearch`. | FOLLOW_UP |

### Про H

Миграция `StreamingSeasonDiscovery` на общий provider-механизм затрагивает исключительно
ANIME-пути. Раздел 23 задания требует не менять поведение ANIME без необходимости, а новый
MOVIE/SERIES каскад в этот класс не упирается — MOVIE/SERIES сезоны приходят из TMDB/Kinopoisk.
Делать не в этом этапе.

## 3. Модули, которые должны остаться стабильными

- Все anime-адаптеры и `resolveRu`/`resolveEn` внутри `SourceEngine`.
- `VetroVideo` схема сериализации (персистится в загрузках).
- Плеер и загрузчик: они потребляют результат и не должны узнать о новых типах провайдеров.

## 4. Где прятать сложность

Новый пакет `media/source/movieseries/`:

- `MovieSeriesStreamingProvider` — contract + `ProviderCapabilities` + `ProviderResolution`;
- `MovieSeriesCascade` — RU/EN каскады, ограниченная параллельность, агрегация кандидатов;
- `matching/` — чистый ID-matcher с уровнями точности;
- `ranking/` — чистый scoring;
- `health/` — стор + политика временного отключения;
- `custom/` — Vetro-манифест, Stremio-транслятор, валидация.

Наружу торчит один узкий интерфейс: каскад отдаёт ранжированный список кандидатов с причиной.
`SourceEngine` остаётся точкой диспетчеризации и не растёт.

## 5. Публичные интерфейсы, которые меняются

- `MovieSeriesPlaybackSource` → расширяется до capability-aware contract. Существующие 4 адаптера
  (Direct, WebDAV, Jellyfin, Emby) адаптируются, их поведение не меняется.
- `PlaybackRoute` — переименование `DirectOnly` + новые ветки.
- `Anime` + схема БД — `imdbId`.

## 6. Риски, за которыми следят ревью тикетов

1. Расползание изменений в ANIME-ветки `SourceEngine`.
2. Ослабление download default-deny при добавлении capability `DOWNLOAD`.
3. Утечка секрета пользовательского источника в `VetroVideo.url`, логи или WorkManager.
4. Пользовательский манифест как канал SSRF: подстановки не должны позволять сменить host.
5. Потеря released-only семантики эпизодов при мульти-кандидатах.
6. Рост `SourceEngine` в god-object.

## 7. Prefactoring, требуемый до фич

Тикеты 01–03 (исходы, маршруты, IMDb ID) — enabling, но каждый имеет собственные проверяемые
критерии и не является «слоем ради слоя».
