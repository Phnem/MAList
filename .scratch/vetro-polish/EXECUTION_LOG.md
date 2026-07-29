# Журнал выполнения — vetro-polish

## Initial codebase discovery (2026-07-29)

### Relevant modules

- `ui/details/EpisodeQualitySheet.kt` (331) — меню качества, единственный `Popup` этой области.
- `ui/shared/theme/OverlayThemeTokens.kt` — `EpisodeMenuSurfaceDark = #333333`, заведён в прошлом
  прогоне под этот же пункт.
- `media/ui/StreamPlayerActivity.kt` — `switchToEpisode`, ветка `STATE_ENDED` (только запись
  прогресса), `EpisodeRange.hasNext/nextOf`.
- `media/ui/StreamPlayerSurface.kt` (319) и `localplayer/ui/PlayerScreen.kt` — `graphicsLayer`
  домножает `animatedVideoScale * zoomState.scale`.
- `localplayer/ui/PlayerControls.kt` (1055) — общий для двух плееров; `playerZoomGestures`,
  ветка pan при `isZoomed`, кнопка `onCycleFit`.
- `localplayer/ui/PlayerZoom.kt` / `PlayerZoomGestures.kt` — чистая арифметика зума + состояние.
- `manga/domain/MangaReadingSummary.kt` — `summarizeMangaReading`, покрыта 10 тестами.
- `manga/ui/MangaChaptersPage.kt` (1094) — группы томов, `groupRowShape(18/4)`, свёрнутый том
  выкидывает `itemsIndexed` из `LazyColumn`.
- `manga/ui/ReaderDock.kt` — приватная `nextLayout` (Webtoon → Rtl → Ltr).
- `manga/data/MangaReadingStore.kt` — снимок в DataStore, `legacyMode` → `Paged`,
  `hasExplicitMode`.
- `manga/domain/DetectReaderMode.kt` + `di/appModule.kt:126` — автодетект режима.
- `ui/settings/SettingsScreen.kt:173` и `ui/details/ModernDetailsEpisodesPage.kt:198` — **две копии**
  одного градиента, разошедшиеся в светлой теме (`#FFD9CC` против `#FFD8CB`).

### Existing behavior

Описано требование за требованием в `spec.md` → «Current behavior».

### Existing terminology

`VideoFit {ORIGINAL, CROP}`, `MangaReaderMode {Paged, Webtoon}`, `PageDirection {Rtl, Ltr}`,
`ChapterReadingProgress.read` (липкая отметка), `MangaReadingSummary`, `EpisodeRange`.

### Existing tests

`MangaReadingSummaryTest` (10), `EpisodeRangeTest` (7), тесты `localplayer` вокруг `PlayerZoom.kt`.
Известное падение — `StatsRatingBucketTest.buckets_continuous_noGaps` (дефект, не регрессия,
`vetro-player` TICKET-05).

### Constraints discovered

- `gradlew.bat` только через PowerShell; `./gradlew` в git bash падает молча.
- `UiStrings` нельзя раздувать (>254 полей роняет release).
- `PlayerControls` общий на два плеера.
- 27 удалённых файлов под `.claude/skills/` в дереве — не трогать.

### Questions answerable from code

- Что за «выпадающее меню» — `EpisodeQualityPopover` (подтверждено D-7 прошлого прогона).
- Почему нет анимации у томов — строки выкидываются из `LazyColumn` целиком.
- Чем «кнопка расширения» отличается от жеста — `onCycleFit` меняет `VideoFit`, жест меняет
  свободный `zoomState.scale`; в `graphicsLayer` они перемножаются.
- Где взять градиент — два готовых литерала в настройках и меню серий.

### Remaining material uncertainties

Все семь вынесены на интервью и закрыты (D-1…D-7). `BLOCKING` не осталось.

## 2026-07-29 — TICKET-01

### Outcome

DONE

### Work completed

Меню качества приведено к референсу: фон `#1C1C1E` alpha 0.96 вместо непрозрачного `#333333`,
скругление `RadiusLg`, карточка 260dp, строка 62dp, подпись 18sp, бейдж HD — сплошная оранжевая
плашка с белым текстом, разделитель отодвинут к краю (16dp вместо 60dp).

### Decisions made

Числа (260/62/16dp, alpha 0.96) выбраны мной как `SAFE_DEFAULT` спеки — направление задано
пользователем, конкретика моя.

### Deviations

Нет.

### Verification

`:app:compileDebugKotlin` — успешно. Визуальное соответствие макету не проверено (нечем).

### Review result

Без блокирующих. Ширина 260dp на узких экранах ограничена существующим `coerceIn` по ширине окна.

### Architecture observations

Нулевое влияние: значения внутри одного composable и один токен с единственным читателем.

### New risks

Нет.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-02.

## 2026-07-29 — TICKET-02

### Outcome

DONE_WITH_DEVIATIONS

### Work completed

