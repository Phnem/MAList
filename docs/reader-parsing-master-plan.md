# Vetro — сводный план: ридер манги, UX деталей, парсинг

Дата сведения: 2026-07-28. Источники: `manga_reader_ux_plan.md`, `manga_reader_ux_plan-1.md`,
`parsing_improvements_plan.md` (× 2 копии — идентичны, слиты в одну).

Стек: Kotlin, Jetpack Compose, Coil 3, Media3/ExoPlayer, DataStore Preferences (прогресс),
SQLDelight (коллекция), Koin, ktor + OkHttp. Пакет `com.example.myapplication`, R-класс
`com.phnem.vetro.R`.

Референсы распакованы и проверены: `Animeref.zip` (15 файлов — Kodik/AnimeGo/jut.su парсеры,
`tokens.json`, `KODIK_API.md`), `Mangaref.zip` (41 файл — исходники Kotatsu).

---

## 0. Расхождения планов с реальным кодом

Проверено чтением кода, а не по описанию. Планы писались раньше — часть утверждений устарела.
Эти поправки меняют объём тикетов, поэтому вынесены наверх.

| Утверждение в плане | Реальность | Следствие |
|---|---|---|
| «Кнопки избранного в проекте нет вообще, целиком новая фича» | **Неверно.** `isFavorite` есть сквозь весь стек: колонка `anime.isFavorite` в `Anime.sq:8`, поле в `Anime.kt:19`, `AnimeRepository.toggleFavorite()` (`AnimeRepository.kt:90`), синк в Supabase (`SyncRepository.kt:20`), тумблер в AddEdit | Тикет сжимается до одной кнопки в Details поверх готового toggle |
| «Кнопка назад в ридере — переиспользовать компонент» | Кнопка **уже есть** — `ReaderChrome`, `MangaReaderScreen.kt:344` | Остаётся только backStack-таргет |
| «Слайдер страниц — новый компонент» | M3 `Slider` **уже есть** — `MangaReaderScreen.kt:404`, уже в BrandOrange | Это замена стиля, а не новая механика |
| «Иконка режима чтения — новое» | Тумблер Paged/Webtoon **уже есть** — `MangaReaderScreen.kt:366` | Расширяется третьим измерением (direction) |
| «Проверить, есть ли поле страны/языка оригинала» | Полей **нет**: в `Anime` только id/title/titleEn/titleRu/episodes/rating/tags/anilistId/malId/shikimoriId/mediaType | Дефолт direction = `Rtl`, автодетект по метаданным отпадает |
| «Формат/Источник/Студия подтягиваются обогащением» | Колонок **нет** в `Anime.sq` | Карточки «Информация» требуют сначала схему + обогащение |

Ещё одна находка, важная для Тикета A1: плеер в `StreamPlayerActivity` пересоздаётся по
`remember(current.url, current.resolvedAt)` (строка 152). Значит **same-URL retry делается
бампом `resolvedAt` без смены url** — `current = current.copy(resolvedAt = now())`. Отдельный
механизм пересборки писать не нужно.

---

## 1. Волна A — быстрые изолированные правки

### A1. Same-URL retry перед fallback в плеере

**Сейчас.** `StreamPlayerActivity.onPlayerError` (строка 249): при любой автоматической попытке
(`automaticRetries < MAX_AUTOMATIC_RETRIES`) сразу вызывается `refreshStream()`, который берёт
`candidates[nextIndex]`. Ту же ссылку повторно не пробует ни разу.

**Требование.** Первая автоматическая попытка — retry того же `current.url`. Только при повторной
ошибке на той же ссылке — нынешняя логика (следующий кандидат → `resolveReplacement()`).

**Реализация.** В `onPlayerError`, после ветки `manualSwitchFallback != null` (её не трогать):

```kotlin
if (automaticRetries == 0) {
    automaticRetries = 1
    resumePosition = player.currentPosition.coerceAtLeast(0L)
    playbackError = null
    current = current.copy(resolvedAt = System.currentTimeMillis()) // тот же url → пересборка
    return
}
```
`currentIndex`/`candidates` не трогаются. `onPlaybackStateChanged(STATE_READY)` уже сбрасывает
счётчик — оставить.

**Приёмка.** Первая ошибка → `prepare()` на том же URL, `candidates`/`currentIndex` неизменны.
Вторая ошибка на той же ссылке → следующий кандидат. Восстановление сбрасывает счётчик. Ручное
переключение рендишна не затронуто.

