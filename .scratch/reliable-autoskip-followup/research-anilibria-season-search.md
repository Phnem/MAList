# AniLiberty API v1: безопасный выбор сезона франшизы

Дата проверки: 2026-07-31.

## Источники

Использовались только официальные ресурсы AniLiberty:

- [Swagger UI API v1](https://api.anilibria.app/api/docs/v1/)
- [OpenAPI 3.0 JSON](https://api.anilibria.app/storage/api/docs/v1?aniliberty-api-v1-docs.json)
- [поиск релизов](https://api.anilibria.app/api/v1/app/search/releases?query=Shokugeki%20no%20Souma)
- [франшизы релиза 5150](https://api.anilibria.app/api/v1/anime/franchises/release/5150)
- [полный релиз 5150](https://api.anilibria.app/api/v1/anime/releases/5150)
- [эпизод Food Wars S3E6](https://api.anilibria.app/api/v1/anime/releases/episodes/95bb6e5b-789e-11ec-ae92-0242ac120002)

OpenAPI также объявляет основной сервер `https://aniliberty.top/api/v1`. Используемый приложением хост `https://anilibria.top/api/v1` на момент проверки возвращал те же ответы, но ссылки выше ведут на официальный API-домен документации.

## Что предоставляет API

`GET /app/search/releases?query=...` возвращает непагинированный массив кратких объектов релиза. Существенные поля: `id`, `alias`, `name.main`, `name.english`, `name.alternative`, `type.value`, `year`, `season.value`, `episodes_total`.

API поиска не принимает номер сезона франшизы. Поле `season.value` и фильтр каталога `f[seasons]` означают календарный сезон выхода (`winter`, `spring`, `summer`, `autumn`), а не «сезон 1/2/3» франшизы. Это прямо следует из enum в [OpenAPI-схеме](https://api.anilibria.app/storage/api/docs/v1?aniliberty-api-v1-docs.json).

`GET /anime/franchises/release/{releaseId}` возвращает все франшизы, в которых участвует релиз. В каждом объекте есть:

- `id`, `name`, `name_english`;
- `total_releases`, `total_episodes`;
- `franchise_releases[]`;
- у элемента `franchise_releases[]`: `sort_order`, `release_id`, `franchise_id` и вложенный краткий `release`.

Схема описывает `sort_order` только как «порядок сортировки». Это не документированное поле `season_number`, поэтому универсально приравнивать его к номеру телевизионного сезона нельзя: во франшизе могут находиться фильмы, OVA и special-релизы. Допустимые типы релиза в схеме: `TV`, `ONA`, `WEB`, `OVA`, `OAD`, `MOVIE`, `DORAMA`, `SPECIAL`.

`GET /anime/releases/{idOrAlias}` принимает числовой ID или alias и возвращает полный релиз, включая `episodes[]`. У эпизода доступны:

- `id`;
- `ordinal` — номер эпизода, который по схеме может быть целым или дробным;
- `duration` в секундах;
- `opening { start, stop }`, `ending { start, stop }`;
- `hls_480`, `hls_720`, `hls_1080`.

Отдельный `GET /anime/releases/{idOrAlias}/episodes/timecodes` требует `sessionToken` и документирует ответ `403` без авторизации. Для источника воспроизведения он не нужен: публичные full-release и episode endpoints уже содержат opening/ending.

## Проверка Food Wars, сезон 3

Результаты официального поиска:

| query | количество | первые ID |
|---|---:|---|
| `Food Wars` | 0 | — |
| `Food Wars! The Third Plate` | 0 | — |
| `Повар-боец Сома` | 7 | `421, 2628, 5150, 8510, 8620` |
| `Shokugeki no Souma` | 5 | `421, 2628, 5150, 8510, 8620` |
| `Shokugeki no Souma: San no Sara` | 5 | `5150, 2628, 8510, 8620, 421` |
| `Повар-Боец Сома 3` | 7 | `5150, 421, 2628, 8510, 8620` |

Следствия:

- маркетинговое английское название из MAL/AniList может вообще ничего не найти;
- даже точное romaji-название сезона возвращает другие сезоны;
- широкое название франшизы ставит первый сезон первым;
- первый результат поиска нельзя автоматически считать выбранным сезоном.

Для релиза `5150` endpoint франшизы возвращает:

- franchise ID `0746d428-2fbf-4264-9ff6-f448f62e48ff`;
- `name = "Повар-боец Сома"`;
- `name_english = "Shokugeki no Souma"`;
- `total_releases = 5`;
- порядок релизов:
  1. `421` — первый сезон;
  2. `2628` — второй сезон;
  3. `5150` — третий сезон;
  4. `8510` — четвёртый сезон;
  5. `8620` — пятый сезон.

В этой конкретной франшизе все пять элементов имеют тип `TV`, поэтому `sort_order = 3` безопасно указывает на Food Wars S3. Его идентифицирующие поля:

- ID `5150`;
- alias `shokugeki-no-souma-san-no-sara`;
- `name.main = "Повар-Боец Сома 3"`;
- `name.english = "Shokugeki no Souma: San no Sara"`;
- `year = 2017`;
- `season.value = "autumn"` — календарная осень, не номер 3;
- `episodes_total = 24`;
- полный релиз содержит 24 эпизода с ordinal `1..24`.

У S3E6:

- episode ID `95bb6e5b-789e-11ec-ae92-0242ac120002`;
- `ordinal = 6`;
- `release_id = 5150`;
- `duration = 1470` секунд;
- opening `74–163` секунд;
- доступны HLS 480p и 720p, HLS 1080p отсутствует;
- ending для этой серии не размечен.

Это одновременно объясняет отсутствие AniLibria в прежнем списке источников: поиск только по общему английскому `Food Wars` даёт пустой массив, а выбор лучшего совпадения по общему русскому/romaji-названию склонен выбрать первый сезон.

## Рекомендуемый безопасный алгоритм

Когда известен `SeasonInfo.seasonNumber`, но нет точного названия сезона:

1. Искать последовательно по доступным русскому, основному и romaji/английскому алиасам; объединять кандидатов по `id`, а не принимать первый ответ.
2. Для кандидатов с сильным совпадением вызвать `/anime/franchises/release/{releaseId}`.
3. Сопоставлять локальные общие названия прежде всего с `franchise.name` и `franchise.name_english`, а не с названием отдельного релиза. Это позволяет выбрать правильную франшизу, не закрепляясь на её первом сезоне.
4. Внутри выбранной франшизы упорядочить `franchise_releases` по `sort_order` и построить список только тех типов релизов, которые считаются сезонами в локальной модели. Нельзя слепо использовать `sort_order == seasonNumber`, если между сезонами присутствуют `MOVIE`, `OVA`, `OAD` или `SPECIAL`.
5. Кандидат выбранного номера дополнительно валидировать:
   - тип соответствует сезонному формату;
   - `episodes_total` совместим с `SeasonInfo.totalEpisodes` или `SeasonInfo.episodes`;
   - название сохраняет токены выбранной франшизы;
   - после загрузки полного релиза существует эпизод с нужным `ordinal`;
   - релиз не заблокирован через `is_blocked_by_geo` или `is_blocked_by_copyrights`, если это влияет на доступность.
6. Только после уникальной валидации загрузить `/anime/releases/{idOrAlias}`, выбрать эпизод по `ordinal` и сформировать варианты HLS. При неоднозначности вернуть пустой результат, а не молча откатиться к первому сезону.

Для Food Wars этот поток даёт: общий romaji-запрос → франшиза `0746...` → третий TV-элемент → release `5150` → episode ordinal `6`.

## Ограничение точности

API v1 не публикует в объекте релиза MAL/AniList ID и не документирует `sort_order` как номер сезона. Поэтому при одном только `seasonNumber` без названия, года, количества эпизодов или согласованной локальной политики фильтрации типов абсолютно универсальное сопоставление невозможно. Безопасное поведение — принять только однозначно подтверждённый кандидат и иначе не выдавать AniLibria-источник.