Свободный зум плеера удалён целиком. Щипок переводит кадр между `VideoFit.ORIGINAL` и `CROP` —
тем же входом, что кнопка в доке. `PlayerZoomState` заменён на `PlayerPinchState` (только
арбитраж мультитача и дебаунс), из `graphicsLayer` обоих плееров ушло умножение на масштаб жеста,
ветка панорамирования в `PlayerControls` удалена.

### Decisions made

`onCycleFit: () -> Unit` → `onSetFit: (VideoFit) -> Unit`: один вход в состояние для кнопки и
жеста. Это и есть техническое содержание требования «прям 1в1».

### Deviations

Замена `onCycleFit` шире, чем планировалось в тикете (там значилось только удаление `zoomState`).
Причина и последствия — в тикете.

### Root causes discovered

Претензия пользователя была не к порогам жеста, а к самой модели: масштаб жеста и `VideoFit`
перемножались в `graphicsLayer`, то есть у кадра было два независимых источника масштаба.

### Verification

`:app:compileDebugKotlin` — успешно. `:app:testDebugUnitTest` — 133 теста, 1 известное падение
(`StatsRatingBucketTest`). Жест на устройстве не проверен.

### Review result

Без блокирующих. Ридер манги не затронут (совпадения только в `manga/`).

### Architecture observations

Долг сократился: из общего `PlayerControls` ушло состояние, протаскивавшееся через оба плеера;
положение кадра стало единственным источником масштаба.

### New risks

Поведение локального плеера на устройстве не проверено — он общий код с правкой и пользователем
не заказан.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-03.

## 2026-07-29 — TICKET-03

### Outcome

DONE_WITH_DEVIATIONS

### Work completed

Автопереход на следующую серию по концу текущей: в стриминге — вызов того же `switchToEpisode`,
что у кнопки «дальше»; в локальном плеере настройка гасит штатный переход ExoPlayer через
`pauseAtEndOfMediaItems`. Переключатель в настройках, дефолт — включено.

### Decisions made

Условие запуска вынесено чистой функцией `shouldAutoAdvance`, а не оставлено внутри слушателя:
`STATE_ENDED` неоднозначен, и правило обязано быть проверяемым.

### Deviations

В локальном плеере поведение не добавлено, а стало управляемым — подробности в тикете.

### Verification

`:app:compileDebugKotlin` — успешно. `:app:testDebugUnitTest` — 138 тестов, 1 известное падение.
Фактический переход по концу серии не проверен.

### Review result

Без блокирующих.

### Architecture observations

Новых узлов нет: одна чистая функция и одна настройка. Дублирования логики перехода не возникло.

### New risks

Дефолт `?: true` продублирован в четырёх читателях (у DataStore нет общего места для дефолтов).
Расхождение здесь дало бы настройку, которая по-разному читается на разных экранах.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-04.

## 2026-07-29 — TICKET-04

### Outcome

DONE

### Work completed

Прогресс манги считается как длина прочитанного отрезка оглавления. Появились `readingFrontier`,
`chaptersToMarkRead` и пакетная запись `MangaReadingStore.markReadBulk`; наблюдатель прогресса во
вкладке «Главы» дописывает недостающие отметки.

### Decisions made

Граница берётся по номеру главы, а не по позиции в списке — правило перестало зависеть от порядка,
в котором источник отдал оглавление.

### Deviations

Позиция → номер (см. тикет). Ненумерованные главы в отрезок не входят.

### Root causes discovered

Старое правило считало размер пересечения множеств. Отметка «прочитано» ставится только по факту
открытия последней страницы, поэтому у читателя, идущего подряд, отмеченной оказывалась одна глава.

### Verification

`:app:testDebugUnitTest` — 151 тест, 1 известное падение. `:app:compileDebugKotlin` — успешно.

### Review result

Без блокирующих. При реализации поймана ловушка со временем дописанных отметок (гасила метку новых
глав) — исправлена до коммита.

### Architecture observations

Домен уточнён: прогресс манги — граница, а не множество. Оба выхода идут через одно правило.

### New risks

Операция необратима: снять дописанные отметки скопом нечем. Принято пользователем (D-4).

### Follow-up work

Нет.

### Next eligible ticket

TICKET-05.

## 2026-07-29 — TICKET-05

### Outcome

DONE

### Work completed

Том стал одним элементом списка: шапка плюс тело под `AnimatedVisibility` с `expandVertically` /
`shrinkVertically` на пружине `seasonExpansion`. Группа без томов осталась ленивой.

### Decisions made

Шапку и тело нельзя держать раздельными элементами: `spacedBy` ставит зазор независимо от высоты, и
у свёрнутого тома между шапками набегал бы двойной отступ.

### Deviations

Тело тома не отдельным элементом, а вместе с шапкой (см. тикет).

### Root causes discovered

Анимации не было по структурной причине: свёрнутый том выкидывал свои `itemsIndexed` из
`LazyColumn`, и анимировать было нечего.

### Verification

`:app:compileDebugKotlin` — успешно. Плавность не проверена.

### Review result

Без блокирующих.

### Architecture observations

Структура списка стала честнее: группа тома — один узел с собственным состоянием видимости.

