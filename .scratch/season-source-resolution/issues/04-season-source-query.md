# TICKET-04: Запрос к источнику вместо `Anime?` — русские алиасы не теряются

## Status

DONE

## Objective

Заменить `Anime.scopedToSeason(seasonInfo): Anime?` явным типом запроса к источнику, который
несёт полный набор алиасов (включая русский), номер сезона и признак доказуемости сезона.

## User or system value

Одна функция сегодня ломает три источника из четырёх:

```kotlin
return copy(title = seasonTitle, titleEn = seasonTitle, titleRu = null, …)
```

`titleRu = null` отбирает у AnimeGo, Kodik и AniLibria единственный алиас, которым русский
каталог реально ищется. Подтверждено логом: AnimeGo искал `Food Wars! The Second Plate`
в русском каталоге и не нашёл ничего.

Второй дефект той же функции — возврат `null` для сезона ≥2 без `title`, который **полностью
выключает** AnimeGo (`if (seasonAnime != null)` в `SourceEngine`) и yummy-путь Kodik
(`val target = seasonAnime ?: return@async emptyList()`). Строки без `title` создаёт
`mergeSeasonDiscovery` — то есть каждый сезон, добранный кнопкой «Найти ещё».

## Dependencies

—

## Scope

- Новый тип в `media/source/`: набор алиасов в порядке приоритета + номер сезона каталога +
  признак «сезон идентифицируем по названию».
- Сезонное название **добавляется** в начало списка алиасов, а не заменяет их (R2 спеки).
- Отсутствие сезонного названия больше не даёт `null`: источник получает франшизные алиасы
  и номер сезона, а признак доказуемости выставляется в «не доказуем» (R3 спеки).
- Обновить вызывающих: `SourceEngine.resolveRu`, `KodikSource.resolveEpisode`,
  `AniLibriaSource.resolveEpisode`, `AnimeGoSource.resolveEpisode`.
- Убрать ветку `if (seasonAnime != null)` вокруг AnimeGo в `SourceEngine`.

## Out of scope

- Лестницы внутри источников (TICKET-05/06/07).
- Трансляция номера сезона (TICKET-08).
- `Anime` как модель данных не меняется.

## Acceptance criteria

- [ ] Русское название доступно источнику при любом номере сезона
- [ ] Сезонное название стоит первым в приоритете алиасов
- [ ] Сезон без `title` не выключает ни один источник
- [ ] Признак доказуемости сезона выражен явно, а не через `null`
- [ ] AnimeGo попадает в список источников всегда
- [ ] `SeasonSourceAnimeTest` переписан под новый тип, старые проверки смысла сохранены
- [ ] Ни один источник не начал искать без сужения там, где сужение было

## Verification plan

```
./gradlew :app:testDebugUnitTest --tests "*SeasonSourceAnime*"
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```

## TDD classification

REQUIRED — чистая функция сопоставления, шов `SeasonSourceAnimeTest.kt` уже есть.

## Expected architecture impact

Закрывает п. 2.1 архитектурного обзора: `Anime?` перестаёт кодировать три разных смысла.
Тип `internal`, за пределы модуля не выходит. Интерфейс сужается, сложность прячется.

## Risks

- Регрессия точности: возврат русского алиаса может снова дать матч на франшизное название
  вместо сезонного — ровно то, что `scopedToSeason` пытался предотвратить. Митигация:
  порядок алиасов (сезонное первым) + порог `TitleMatcher` остаётся на месте, и ступени
  источников обязаны проверять принадлежность сезону отдельно (TICKET-05/06/07).
- Широкий диффузный дифф по четырём источникам. Держать тикет чисто механическим: перенос
  сигнатур, без изменения логики выбора.

## Implementation notes

`Anime.scopedToSeason(SeasonInfo?): Anime?` заменён на
`Anime.seasonSourceQuery(SeasonInfo?): SeasonSourceQuery` — тип всегда непустой, три поля:

| Поле | Смысл |
|---|---|
| `anime` | алиасы для поиска; сезонное название в `title`/`titleEn`, франшизное русское в `titleRu` **сохранено** |
| `seasonNumber` | номер в шкале КАТАЛОГА, зажат снизу единицей |
| `seasonIdentifiable` | у сезона есть собственное название ⇒ принадлежность можно требовать доказательно |

Два дефекта прежней функции закрыты:

1. `titleRu = null` убрано. Русский алиас — единственный, которым AnimeGo, Kodik и AniLibria
   реально находят тайтл в русских каталогах.
2. Возврат `null` для сезона ≥2 без названия убран. Вместо выключения источника отдаётся
   запрос с франшизными алиасами и снятым `seasonIdentifiable`.

Вызывающие:

- `SourceEngine.resolveRu` — ветка `if (seasonAnime != null)` вокруг AnimeGo удалена,
  источник участвует всегда; AniLibria по точной ссылке получает `seasonQuery.anime`.
- `KodikSource.resolveEpisode` — ранний `return@async emptyList()` в yummy-пути удалён;
  оба пути получают `seasonQuery.anime`, номер берётся из `seasonQuery.seasonNumber`.
- `AniLibriaSource.resolveEpisode` — `findRelease` вызывается всегда, франшизный путь
  остаётся запасным. На сезонах без названия `findRelease` теперь вообще доходит до сети
  (раньше пропускался), а `releaseIdentifiesSelectedSeason` падает на ветку порядкового
  маркера — это и есть требование R3 спеки.

`seasonIdentifiable` пока только объявлен и проверяется тестами; потребителями станут
TICKET-05/06/07, где он решает, доступна ли неточная ступень лестницы.

## Deviations

- Planned: «набор алиасов» отдельным списком в типе запроса.
- Actual: алиасы остались внутри `Anime`, тип несёт его целиком.
- Reason: все четыре источника читают алиасы из `Anime` (`title`/`titleRu`/`titleEn`) и
  используют оттуда же `anilistId`/`malId`/`episodes`. Отдельный список потребовал бы
  переписать сигнатуры `AnimeGoSource.resolveEpisode`, `KodikDirectSearch.findEpisodeCandidates`
  и `AniLibriaSource.findRelease` — широкий дифф ради формы, при том что тикет прямо требует
  держать правку механической.
- Consequence: приоритет алиасов выражен порядком полей `Anime`, а не позицией в списке.
  Для источников это то же самое: они и так читают `title` первым.
- Follow-up: нет. Если TICKET-07 упрётся в порядок алиасов, список вводится там точечно.

## Review findings

- **Эквивалентность на сезоне 1 и без сезона** — проверено тестами `no season keeps the row
  untouched` и ветвью пустого названия: строка возвращается неизменной, как и раньше.
- **Риск из тикета «франшизный алиас перебьёт сезонный»** — порядок сохранён (сезонное
  название в `title`, которое источники читают первым), пороги матчера не тронуты.
  Доказательная проверка принадлежности сезону остаётся за тикетами 05–07, где для этого
  теперь есть явный флаг.
- **Расширение поверхности** — `SeasonSourceQuery` и `seasonSourceQuery` объявлены `internal`,
  за пределы модуля не выходят.
- **Архитектурный наблюдатель** — закрыт п. 2.1 обзора: `Anime?` перестал кодировать три
  смысла. Долга не добавлено, направление зависимостей не изменилось.

Блокирующих замечаний нет.

## Completion evidence

- Command: `./gradlew.bat :app:testDebugUnitTest` → `BUILD SUCCESSFUL`,
  агрегат: `tests=273 failures=0 errors=0` (было 269 — добавлено 4 теста, минус 2 старых,
  плюс 1 из TICKET-02)

Файлы: `media/source/SeasonSourceAnime.kt` (переписан), `media/source/SourceEngine.kt`,
`media/source/KodikSource.kt`, `media/source/AniLibriaSource.kt`,
`test/.../SeasonSourceAnimeTest.kt` (переписан).
