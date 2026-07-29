# Katana Loader — Execution Log

## 2026-07-29 — Initial codebase discovery

### Происхождение задачи

Это описание к ранее заведённому и отложенному тикету
[`.scratch/vetro-player/issues/04-fullscreen-loading-rework.md`](../vetro-player/issues/04-fullscreen-loading-rework.md)
(статус `NEEDS_USER_DECISION`, «пользователь опишет позже, отдельным проходом»). Прогон
`vetro-player` закрыт по остальным пунктам; этот пакет закрывает его TICKET-04.

### Relevant modules

- `app/.../media/ui/StreamPlayerActivity.kt` — стриминговый плеер. Переключение серий:
  `switchingTo` (строка 165), резолв ссылки, `busyText` (448–452): `"Загружаем серию $switchingTo…"`
  и `"Обновляем ссылку на поток…"`. Показывается `Column` с чёрной подложкой alpha 0.72 в центре
  (455–500); там же живут ошибки и кнопки «Повторить»/«Закрыть».
- `app/.../media/ui/StreamPlayerSurface.kt` — видео в `PlayerView` c **SurfaceView**
  (215–226, явный комментарий «не менять на TextureView»). Кадр снимается `PixelCopy`
  (88–90) для ambient-доков.
- `app/.../media/ui/StreamWatchSheet.kt:74` — голый `CircularProgressIndicator()` на резолве
  источников перед запуском плеера.
- `app/.../localplayer/ui/PlayerControls.kt:541` — спиннер буферизации в центре транспорта
  (общий для локального и стримового плееров).
- `app/.../localplayer/ui/LocalPlayerActivity.kt:360` — спиннер загрузки локального плеера.
- `app/.../manga/ui/MangaReaderScreen.kt:152` — `MangaReaderUiState.Loading` → оранжевый
  спиннер по центру чёрного экрана. Строка 695 — спиннер плейсхолдера **отдельной страницы**
  внутри ленты (не полноэкранный).
- `app/.../manga/ui/MangaChaptersPage.kt:128,230,738,988` — спиннеры оглавления/поиска/скачивания.
- `app/.../ui/details/DetailsScreen.kt:513–524` — `DetailsUiState.Idle/Loading` → спиннер +
  текст «Загрузка...».
- `app/.../ui/details/ModernDetailsEpisodesPage.kt` (431, 675, 854, 985), `DetailsEpisodesPage.kt:603`
  — точечные спиннеры внутри страниц.
- `app/.../ui/shared/ListSyncLoadingOverlay.kt` — единственный существующий полноэкранный
  оверлей загрузки: стеклянная карточка, кольцо с градиентом, детерминированный прогресс.
  Свой законченный дизайн, не спиннер.
- ~25 прочих `CircularProgressIndicator` в настройках, Inspect, Home, Splash — точечные.

### Existing behavior

Общего компонента полноэкранной загрузки нет. Индикаторы разнородны: цвет то `BrandOrange`,
то `colorScheme.primary`, то `Color.White`, то `BrandBlue`; часть с текстом, часть без.

### Existing terminology

`MangaReaderUiState.Loading`, `DetailsUiState.Loading/Idle`, `switchingTo`, `retrying`,
`isBuffering`.

### Constraints discovered

- `minSdk = 26`, `compileSdk = 36`. `Modifier.blur` — no-op ниже API 31.
- **Видео живёт в `SurfaceView`.** Ни `Modifier.blur`, ни kyant `drawBackdrop` не видят его
  пикселей: блюр «живого кадра» поверх плеера невозможен напрямую. Существующий обходной
  путь в репозитории — снимок `PixelCopy` (`AmbientDock.kt`, `ReaderAmbient.kt`).
- Моушн только из `MotionTokens`/`IosDesign` (`ui/shared/theme/MotionTokens.kt`).
- Палитра брендовая (`#E85002` + чёрный/серые). Анимация по описанию — **белые линии**,
  это осознанное исключение внутри полноэкранного тёмного оверлея.
- `UiStrings` упёрся в лимит 255 полей: новые строки — в отдельный объект, не в `UiStrings`
  (см. память `uistrings-255-param-limit`).
- Не добавлять/убирать структурно модификаторы над `layerBackdrop`.
- В рабочем дереве 27 удалённых файлов под `.claude/skills/` — не трогать.

### Questions answerable from code

- Где сейчас точки загрузки — да (список выше).
- Есть ли общий компонент — нет, его надо создать.
- Можно ли блюрить видео — нет, только через снимок кадра.

### Remaining material uncertainties

Вынесены в интервью: объём применения, судьба текстовых подписей, фон под анимацией у плеера,
защита от мигания на быстрых загрузках.