**Не делать.** Не менять `MAX_AUTOMATIC_RETRIES`, не трогать тело `resolveReplacement()`.

**Файлы.** `media/ui/StreamPlayerActivity.kt`

---

### A2. Дропдаун качества — единая blur-поверхность

**Сейчас.** `EpisodeQualitySheet.kt`: список из отдельных пилюль (`QualityPill`), зазор
`QualityMenuGap = 8.dp`, сплошной непрозрачный `pillColor` без блюра, на выбранном — оранжевая
обводка + подложка.

**Требование.** Одна цельная скруглённая полупрозрачная поверхность с блюром (переиспользовать
существующий backdrop-паттерн проекта — `AdaptiveGlassProfile`/`GlassBackdropRecovery`, не писать
новый). Внутри — строки, разделённые `HorizontalDivider` alpha ≈ 0.08–0.12. На выбранном — только
галочка `Icons.Rounded.Check`.

**Реализация.** Убрать per-row `background`/`border`; обернуть список в один контейнер; убрать
`QualityMenuGap` из `menuHeightPx` → `state.options.size * rowPx + arrowPx`. Позиционирование
(`anchor`, стрелка, `AnimatedVisibility`) не трогать.

⚠️ Гвоздь из памяти проекта: **никогда не добавлять/удалять структурно модификатор над
`layerBackdrop`** — переключать свойства, контент двигать через `.offset{}`, не `graphicsLayer`.

**Приёмка.** Цельная blur-карточка, разделители вместо зазоров, только галочка. Анимация и
позиционирование по anchor — без регрессий.

**Файлы.** `ui/details/EpisodeQualitySheet.kt`

---

### A3. Кэш сдвига Caesar-шифра в Kodik

**Сейчас.** `KodikSource.kt:358` `decodeKodikSource()` — чистый перебор `for (shift in 0..25)` на
**каждый** элемент `sources`. При 3 качествах — до ~78 попыток `decodeBase64()` на эпизод, кэша
между вызовами нет (экстрактор создаётся заново на каждый iframe).

**Требование.** Кэшировать найденный сдвиг и пробовать его первым, брутфорс — только при промахе.
Алгоритм не меняется, меняется порядок попыток.

**Реализация.** `private object KodikCipherCache { @Volatile var lastShift: Int? = null }`,
вынести тело сдвига в `tryShift(shift)`, в цикле пропускать уже опробованный кэш, при успехе —
записать. Референс (`parser_kodik.py::_convert`) делает ровно это.

**Приёмка.** Первый вызов ведёт себя как раньше; последующие с тем же сдвигом декодируют за одну
попытку. Смена сдвига на стороне Kodik по-прежнему отрабатывает через полный перебор.

**Файлы.** `media/source/KodikSource.kt`

---

## 2. Волна B — источники парсинга

### B1. Зеркало домена jut.su

**Сейчас.** `JutSuSource.kt:29` — `baseUrl = "https://jut.su"` зашит; ещё есть проверка
`contains("jut.su")` на строке 37.

**Требование.** Прослойка смены домена одной настройкой (как параметр `mirror` в
`parser_jutsu.py`) — страховка на случай блокировки.

**Реализация.** Домен в изменяемый источник (константа-дефолт + переопределение из DataStore-ключа
в `DevPreferencesKeys`). Проверку `contains("jut.su")` на строке 37 сделать по активному домену,
иначе при зеркале известный URL тайтла перестанет распознаваться.

**Приёмка.** Без настройки поведение прежнее. С заданным зеркалом все запросы и проверка
`knownTitleUrl` идут на него.

**Файлы.** `media/source/JutSuSource.kt`, `data/local/DevPreferencesKeys.kt`

---

### B2. AnimeGo — второй бэкенд плеера CVH (CDNVideoHub)

**Сейчас.** `AnimeGoSource.kt:53` — `label = "720p", resolution = 720` **захардкожено**; `cvh`
в файле не встречается ни разу. Единственный путь — aniboom.

**Требование.** Параллельный путь через CVH: реальные качества вместо заглушки + независимый
источник там, где aniboom недоступен.

**Реализация** (из `parser_animego.py`, проверено):
- Константы: `_CVH_API_BASE = "https://plapi.cdnvideohub.com/api/v1/player/sv"`, `_PUB = "747"`,
  `_AGGR = "mali"`.
- `cvh_id` — из embed-ссылки: подстрока между `cdn-iframe/` и следующим `/`. Селектор аналогичен
  нынешнему `iframe[src*=aniboom]`.
