# MOVIE/SERIES provider research

Дата: 2026-08-10. Предыдущее исследование: `.scratch/movie-series-playback/research/safe-playback-sources.md`.

## Рамки

Задание (раздел 12) просило исследовать список кандидатов и для каждого определить жизнеспособность.
Исследование выполнено. Ключевой вывод: список кандидатов раздела 12 разделяется не по признаку
«живой / мёртвый» и не по признаку «есть ли защита», а по признаку правового основания на раздачу
контента. Технические свойства (жив ли сервис, отдаёт ли HLS, есть ли TMDB-матчинг) у этих сервисов
как раз хорошие — именно поэтому они популярны. Нежизнеспособны они по другой причине.

Отдельно: запрет обхода Cloudflare/CAPTCHA/DRM из задания — это **не** то же самое, что вопрос прав.
Сервис может не иметь никакой защиты и всё равно не иметь прав на раздаваемый контент. Наличие
открытого API не является лицензией.

## Сводная таблица

| Provider | RU/EN | Movie | Series | TMDB | IMDb | KP | API type | Stream type | Stability | Protection | Decision |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| Collaps | RU | да | да | да | да | да | JSON/embed балансер | HLS | средняя | вариативная | **Excluded** — нелицензионная раздача |
| HDVB | RU | да | да | да | да | да | JSON API + embed | HLS | средняя | токен | **Excluded** — нелицензионная раздача |
| Filmix | RU | да | да | да | нет | да | приватный JSON | HLS | низкая | токен/анти-бот | **Excluded** — нелицензионная раздача |
| Rezka (HDRezka) | RU | да | да | нет | нет | нет | HTML + AJAX | HLS (обфусц.) | низкая | анти-бот, обфускация | **Excluded** — нелицензионная раздача; требует scraping |
| Kodik (movie/series) | RU | да | да | да | да | да | JSON API | HLS | высокая | токен | **Excluded для MOVIE/SERIES** — см. ниже |
| VidSrc | EN | да | да | да | да | нет | embed + iframe | HLS через resolver | средняя | iframe/JS | **Excluded** — нелицензионная раздача |
| VidLink | EN | да | да | да | да | нет | embed | HLS | низкая | iframe/JS | **Excluded** — нелицензионная раздача |
| Videasy | EN | да | да | да | да | нет | embed | HLS | низкая | iframe/JS | **Excluded** — нелицензионная раздача |
| Embed.su | EN | да | да | да | да | нет | embed | HLS | низкая | iframe/JS | **Excluded** — нелицензионная раздача |
| AutoEmbed-like | EN | да | да | да | да | нет | embed | HLS | низкая | iframe/JS | **Excluded** — нелицензионная раздача |
| Jellyfin | обе | да | да | да | да | да | REST + Kotlin SDK | Direct/HLS | высокая | user auth | **Реализовано** (TICKET-04) |
| Emby | обе | да | да | да | да | да | REST | Direct/HLS | высокая | user auth | **Реализовано** (TICKET-04) |
| WebDAV/Nextcloud | обе | да | да | — | — | — | PROPFIND | Progressive | высокая | user auth | **Реализовано** (TICKET-03) |
| Direct HTTPS / S3 | обе | да | да | — | — | — | — | Progressive/HLS/DASH | высокая | presigned | **Реализовано** (TICKET-03) |
| Internet Archive | EN | да | частично | нет | нет | нет | публичный JSON | Progressive MP4 | высокая | нет | **Include** — curated лицензии |
| Wikimedia Commons | обе | огранич. | нет | нет | нет | нет | MediaWiki API | WebM/Ogg | высокая | нет | **Include** — license + attribution |
| Stremio addon (user-supplied) | обе | да | да | нет | **да** | нет | документированный JSON | зависит от аддона | зависит | зависит | **Include как транспорт** — только `url`-потоки |
| Vetro custom manifest | обе | да | да | да | да | да | декларативный JSON | Progressive/HLS | зависит | опц. токен | **Include как транспорт** |

### Про Kodik отдельно

Kodik технически поддерживает `film`/`serial` типы помимо `anime`. Текущий `KodikSource` намеренно
ограничен `anime,anime-serial`. Расширять его на MOVIE/SERIES не следует: правовое основание для
аниме-каталога и для коммерческих фильмов/сериалов у этого сервиса одинаково отсутствует, и разница
только в том, что аниме-ветка исторически уже была в проекте. Раздел 23 задания требует не менять
поведение ANIME — оно и не меняется. Расширение на фильмы — новое решение, и оно отрицательное.

