# Execution log — плеер: автоскип, переключение серий, жест разворота

## Initial codebase discovery

Дата: 2026-07-29. Workflow: `/ticket-autopilot`. Источник задачи — 4 пункта от пользователя, дословно в `spec-source.md`.

### Relevant modules

В приложении **два независимых плеера**, и это определяет всю декомпозицию:

| Плеер | Пакет | Чем занят |
|---|---|---|
| Локальный | `localplayer/ui/` — `LocalPlayerActivity`, `DownloadedPlayerActivity`, `PlayerScreen`, `PlayerControls` | локальные файлы и скачанные серии, media3 ExoPlayer с плейлистом |
| Стриминговый | `media/ui/` — `StreamPlayerActivity`, `StreamPlayerSurface`, `StreamWatchSheet`, `StreamWatchViewModel` | онлайн-источники (Kodik, JutSu, AnimeHeaven) |

`PlayerControls` из `localplayer` переиспользуется **обоими** — стриминговый плеер вызывает его из `StreamPlayerSurface`.

Настройки: `ui/settings/` (`SettingsScreen`, `SettingsSheets`, `SettingsUiState`, `SettingsViewModel`).

### Existing behavior (корневые причины установлены)

**Пункт 1 — автоскип опенинга.** Механизм **уже построен целиком**, включить его нечем:

- `localplayer/domain/AniSkipSegmentProvider` + `PreferSourceTimestampsSkipProvider` достают тайминги; `SkipModels.kt` определяет `SkipKind { OPENING, ENDING, RECAP, MIXED }`.
- Оба плеера уже принимают `autoSkipEnabled` и уже умеют перематывать: `PlayerScreen.kt:137-139` и `StreamPlayerSurface.kt:136-138` — `if (autoSkipEnabled && segment != null) seekTo(segment.endMs)`.
- Когда автоскип выключен, показывается ручная кнопка «пропустить» (`skipVisible = !autoSkipEnabled && activeSegment != null`).
- Флаг хранится в `LocalPlayerViewModel.AUTO_SKIP_KEY = booleanPreferencesKey("local_player_auto_skip")`, DataStore `named("settings")`.

**Ключевая находка: у этого ключа три читателя и ноль писателей.** Читают `LocalPlayerViewModel:61`, `StreamPlayerActivity:124`, `DownloadedPlayerActivity:84` — все с `?: false`. Записать его не может ничто. То есть автоскип сегодня — мёртвый код, и не хватает ровно того, что просит пункт: переключателя в настройках.

**Пункт 2 — кнопки следующей/предыдущей серии.** Корневая причина найдена: `StreamPlayerSurface.kt:237-238` передаёт в `PlayerControls` **захардкоженные** `hasPrev = false, hasNext = false`. `PlayerControls.kt:412,423` рисует кнопки как `enabled = hasPrev/hasNext`, поэтому в стриминговом плеере они постоянно неактивны.

В локальном плеере всё работает: `PlayerScreen.kt:296-297` отдаёт настоящие `currentIndex > 0` / `currentIndex < episodes.lastIndex`, а `setMediaItems(items, startIndex, 0L)` (`:89`) складывает серии в плейлист ExoPlayer, так что `seekToNextMediaItem()` действительно есть куда идти.

Осложнение: стриминговый плеер получает через intent ссылки **только текущей серии** (`EXTRA_VIDEOS_JSON`), плюс `EXTRA_SEASON`, `EXTRA_EPISODE` и `EXTRA_SEASON_JSON` (в нём общее число серий). Номер текущей серии и их количество он знает — а вот ссылку на соседнюю серию нужно резолвить заново через источник. Готовый путь резолва есть в `StreamWatchViewModel`.

**Пункт 3 — жест разворота.** Оба плеера намертво фиксируют `AspectRatioFrameLayout.RESIZE_MODE_FIT` (`PlayerScreen.kt:260`, `StreamPlayerSurface.kt:215`). Жеста масштабирования в плеерах нет вовсе.

При этом в ридере манги уже есть отлаженная реализация: `MangaReaderScreen.kt:594` `zoomAndPan()` на `calculateZoom()`, с комментарием (`:588`), почему штатный `Modifier.transformable` не подошёл. Это готовый образец, а не повод изобретать заново.

**Пункт 4 — переделка загрузки.** Пользователь явно попросил только записать в план и описать позже отдельным проходом. Реализации не подлежит.

### Existing terminology

`SkipKind` (OPENING / ENDING / RECAP / MIXED), `SkipSegment(startMs, endMs, kind)`, `activeSegment`, `autoSkipEnabled`. Локальный плеер оперирует «эпизодами» как элементами плейлиста media3; стриминговый — парой (`season`, `episode`) плюс резолв ссылок.

### Existing tests

Юнит-тестов вокруг плееров нет. В `app/src/test` лежат только `MediaTypeFromCategoryTest`, `SearchResultCountTextTest`, `DuplicateTitleRuleTest` (последние два — из прогона `vetro-todo`).

### Constraints discovered

1. Рабочее дерево чистое (кроме 27 удалённых файлов под `.claude/skills/` — были до начала работы, не трогать). Ветка `vetro-todo`, база `main`.
2. `PlayerControls` общий для двух плееров — правка его сигнатуры затрагивает оба.
3. Пункт 4 заблокирован самим пользователем до отдельного описания.
4. Проверить поведение плеера в этой сессии нечем: ни устройства, ни эмулятора. Всё, что связано с реальным воспроизведением и жестами, подтверждает пользователь.

### Questions answerable from code

- Почему не работают кнопки серий — ответ: `hasPrev/hasNext` захардкожены в `false` в стриминговом плеере (в локальном работают).
- Почему нет автоскипа — ответ: у настройки нет писателя, дефолт `false`.
- Есть ли уже механика скипа — ответ: есть целиком, включая ручную кнопку и AniSkip-провайдер.
- Один ли переключатель на оба плеера — ответ: да, ключ `AUTO_SKIP_KEY` общий, читается из всех трёх активностей.

### Remaining material uncertainties

В `INTERVIEW`: как должно вести себя переключение серий в стриминговом плеере (ссылку соседней серии надо резолвить заново); что именно понимать под «развернуть на весь экран» и в каких плеерах; должен ли переключатель автоскипа охватывать только опенинг или все виды сегментов, которые уже отдаёт провайдер.