- `GET {base}/playlist?pub=&aggr=&id={cvh_id}` → `items[]` с `season`/`episode`/`vkId`/
  `voiceStudio`/`voiceType`.
- `GET {base}/video/{vkId}` → `sources: { hlsUrl, dashUrl, url360, url480, url720, ... }` — каждое
  качество отдельным `VetroVideo` с честным `resolution`.
- Если в плейлисте один сезон — номер сезона из запроса игнорируется (так делает референс).
- Сопоставление студии озвучки с лейблом AnimeGO — fuzzy в три прохода: точное → подстрока в любую
  сторону → нет совпадения.
- Каждая озвучка — **отдельный `VetroHoster`**, как уже сделано для Kodik-дубляжей.

**Приёмка.** Для тайтла с cvh-переводом возвращаются несколько качеств с реальными
`resolution`. Aniboom-путь не затронут и работает как раньше. Оба пути агрегируются, а не
«первый успех — стоп».

**Файлы.** новый `media/source/CvhResolver.kt`, `media/source/AnimeGoSource.kt`

---

### B3. Прямой поиск через kodik-api.com

**Сейчас.** Единственный путь к Kodik — через YummyAnime (`api.yani.tv`, `findRelease()`).
Не проиндексировал тайтл / отдал неверный slug / недоступен → Kodik выпадает целиком, хотя сам
Kodik жив.

**Требование.** Второй независимый способ найти iframe, параллельно с YummyAnime.

**Реализация.**
- `POST https://kodik-api.com/search?token=…&title=…` → в ответе готовое поле `link` вида
  `//kodik.info/serial/7497/9ce…/720p` — **тот же формат iframe-URL**, который
  `KodikExtractor.resolve()` уже умеет разбирать. Экстрактор не трогаем.
- Токены — `tokens.json` из референса как ассет. Расшифровка (`TOKENS.md`, проверена):
  `p1 = b64decode(reverse(first half))`, `p2 = b64decode(reverse(second half))`, результат
  `p2 + p1` → обычный 32-символьный hex.
- Порядок тиров: `stable` → `unstable` → `legacy`, `dead` пропускать; внутри тира — приоритет по
  `functions_availability.search`. Найденный рабочий токен закэшировать на сессию.
- Запускать `async`/`awaitAll` вместе с YummyAnime, дедуп по `iframe_url` перед `KodikExtractor`.

**Приёмка.** Тайтл, которого нет в YummyAnime, но есть в Kodik, отдаёт озвучки. При падении одного
из двух путей второй продолжает работать. Дублей iframe в выдаче нет.

**Замечание.** Токены в `tokens.json` датированы 26 апреля и протухают. Апстрим-URL для
автообновления в референсе не указан — вшиваем ассетом, деградация мягкая (путь просто
отключается, YummyAnime продолжает работать).

**Файлы.** новый `media/source/KodikDirectSearch.kt`, `media/source/KodikSource.kt`,
`app/src/main/assets/kodik_tokens.json`

---

## 3. Волна C — ядро ридера

### C1. Режим чтения per-title + направление

**Сейчас.** `MangaReadingStore.kt:33` — `MangaReaderMode { Paged, Webtoon }` с KDoc «Настройка
глобальная: смена режима на каждый тайтл никому не нужна», один ключ `READER_MODE_KEY`.
**Это решение меняем на противоположное.**

**Требование.** Направление хранится per-title; `Paged` разделяется на направление; `Webtoon`
направления не имеет.

**Реализация.**
- `enum class PageDirection { Rtl, Ltr }` (Rtl = Классика, Ltr = Комикс).
- `MangaReaderMode` остаётся `{ Paged, Webtoon }` — механика рендера не меняется, направление —
  отдельное измерение, применимое только при `Paged`.
- В `ReadingSnapshot` добавить nullable `mode: MangaReaderMode? = null`,
  `direction: PageDirection? = null` (`null` = не задано → дефолт).
- `readerModeFlow()`/`setReaderMode()` → сигнатура с `animeId`, per-title snapshot. Добавить
  `directionFlow(animeId)`/`setDirection(animeId, …)`.
- **Обратная совместимость:** `READER_MODE_KEY` не удалять — дефолт для тайтлов без per-title
  значения (`snapshot.mode == null` → `READER_MODE_KEY` → иначе `Paged`).
- Дефолт `direction` — `Rtl`: поля страны/языка оригинала в модели тайтла нет (см. §0).
- ⚠️ В `MangaReaderScreen` пейджер обёрнут в `key(state.chapter.key, mode)` (строка 144) —
  `direction` **обязан войти в этот key**, иначе смена направления не пересоберёт пейджер.
