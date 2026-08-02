# Журнал выполнения — резолв сезонов по источникам

## 2026-08-01 — Первичное обследование кодовой базы

### Relevant modules

- `media/source/` — `SourceEngine`, `JutSuSource`, `JutSuEpisodePageParser`, `JutSuTitleSearch`,
  `KodikSource`, `KodikDirectSearch`, `AniLibriaSource`, `AnimeGoSource`, `SeasonSourceAnime`,
  `SkipReferencePropagation`, `VetroHttpSource`, `VetroModels`
- `domain/seasons/` — `SeasonEpisodesResolver`, `StreamingSeasonDiscovery`, `SeasonEpisodesModels`
- Вызывающие: `MediaGatewayImpl`, `StreamPlayerActivity.episodeResolver`,
  `EpisodeMenuViewModel.forSeason`, `SeasonBatchDownloader`

### Existing behavior

`resolveHosters(anime, episode, seasonInfo, language)` — единственная точка входа. RU-путь:
сначала «точные» источники по сохранённым `WebLinks`, затем нативные (AniLiberty, AnimeGo,
Kodik, jut.su) параллельно, затем прямой URL-фолбэк. Результат нормализуется
`withPropagatedSkipReference().playableHosters()`.

### Existing terminology

`SeasonInfo.seasonNumber` — позиция в цепочке франшизы AniList после перенумерации
(`seasons.mapIndexed { i, s -> s.copy(seasonNumber = i + 1) }`). `SeasonInfo.title` —
`node.titleEnglish ?: node.titleRomaji`, английское. У строк от источников просмотра
(`mergeSeasonDiscovery`) `title = null`.

### Existing tests

`AniLibriaSeasonSelectionTest`, `AniLibriaV1ParserTest`, `JutSuEpisodePageParserTest`,
`JutSuTitleSearchTest`, `KodikSeasonSelectionTest`, `SeasonSourceAnimeTest`,
`SkipReferencePropagationTest`, `VideoRankingTest`, `StreamingSeasonRefreshTest`.

Пробелы: у `AnimeGoSource` тестов нет вовсе; `JutSuEpisodePageParserTest` кормится
синтетическим фикстуром, реальной разметки jut.su в тестах нет.

### Constraints discovered

- `UiStrings` — жёсткий лимит 255 полей, превышение роняет release (`VerifyError` в clinit).
- `R`-класс только `com.phnem.vetro.R`.
- Рабочее дерево содержит обширные несвязанные правки пользователя.
- Удалённые файлы под `.claude/skills/` трогать нельзя.

### Questions answerable from code

- Кто и как строит `Anime` для резолва — `EpisodeMenuViewModel.forSeason` и
  `StreamPlayerActivity.episodeResolver`, оба подменяют `title` на сезонное, `titleRu` сохраняют.
- Почему AnimeGo иногда отсутствует в выдаче — `if (seasonAnime != null)` в `SourceEngine`.
- Почему Kodik теряет кандидатов — `selectKodikSerialEpisodeLink` считает карту `seasons`
  авторитетной и индексирует её каталожным номером.

### Remaining material uncertainties

1. **BLOCKING** — почему реальная страница jut.su отдаёт base64-конфиг плеера, но ноль
   `<source>`. Снимается TICKET-02.
2. **SAFE_DEFAULT** — «endpoint» как ступень кода, а не тумблер настроек. Решено, см. Decisions.
3. **SAFE_DEFAULT** — трансляция номера сезона по числу серий с перебором соседей как запасным.
4. **DEFERRED** — поднимать ли `CURRENT_SCHEMA`; решается в TICKET-08.

## 2026-08-01 — Диагностика по двум логам устройства

### Что дал первый лог (18:37)

Только две попытки, обе по «Повар-боец Сома»:

- `S4E2` → `season-4/episode-2.html` → 200 → 0 qualities, reference=true
- `S6E1` → `season-6/episode-1.html` → 404; Kodik без кандидатов; AniLibria впустую

Первичная гипотеза: сплошной сдвиг нумерации после расколотого кора, «после S3 всё сыпется».

### Что опроверг второй лог (20:01) — ключевой поворот

Пользователь сообщил, что S5 играется, а S2 — нет. Это несовместимо с монотонным сдвигом.
Второй лог показал три попытки подряд:

```
season-2/episode-2.html → 200 → 0 qualities, reference=true
season-2/episode-3.html → 200 → 0 qualities, reference=true
season-2/episode-4.html → 200 → 0 qualities, reference=true
No Kodik candidates for 'Food Wars! The Second Plate' ep=2 / ep=3 / ep=4
animego.me/search/all?q=Food+Wars!+The+Second+Plate → 200 → пусто
anilibria: 3 поиска + обход франшиз 421/2628/5150/8510/8620 → пусто
```

**URL сезона здесь правильный**: jut.su season-2 = The Second Plate. Значит гипотеза о сдвиге
нумерации объясняет только случай S6 (404), но не S2.

### Опровергнутые гипотезы