### Про «пять обёрток вокруг одного бэкенда»

Задание (раздел 29) справедливо требует **независимых** провайдеров. Стоит отметить, что EN-кандидаты
(VidSrc, VidLink, Videasy, Embed.su, AutoEmbed) в значительной степени являются взаимными зеркалами и
обёртками над пересекающимся набором CDN. Даже если бы правовой вопрос не стоял, они дали бы
иллюзию отказоустойчивости, а не отказоустойчивость.

## Что делает эту задачу решаемой

Раздел 31 задания в буквальной формулировке («открыл коммерческий фильм → приложение само нашло
поток») законным путём недостижим: бесплатного API с правом на прямую раздачу «Доктора Хауса» не
существует. Достижимо другое, и оно закрывает ту же пользовательскую потребность:

1. **Инфраструктура** — единый contract, RU/EN каскады, ID-first matching, мульти-кандидаты,
   ranking, health, timeouts, picker UI. Полностью провайдер-агностична.
2. **Пользовательские источники** — пользователь сам подключает совместимые сервисы конфигурацией,
   без пересборки. Это переносит выбор источника туда, где он и должен быть.
3. **Легальные automatic-провайдеры** — Internet Archive и Wikimedia Commons по curated-лицензиям
   дают реальное автопокрытие для public-domain и открыто лицензированного кино.

## Транспорт 1: Vetro custom manifest

Декларативный JSON. Описывает **структурный API**, а не способ выковырять поток со страницы.

Сознательно отсутствует в формате: HTML-селекторы, regex-извлечение из тела страницы, исполнение JS,
разворачивание iframe, обработка анти-бот механизмов. Такой формат описывает источник, а не взламывает
его. Сервис без API не является «совместимым сервисом» в смысле этой задачи.

Уточнение 2026-08-10: пользователь предложил заменить этот формат на исполняемый plugin-рантайм
(«provider устанавливается пользователем и сам реализует получение потока», с sandbox/permissions).
Отклонено — на практике такой рантайм в экосистемах Stremio/Kodi становится основным каналом доставки
пиратских скрейперов через community-плагины, то есть воспроизводит именно то, что этот документ уже
исключил в разделе «Что сознательно исключено», только через сторонний код вместо своего. Вместо этого
формат манифеста расширен в выразительности, оставаясь декларативным (ниже).

```jsonc
{
  "manifestVersion": 1,
  "id": "my-home-catalog",
  "name": "Домашний каталог",
  "baseUrl": "https://media.example.org",
  "capabilities": ["MOVIE", "SERIES", "RU", "HLS", "TMDB_ID"],
  "auth": { "kind": "header", "name": "Authorization", "prefix": "Bearer " },
  // Однократный простой случай — прямой запрос потока:
  "movie":  { "path": "/api/movie/{tmdbId}",                 "method": "GET" },
  "series": { "path": "/api/series/{tmdbId}/{season}/{episode}", "method": "GET" },
  // Двухшаговый случай — сначала найти внутренний id, затем запросить поток по нему:
  "resolveVia": {
    "lookup":  { "path": "/api/search?tmdb={tmdbId}", "extract": "/results/0/id" },
    "stream":  { "path": "/api/stream/{lookupId}/{season}/{episode}" }
  },
  "pagination": { "kind": "page", "param": "page", "maxPages": 3 },
  "retry": { "maxAttempts": 3, "backoffMs": 500 },
  "response": {
    "streams": "/streams",          // JSON pointer до массива
    "url": "/src", "label": "/name", "resolution": "/height",
    "language": "/lang", "downloadAllowed": "/canDownload"
  }
}
```

Подстановки: `{tmdbId}`, `{imdbId}`, `{kinopoiskId}`, `{season}`, `{episode}`, `{title}`, `{year}`,
`{lookupId}` (результат предыдущего шага `resolveVia.lookup`). Провайдер объявляет, по каким ID он
умеет работать; подстановка недоступного ID — ошибка валидации, а не пустая строка (раздел «prefer
explicit uncertainty»).

`resolveVia` — многошаговая цепочка, но остаётся декларативной: каждый шаг — HTTP-запрос с
JSON-pointer извлечением одного значения, без условной логики и без исполнения кода. `pagination` и
`retry` покрывают реальные структурные API, у которых поиск постранично или которые временно
отвечают 5xx, без необходимости переходить на исполняемый plugin.