- В `Paged` направление применяется через `CompositionLocalProvider(LocalLayoutDirection …)` вокруг
  `HorizontalPager` — порядок страниц не переворачивать вручную, иначе поедет прогресс.

**Приёмка.** Два тайтла с разными режимами не влияют друг на друга. Тайтлы без per-title значения
работают на старом глобальном дефолте. `Webtoon` не зависит от `direction`.

**Не делать.** Не переносить `direction` на уровень главы.

**Файлы.** `manga/data/MangaReadingStore.kt`, `manga/ui/MangaReaderViewModel.kt`,
`manga/ui/MangaReaderScreen.kt`

---

### C2. Автоопределение вебтуна

Из `DetectReaderModeUseCase.kt` (Kotatsu). Декодировать **только bounds**
(`inJustDecodeBounds = true`, без загрузки в память) страницы на 30 %-й позиции главы; если
`height > width × 1.8` — вебтун. Один запрос, никакой лишней сети. URL страницы уже есть через
`MangaPageResolver.pages()`.

Результат — **подсказка дефолта** для тайтла без явного выбора (пишется как per-title `mode` из
C1), а не принудительное переключение: явный выбор пользователя всегда важнее.

**Приёмка.** Длинная вертикальная полоса открывается вебтуном без ручного переключения. Ручной
выбор режима не перетирается автодетектом при следующем открытии.

**Файлы.** новый `manga/domain/DetectReaderMode.kt`, `manga/ui/MangaReaderViewModel.kt`

---

### C3. RegionBitmapDecoder — защита от OOM

Из `RegionBitmapDecoder.kt` (Kotatsu). Это буквально `coil3.decode.Decoder` через
`BitmapRegionDecoder`, с фолбэком на обычный декодер, если региональный недоступен. Подключается
компонентом в существующий `ImageLoader` (`VetroApplication.kt:44`), а не отдельной подсистемой.
Прямая защита от OOM и лимитов OpenGL-текстур на длинных вебтун-стрипах.

**Приёмка.** Очень длинная страница (высота > лимита текстуры) отображается без краша и без
пустого места. Обычные страницы декодируются как раньше.

**Файлы.** новый `manga/ui/RegionBitmapDecoder.kt`, `VetroApplication.kt`

---

### C4. Битая страница — inline retry

Coil3 `AsyncImage` `error`-слот → заглушка с кнопкой «Обновить», повторный `ImageRequest` с
cache-bust параметром. `Pager`/`LazyColumn` не ломается по построению — ошибка одного элемента не
влияет на остальные.

**Приёмка.** Страница, отдавшая 403/500, показывает заглушку с кнопкой; тап перезагружает только
её; соседние страницы не затронуты.

**Файлы.** `manga/ui/MangaReaderScreen.kt`

---

## 4. Волна D — UX ридера

### D1. Мини-док сверху справа

Стеклянный док с тремя переключателями: список глав, режим чтения, обрезка полей.
- Кнопка списка глав открывает **существующий** `IosBottomSheet.kt`
  (`ui/shared/components/`) с новым содержимым — компонент не пишется заново.
- Иконка режима чтения — на C1 (per-title режим + направление, три состояния: Вебтун / Классика /
  Комикс).
- Тумблер обрезки полей — на D3.

Материал и пружины — из канона `MotionTokens`/`IosDesign`, палитра только orange/mono.

**Файлы.** `manga/ui/MangaReaderScreen.kt`, новый `manga/ui/ReaderDock.kt`

---

### D2. Слайдер страниц

Не буквальный реюз слайдера рейтинга: общий визуальный материал (жидкое стекло, трек —
`ui/shared/components/rating/RatingTrackWidget.kt`), но другая модель значений.
- **Дискретный** `Int` (номер страницы) вместо непрерывного `Float` 0–10.
- Один тон brand orange вместо OKLAB-перехода между 5 состояниями.
- Haptic на **каждый снап** при drag, не continuous.

Общую часть стекла можно вынести в низкоуровневый примитив (`LiquidGlassTrack`) — чище, чем
копировать рейтинг-виджет и урезать половину логики.

Заменяет нынешний M3 `Slider` (`MangaReaderScreen.kt:404`).

**Файлы.** `manga/ui/MangaReaderScreen.kt`, новый общий примитив в `ui/shared/components/`

---

### D3. EdgeDetector — обрезка полей