### New risks

Главы одного тома измеряются вместе. Для тома в 10–20 глав это ничего не стоит.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-06.

## 2026-07-29 — TICKET-06

### Outcome

DONE

### Work completed

Градиент экрана вынесен в `IosDesign.screenGradient`; настройки, меню серий и меню глав читают
токен. Карточки томов и глав получили скругления 22/6 и полупрозрачные затемняющие заливки.

### Decisions made

Заливки переведены с осветления на затемнение: белая плёнка поверх тёплого градиента давала бы
дымку вместо стекла.

### Deviations

Радиусы 22/6 — локальные константы файла: общего токена ровно на 22dp в `IosDesign` нет.

### Verification

`:app:compileDebugKotlin` — успешно. Оттенок и читаемость на устройстве не проверены.

### Review result

Без блокирующих. Значения чужих экранов перенесены дословно, кроме сведения `#FFD9CC` → `#FFD8CB`.

### Architecture observations

Обязательная правка архобзора выполнена: копипаста градиента (уже разошедшаяся) заменена токеном.

### New risks

Нет.

### Follow-up work

Нет.

### Next eligible ticket

TICKET-07.

## 2026-07-29 — TICKET-07

### Outcome

DONE

### Work completed

Цикл раскладок: вертикальный → LTR → RTL. Правило вынесено в `manga/domain/ReaderLayout.kt` и
покрыто 5 тестами. Дефолт режима — вертикальная лента. `DetectReaderMode` удалён вместе с
koin-регистрацией и осиротевшим `MangaReadingStore.hasExplicitMode`.

### Decisions made

Старый глобальный ключ уважается, если в нём явно `Paged`; всё остальное читается как `Webtoon`.

### Deviations

Нет.

### Verification

`:app:testDebugUnitTest` — 156 тестов, 1 известное падение. `:app:compileDebugKotlin` — успешно.

### Review result

Без блокирующих.

### Architecture observations

Удалён целый мёртвый узел (сеть + анализ картинок + регистрация). Правило порядка стало тестируемым.

### New risks

Тайтлы без явного выбора перейдут на вертикальный режим — заказанное следствие (D-7).

### Follow-up work

Нет.

### Next eligible ticket

Нет: пакет закрыт. Финальное ревью — `reviews/final-review.md`.

## 2026-07-29 — Финальное ревью

### Outcome

COMPLETED по коду.

### Verification

`:app:assembleDebug` — `BUILD SUCCESSFUL in 46s`. `:app:testDebugUnitTest` — 156 тестов, 1 падение
(`StatsRatingBucketTest`, существовавший дефект).

### Review result

Все FR-1…FR-7 — PASS, кроме проверок, требующих устройства (`NOT_VERIFIED`) и FR-5e (PARTIAL:
контраст оценён логикой, не глазами). Постороннего рефакторинга и отладочного кода нет.

### Architecture observations

Пять улучшений и четыре оставшихся долга перечислены в `reviews/final-review.md`.

### Follow-up work

Хвосты прошлых прогонов: `vetro-todo` TICKET-10/11, `vetro-player` TICKET-04/05.

## 2026-07-29 — TICKET-08 (заведён после закрытия пакета)

### Outcome

DONE_WITH_DEVIATIONS

### Discovery source

Прямая просьба пользователя после приёмки первых семи пунктов. Спека дополнена разделом FR-8 и
критерием AC-12; объём остальных тикетов не тронут.

### Work completed

Удержание пальца в правой половине включает скорость 2× и возвращает прежнюю при отпускании.
Распознавание встроено в существующий обработчик тапов; добавлены `isSpeedHoldZone` и `HOLD_SPEED`
с тестами, плюс плашка «2×».

### Decisions made

Порог удержания — системный `viewConfiguration.longPressTimeoutMillis`, а не своя константа.
Тап и удержание распознаёт один обработчик: два независимых распознавателя на одном касании
отняли бы друг у друга либо тап, либо удержание.

### Deviations

Сверх плана добавлен индикатор «2×».

### Root causes discovered

`awaitLongPressOrCancellation` для этого случая не годится: он не отличает отпускание от отмены, а
событие отпускания к моменту возврата уже израсходовано — тап на правой половине терялся бы.

### Verification

`:app:compileDebugKotlin` — успешно. `:app:testDebugUnitTest` — 159 тестов, 1 известное падение.
`:app:assembleDebug` — `BUILD SUCCESSFUL in 28s`. Сам жест на устройстве не проверен.

### Review result

Без блокирующих. Две проблемы найдены самопроверкой и исправлены до коммита: `speed` в ключах
`pointerInput` (откатывал бы выбор скорости из меню) и восстановление скорости на уже освобождённом
плеере.

### Architecture observations

Новых состояний в хостах не появилось. Зона и множитель ушли к остальной чистой арифметике жестов.

### New risks

Конфликт удержания с дабл-тапом закрыт конструкцией, но проверяется только на устройстве.

### Follow-up work

Нет.

### Next eligible ticket

Нет: пакет снова закрыт.
