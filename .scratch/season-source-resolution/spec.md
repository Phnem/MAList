# Резолв сезонов: восстановить выдачу видео по всем источникам

## Problem

После серии правок в `media/source/` и `domain/seasons/` часть сезонов перестала резолвиться
вообще — ни один из четырёх RU-источников не отдаёт ни одного играбельного видео. Пользователь
получает тост «нет источников» на сезонах, которые раньше игрались.

Подтверждено логами устройства (SM-S936B, 2026-08-01) на тайтле «Повар-боец Сома»
(Food Wars / Shokugeki no Souma).

### Доказательства: S2E2/E3/E4 — «Food Wars! The Second Plate»

```
jut.su/shokugeki-no-souma/season-2/episode-2.html → 200 → 0 qualities, reference=true
jut.su/shokugeki-no-souma/season-2/episode-3.html → 200 → 0 qualities, reference=true
jut.su/shokugeki-no-souma/season-2/episode-4.html → 200 → 0 qualities, reference=true
KodikSource: No Kodik candidates for 'Food Wars! The Second Plate' ep=2 / ep=3 / ep=4
animego.me/search/all?q=Food+Wars!+The+Second+Plate → 200 → пусто (молча)
anilibria: search «Food Wars! The Second Plate» → «Повар-боец Сома» → «Food Wars!»
          → franchises/release/{421,2628,5150,8510,8620} → пусто
SourceEngine: Resolved 0 hosters / 0 videos for Food Wars! The Second Plate S2E2 [RU]
```

**URL сезона здесь ПРАВИЛЬНЫЙ**: на jut.su season-2 — это ровно The Second Plate. Значит
основная причина отказа для S2 не в нумерации сезонов, а в четырёх независимых поломках.

### Доказательства: S6E1 — «Food Wars! The Fifth Plate»

```
jut.su/shokugeki-no-souma/season-6/episode-1.html → 404 → No sources ... season=6
```

Здесь уже нумерация: каталог (обход франшизы AniList) считает Totsuki Train Arc отдельным
сезоном, поэтому у него S1..S6, а у jut.su/Kodik/AniLibria тот же тайтл разложен на 5 сезонов.

### Доказательства: S4E2 — «The Third Plate: Totsuki Train Arc»

```
jut.su/shokugeki-no-souma/season-4/episode-2.html → 200 → 0 qualities, reference=true
```

Двойной отказ: и номер сезона указывает на чужой контент (jut.su season-4 = The Fourth Plate),
и `<source>` всё равно не извлеклись.

## Current behavior

### jut.su

Страница серии загружается (200), `JutSuEpisodePageParser` успешно достаёт base64-конфиг плеера
(`reference=true` ⇒ распарсились и тайминги, и `this_video_duration`), но `parsed.sources`
пуст. То есть HTML — настоящая страница серии, а не заглушка; не срабатывает именно извлечение
`<source>`.

`JutSuEpisodePageParserTest` кормится **синтетическим** фикстуром вида
`<source src="https://video.jut.su/death-note-1.mp4" label="720p" res="720"/>` — он проходит,
поэтому регрессия не поймана тестом. Реальная разметка страницы в тестах не представлена.

Корень пока не установлен. Кандидаты:
1. Реальная разметка отличается от фикстура (атрибуты, вложенность, порядок).
2. `isPlayableJutSuVideoUrl` отбраковывает настоящие URL (проверка расширения/MIME/плейсхолдеров).
3. jut.su перестал отдавать `<source>` в статическом HTML и собирает их скриптом.

### Kodik

`selectKodikSerialEpisodeLink` объявляет карту `seasons` авторитетной и берёт из неё строго
`linksBySeason[<номер сезона приложения>]`. У Kodik сиквел — как правило отдельный релиз со
своей нумерацией (`seasons: {"1": …}`), поэтому запрос второго сезона по ключу `2` не находит
ничего и кандидат выбрасывается. Прежний `episodeLink()` перебирал все сезоны и находил серию.

Вторая, независимая причина: поиск идёт по `seasonAnime`, у которого `titleRu = null`
(см. ниже), то есть по английскому названию — в русском каталоге Kodik оно часто не проходит
порог `TitleMatcher.MATCH_THRESHOLD = 0.85`.

### AnimeGo

Ищет по `seasonAnime.title` — английскому названию сезона. AnimeGo — русский каталог. Русский
алиас до него не доезжает, потому что `scopedToSeason` его обнуляет. Источник молча отдаёт пусто.

### AniLibria