Портируется из `EdgeDetector.kt` (Kotatsu) как Coil3-трансформация. Тумблер в доке включает и
выключает применение к текущей странице через настройку ридера.

**Файлы.** новый `manga/ui/EdgeCropTransformation.kt`, `manga/ui/ReaderDock.kt`

---

## 5. Волна E — полировка ридера

### E1. Префетчинг страниц
Паттерн `MangaPrefetchService.kt` (Kotatsu) — фоновый воркер, качающий страницы вперёд в
Coil-кэш. Адаптация: подключить к `MangaPageResolver` вместо Kotatsu-шного `MangaRepository`.

### E2. Backstack-таргет кнопки назад
Если ридер открыт из Details напрямую (кнопкой Play, минуя меню глав), back ведёт **в меню глав**,
а не `finish()`/pop по умолчанию. Кнопка уже существует (`MangaReaderScreen.kt:344`) — нужен
явный таргет.

### E3. Оффсет скролла для вебтуна (низкий приоритет)
`ChapterReadingProgress` + nullable `scrollOffsetFraction: Float?`. Постраничной точности почти
всегда достаточно — делать последним.

---

## 6. Волна F — Details и меню глав

### F1. Кнопка избранного в Details
**Сжатый тикет** (см. §0): backend готов целиком. Нужна третья кнопка-закладка рядом со
«Смотреть»/«Скачать» (`DetailsScreen.kt:435`), дёргающая существующий
`AnimeRepository.toggleFavorite()`.

### F2. Схема + обогащение: Формат / Источник / Студия
Колонок в `Anime.sq` **нет** — сначала схема (миграция SQLDelight) и наполнение через
AniList-пайплайн обогащения, **потом** вёрстка F3. Без этого карточкам неоткуда брать данные.
Поля: `format` (TV/ONA/Movie), `source` (Манга/Новелла/Ориджинал), `studio`.

### F3. Карточки «Информация» 2×3
Заменяют строку мелких чипов: Статус, Эпизоды, Формат, Релиз, Источник, Студия — иконка + подпись +
значение. **Блокируется F2.**

### F4. Чипсы сезонов
Из плоских серых outline-пилюль («S1 · 24 эп.») в залитые оранжевым с иконкой play
(«▶ S1 · 8 эп.») — читаются как кнопки перехода, а не теги.

### F5. Меню глав grid/list + диплинк Mihon
На существующем `IosBottomSheet`: `LazyVerticalGrid` для манги, `LazyColumn` для аниме, выбор по
`MediaType`. Сам bottom sheet не меняется. Плюс кнопка → `Intent.ACTION_VIEW` на
`https://github.com/keiyoushi/extensions` во внешнем браузере (первая итерация; позже заменится
внутренним экраном, когда появится `mihon-compat`).

---

## 7. Волна G — синк прогресса в Supabase

`EpisodePlaybackStore` и `MangaReadingStore` **не подключены** к `sync/supabase/*` вообще, хотя там
уже есть синк ключей, коллекции и картинок — паттерн `ApiKeySyncRepository`/`SyncRepository`
переиспользуется.

Модель: `positionMs`/`durationMs` на эпизод, `pageIndex`/`pageCount`/`read` на главу. Конфликт — по
`updatedAt` (уже есть в обеих моделях).

> ⚠️ **Требует участия пользователя:** нужна новая таблица в его проекте Supabase. Клиентский код
> пишется и коммитится, но синк не заработает, пока таблица не создана. Вынесено в последнюю волну
> именно поэтому.

---

## 8. Явно вне объёма

- `AutoFixUseCase`/`AlternativesUseCase`/`MigrateUseCase` (починка мёртвого источника манги) —
  средний приоритет референса, но противоречит принципу «никогда не привязывать автоматически»
  без отдельного UX-решения. Отложено до явного запроса.
- `MangaSourceHeaderInterceptor` — централизация заголовков. Стоит сделать **до** третьего
  источника манги, не сейчас (сейчас их два: MangaDex, Remanga).
- `AvifImageDecoder` — только если источники реально отдают AVIF; сначала проверить логами
  content-type.
- `ExpiringLruCache`/`MemoryContentCache` — избыточно, Coil3 уже держит memory-cache.
- `CbzFetcher` (локальные .cbz) — офлайн-импорта файлов в планах нет.
- `PageSaveHelper` (сохранение страницы в галерею) — по запросу.
- AniLiberty `/anime/franchises/*` как перекрёстная проверка графа франшиз — AniList справляется,
  не срочно.