| Гипотеза | Опровержение |
|---|---|
| «Всё после S3 сыпется из-за сдвига нумерации» | S2 ломается при заведомо правильном URL |
| «`scopedToSeason` вернул `null` и выключил AnimeGo/yummy» | `seasonInfo.title` = «Food Wars! The Second Plate» непустое, значит `scopedToSeason` отработал и AnimeGo запускался — в логе виден его запрос |

### Установленные причины, по одной на источник

| Источник | Причина | Тикет |
|---|---|---|
| jut.su | страница 200, base64-конфиг парсится (`reference=true`), но `parsed.sources` пуст; корень не установлен | 02 → 03 |
| Kodik | `selectKodikSerialEpisodeLink` индексирует карту `seasons` каталожным номером; у сиквела-релиза своя нумерация | 05 |
| AniLibria | `releaseIdentifiesSelectedSeason` требует точного равенства **английскому** названию сезона; названия релизов русские ⇒ предикат ложен всегда для сезона ≥2 | 06 |
| AnimeGo | ищет русский каталог английским названием, потому что `scopedToSeason` ставит `titleRu = null` | 04 → 07 |
| Все (S6) | каталожный номер сезона подставляется в источник без трансляции | 08 |

### Почему это не поймали тесты

Швы есть у всех источников, но `JutSuEpisodePageParserTest` работает с синтетической
разметкой вида `<source src="…" label="720p" res="720"/>`, которая парсится успешно.
Реальная страница в тестах не представлена. У `AnimeGoSource` тестов нет вовсе.

### Побочные наблюдения (вне объёма)

- `api.jikan.moe` отвечает 504 на всех запросах в обоих логах.
- Резолв титульной страницы jut.su через `lookfor` стоит до 10 секунд: первый запрос с
  дефисом уходит на JS-страницу поиска, и только вторая попытка без пунктуации даёт 301.
  Повторяется на каждую серию — кандидат на кэш.
- `SupabaseSync`: `postgresChangeFlow after joining the channel`; `Push anime failed:
  null value in column "media_type"`; `Passphrase bootstrap failed: RLS policy`.

### Next eligible ticket

TICKET-01 (готовый фронт: 01, 02, 04, 06)

## 2026-08-01 — Прогон тикетов 01, 02, 04–08

### Outcome

Семь тикетов закрыто, один остановлен на решении пользователя, один заведён как продолжение.

| ID | Итог | Тесты после |
|---|---|---|
| 01 | DONE | 269 |
| 02 | DONE_WITH_DEVIATIONS | 270 |
| 04 | DONE | 273 |
| 05 | DONE_WITH_DEVIATIONS | 277 |
| 06 | DONE_WITH_DEVIATIONS | 279 |
| 07 | DONE | 289 |
| 08 | DONE_WITH_DEVIATIONS | 299 |

### Главный поворот: jut.su (TICKET-02)

Спайк опроверг саму постановку TICKET-03. Реальная страница отдаёт четыре `<source>`, у всех
`src` — `pixel.png?<res>` с `type="video/mp4"`; во всём документе ноль вхождений `.mp4` и
`m3u8`, признак `jutsu_new_player = "yes"`. Ноль видео — правильный разбор, а не дефект.
Снято дважды, включая полноценную сессию с cookie-jar. jut.su остался источником таймскипов.

Следствие: вся нагрузка по видео перешла на Kodik, AniLibria и AnimeGo, то есть тикеты 04–07
из вспомогательных стали основными.

### Инвариант пересилил букву плана трижды

Плановые «неточные ступени» не реализованы там, где они возвращали бы исходный дефект:

- **05** — перебора сезонов карты Kodik нет: он срабатывал бы ровно при недоказуемом сезоне
  и почти всегда отдавал бы серию первого.
- **06** — нечёткая ступень не добавлена (пропускала бы усечённое название соседнего сезона,
  что прямо запрещает существующий тест), `singleOrNull` не ослаблен.
- **08** — недоказуемое сопоставление шкал отказывает, а не угадывает.

Каждый отказ закреплён отрицательным тестом.

### Корневые причины, подтверждённые правками

| Источник | Что чинилось |
|---|---|
| Kodik | карта `seasons` индексировалась каталожным номером; у сиквела-релиза свой ключ «1» |
| AniLibria | непустое сезонное название обрывало проверку на точном равенстве, не пуская к порядковому маркеру, который единственный работает для русских релизов |
| AnimeGo | русский каталог опрашивался английским названием: сужение обнуляло `titleRu` |
| Все | `scopedToSeason` возвращал `null` для сезона ≥2 без названия и выключал AnimeGo с yummy-путём Kodik |

### Verification

`./gradlew.bat :app:testDebugUnitTest` → `tests=299 failures=0 errors=0`;
`./gradlew.bat :app:assembleDebug` → `BUILD SUCCESSFUL`. Ручная проверка на устройстве
не выполнялась ни по одному тикету.

### New risks

Индексная зависимость осталась во франшизном пути AniLibria — выдать чужой сезон не может
(подтверждение из TICKET-06 обязательно), но теряет резолвы. Вынесено в TICKET-09.

### Next eligible ticket

TICKET-03 после решения пользователя; TICKET-09 доступен независимо.