`releaseIdentifiesSelectedSeason` при непустом `seasonInfo.title` требует **точного равенства**
нормализованного названия релиза английскому названию сезона из AniList. У AniLibria названия
русские («Повар-боец Сома 2»), поэтому предикат ложен всегда для сезона ≥2. Он гасит и обычный
`findRelease(requiredSeason)`, и франшизный путь `selectAniLibriaSeasonRelease`.

Франшизный путь дополнительно fail-closed: `.getOrNull(seasonNumber - 1)` по индексу,
`matches.singleOrNull()`, обязательное совпадение `episodes_total` с ожидаемым и фильтр
`ANILIBRIA_SEASON_TYPES = {"TV"}`.

### Общее: `scopedToSeason`

```kotlin
internal fun Anime.scopedToSeason(seasonInfo: SeasonInfo?): Anime? {
    if (seasonInfo == null) return this
    val seasonTitle = seasonInfo.title?.trim().orEmpty()
    if (seasonTitle.isEmpty()) return takeIf { seasonInfo.seasonNumber <= 1 }
    return copy(title = seasonTitle, titleEn = seasonTitle, titleRu = null, …)
}
```

Две проблемы:

1. `titleRu = null` — у трёх русских источников из четырёх отбирается единственный алиас,
   которым они реально ищут.
2. Возврат `null` для сезона ≥2 без `title` полностью выключает AnimeGo
   (`if (seasonAnime != null)` в `SourceEngine`) и yummy-путь Kodik
   (`val target = seasonAnime ?: return@async emptyList()`). Строки сезонов без `title`
   создаёт `mergeSeasonDiscovery` — то есть всё, что добрала кнопка «Найти ещё».

### Общее: нумерация сезонов

`SeasonEpisodesResolver` нумерует сезоны сплошным индексом по цепочке франшизы AniList
(`seasons.mapIndexed { i, s -> s.copy(seasonNumber = i + 1) }`). Источники просмотра нумеруют
по-своему: расколотые коры они обычно держат внутри одного сезона. Номер передаётся в источники
как есть, без трансляции. `mergeSeasonDiscovery` и `mergeStreamingSeasons` сливают найденное
у источников тоже по номеру, из-за чего счётчики серий приписываются чужим сезонам.

## Desired outcome

Сезон, который физически доступен хотя бы у одного источника, резолвится и играется. Отказ
одного источника не превращается в отказ всей выдачи. При этом сохраняется уже принятый
инвариант: **никогда не подсовывать серию чужого сезона молча**.

## Required behavior

### R1. Лестница стратегий на источник

Каждый источник получает упорядоченную лестницу стратегий («endpoints»). Лучшая стратегия —
первая. Если она не дала играбельного результата, пробуется следующая. Лестница обрывается на
первом успехе.

Требования к лестнице:

- каждая ступень независимо покрыта юнит-тестом на существующем шве;
- ступень либо возвращает результат запрошенного сезона, либо не возвращает ничего;
- ступень, которая не может доказать принадлежность сезону, помечается как «неточная» и
  ставится ниже точных;
- лестница не делает дополнительных сетевых запросов, пока предыдущая ступень не отработала.

### R2. Русские алиасы не теряются

Сужение до сезона обязано ДОБАВЛЯТЬ сезонное название к алиасам, а не ЗАМЕНЯТЬ ими набор.
Русское название тайтла остаётся доступно источникам всегда.

### R3. Отсутствие сезонного названия не выключает источник

Строка сезона без `title` (пришедшая от источника просмотра) не должна отключать AnimeGo
и yummy-путь Kodik. Источник получает франшизные алиасы плюс номер сезона.

### R4. Трансляция номера сезона

Номер сезона в шкале каталога транслируется в шкалу конкретного источника прежде, чем попасть
в URL или в ключ карты. Если трансляция не доказуема — источник отдаёт пусто, а не чужой сезон.

### R5. Наблюдаемость отказа

По логу одного запуска должно быть видно, какой источник чем закончил и на какой ступени
лестницы остановился. Сегодня AnimeGo и AniLibria молчат полностью.

## User-visible behavior

- Сезоны «Повар-боец Сома» S2–S6 играются.
- Тост «нет источников» остаётся только там, где сезона действительно нет ни у кого.
- Никакой сезон не начинает играть контент другого сезона.

## Domain rules

- **Номер сезона каталога** — позиция в цепочке франшизы AniList после перенумерации.
- **Номер сезона источника** — то, чем сезон называет конкретный сайт.
- Эти две величины не равны и не обязаны быть равны. Отождествление — дефект.
- `SeasonInfo.title` — английское название из AniList (`titleEnglish ?: titleRomaji`), у строк
  от источников просмотра — `null`.

## Functional requirements