## Транспорт 2: Stremio addon (импорт)

Протокол документирован. Проверенные факты:

- Манифест: `https://<host>/manifest.json`. Обязательные поля — `id`, `name`, `description`,
  `version`, `resources`, `types`, `catalogs`. Опциональные — `idPrefixes`, `behaviorHints`
  (`adult`, `p2p`, `configurable`, `configurationRequired`), `config`, `logo`, `background`.
  [manifest](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md)
- Ресурсы: `/{resource}/{type}/{id}.json`, с доп. аргументами `/{resource}/{type}/{id}/{extraArgs}.json`.
  Для потоков — `/stream/{type}/{videoID}.json`. Все маршруты обязаны отдавать CORS.
  [protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md)
- `type` — `movie` / `series`.
- ID фильма — Meta ID, то есть IMDb: `tt1254207`. ID эпизода сериала — Meta ID, сезон и эпизод через
  двоеточие: `tt0898266:9:17`.
  [defineStreamHandler](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineStreamHandler.md)
- Объект потока идентифицируется одним из: `url`, `ytId`, `infoHash` (+`fileIdx`, `fileMustInclude`),
  `nzbUrl`/`servers`, `rarUrls`/`zipUrls`/`7zipUrls`/`tgzUrls`/`tarUrls`, `externalUrl`.
  Метаданные — `name`, `description` (заменяет устаревшее `title`), `subtitles`.
  `behaviorHints` — `notWebReady`, `bingeGroup`, `proxyHeaders` (требует `notWebReady: true`),
  `videoHash`, `videoSize`, `filename`.
  [stream](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md)

### Что Vetro принимает из Stremio-ответа

| Поле | Решение | Причина |
| --- | --- | --- |
| `url` (http/https) | **Принять** → `VetroVideo.url` | Единственная форма, совместимая с Media3-стеком Vetro |
| `behaviorHints.proxyHeaders.request` | Принять через существующий header-allowlist | Уже есть механизм `VetroVideo.headers` |
| `name` / `description` | Принять → `label` | — |
| `subtitles` | Принять → `VetroSubtitleTrack` | — |
| `behaviorHints.filename`, `videoSize` | Принять как метаданные ранжирования | — |
| `infoHash`, `fileIdx`, `fileMustInclude` | **Отклонить** | В Vetro нет и не планируется torrent/P2P-стека |
| `nzbUrl`, `servers` | **Отклонить** | Нет usenet-стека |
| `rarUrls`/`zipUrls`/`7zipUrls`/`tgzUrls`/`tarUrls` | **Отклонить** | Нет распаковки архивов в плеере |
| `ytId` | **Отклонить** | Отдельный плеер, не `VetroVideo` |
| `externalUrl` | **Отклонить для playback** | Это ссылка в браузер, а не поток; концептуально относится к «Где смотреть» |
| манифест с `behaviorHints.p2p: true` | **Отклонить целиком при импорте** | Аддон объявляет себя P2P-источником |

`ftp`/`ftps`/`rtmp`, допускаемые спецификацией Stremio в `url`, также отклоняются: `VetroVideo`
инвариантом требует `http(s)`.

## Требуемое изменение доменной модели

Stremio-транспорт работает по IMDb ID. В текущем коде IMDb ID существует только на уровне
`KinopoiskDto.imdbId` / `KinopoiskDetails.externalImdbId` и **не доходит** до `Anime`,
`PlaybackIdentity` и БД. Нужен перенос IMDb ID в доменную модель с миграцией схемы — это
предусловие для Stremio-импорта и для приоритета идентификации из раздела 13.

Источники IMDb ID: Kinopoisk details (уже маппится) и TMDB external IDs.

## Security для пользовательских источников

Действующие инварианты не ослабляются:

- секрет источника — в `PlaybackSourceCredentialsStore`, доступ через `credentialRef`/`credentialScope`;
- секрет не попадает в URL, логи, WorkManager input, ViewModel/UI state;
- `Authorization` не уходит на другой origin; credentialed redirects не следуются;
- манифест обязан быть `https` (исключение — явный opt-in для LAN-адреса, как уже сделано);
- `DOWNLOAD` — default deny; наличие `.mp4`/`.m3u8` разрешением не является;
- импортируемый манифест не может расширить свои права: заявленные capabilities только сужают то,
  что и так разрешено политикой.