| ID | Требование |
|---|---|
| F1 | jut.su извлекает видео с реальной страницы серии |
| F2 | jut.su имеет запасные ступени извлечения, если основная не сработала |
| F3 | `scopedToSeason` сохраняет русское название |
| F4 | `scopedToSeason` не возвращает `null` — источник всегда получает пригодный набор алиасов |
| F5 | Kodik находит серию, когда релиз нумерует сезоны по-своему |
| F6 | AniLibria опознаёт сезон без требования точного равенства английскому названию |
| F7 | AnimeGo ищет по русскому алиасу |
| F8 | Номер сезона транслируется каталог → источник |
| F9 | Каждый источник логирует исход резолва |

## Non-functional requirements

- Бюджеты таймаутов `SourceEngine` не растут: лестницы ступеней укладываются в текущие
  `SOURCE_TIMEOUT_MS` / `KODIK_SOURCE_TIMEOUT_MS`.
- Дополнительные ступени не выполняются, пока не понадобились.
- Никаких новых полей в `UiStrings` (лимит 255, см. память проекта).

## Compatibility and migration constraints

- Формат `SeasonEpisodesEntry` меняется только при необходимости; при изменении семантики
  `seasons` поднимается `CURRENT_SCHEMA` (сейчас 2).
- Уже сохранённые `WebLinks` на `jut.su` остаются валидны.
- Зеркало jut.su (`mirrorProvider`) продолжает работать.

## Failure and fallback behavior

- Источник, упавший или не уложившийся в таймаут, выпадает из выдачи, остальные продолжают.
- Пустая выдача всех источников → прежний тост, но с логом причины по каждому.
- Недоказуемая принадлежность сезону → пусто. Это сильнее, чем «сыграть хоть что-то».

## Out of scope

- EN-путь (`resolveEn`, AnimeHeaven) — кроме случаев, где правка общая.
- Скачивание серий (`SeasonBatchDownloader`) — использует тот же `resolveHosters`, чинится
  автоматически, отдельных правок не планируется.
- Переработка UI выбора сезона.
- `mihon-compat`, манга, локальный плеер.
- Общая переработка `SeasonEpisodesResolver` (обход франшизы) — только трансляция номера.

## Acceptance criteria

- [ ] «Повар-боец Сома» S2E2 отдаёт хотя бы одно играбельное видео
- [ ] «Повар-боец Сома» S6E1 отдаёт хотя бы одно играбельное видео либо мотивированный отказ
- [ ] Ни один сезон не отдаёт видео другого сезона
- [ ] Каждая ступень каждой лестницы покрыта юнит-тестом
- [ ] В логе одного запуска виден исход по каждому из четырёх источников
- [ ] `./gradlew :app:testDebugUnitTest` зелёный (кроме уже падавшего `StatsRatingBucketTest`)

## Open questions

| # | Вопрос | Класс | Решение |
|---|---|---|---|
| 1 | Почему реальная страница jut.su не отдаёт `<source>` | BLOCKING | снимается TICKET-02 (спайк) |
| 2 | «Другие варианты как endpoint» — ступени кода или переключатели в настройках? | SAFE_DEFAULT | ступени кода: лестница стратегий внутри источника. Пользовательских тумблеров не заводим — их нечем осмысленно выбирать. Изменяемо позже без переделки: ступени уже разделены. |
| 3 | Нужна ли трансляция номера через сопоставление числа серий или достаточно перебора соседей | SAFE_DEFAULT | сопоставление по числу серий как основная ступень, перебор соседей — запасная. Обе проверяемы юнит-тестом. |
| 4 | Поднимать ли `CURRENT_SCHEMA` | DEFERRED | решается в TICKET-08, когда станет ясно, меняется ли содержимое `seasons` |

## Test seams

Швы уже существуют, все ступени тестируемы без сети:

| Шов | Файл теста |
|---|---|
| `JutSuEpisodePageParser.parse` | `JutSuEpisodePageParserTest.kt` |
| `selectJutSuSearchResponse` / `jutSuSearchQueries` | `JutSuTitleSearchTest.kt` |
| `selectKodikSerialEpisodeLink` / `selectKodikEpisodeLink` | `KodikSeasonSelectionTest.kt` |
| `selectAniLibriaSeasonRelease` / `releaseIdentifiesSelectedSeason` | `AniLibriaSeasonSelectionTest.kt` |
| `Anime.scopedToSeason` | `SeasonSourceAnimeTest.kt` |
| `mergeSeasonDiscovery` / `refreshSeasonDiscovery` | `StreamingSeasonRefreshTest.kt` |
| `playableHosters` / `withPropagatedSkipReference` | `SkipReferencePropagationTest.kt` |

Пробел: ни один тест не работает с реальной разметкой jut.su. Закрывается TICKET-02.
