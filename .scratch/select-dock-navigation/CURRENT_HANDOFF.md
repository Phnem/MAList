# Current handoff

## Original goal

Корневая навигация: док-селектор + рабочая область из четырёх страниц (Кадр · Главная ·
Добавить · Настройки) с переходом-наездом; контекстное меню карточки по долгому удержанию
вместо свайпов; статистика — шторкой из верхнего дока. Всё под dev-флагом.

## Canonical artifacts

- Спека: `.scratch/select-dock-navigation/spec.md`
- План: `.scratch/select-dock-navigation/MASTER_PLAN.md`
- Архитектура: `.scratch/select-dock-navigation/architecture/INITIAL_REVIEW.md`
- Тикеты: `.scratch/select-dock-navigation/issues/01..11`

## Current workflow state

VERIFYING_TICKET. Реализованы и собираются 01, 02, 03, 04, 07, 09, 10, 11. Ручная проверка на
устройстве не выполнена НИ ПО ОДНОМУ из них.

## Completed tickets

Нет (все ждут устройства).

## Active ticket

TICKET-07 «Кадр и Настройки как страницы» — последний реализованный.

## Next eligible ticket

TICKET-06 «Страница Добавить» (осталось только сбросить черновик при уходе), затем TICKET-08
«Приёмочный проход».

## Decisions that must be preserved

D1–D18 в `MASTER_PLAN.md`. Особо:

- Флаг читается ровно в одной точке — `composable<HomeRoute>` в `NavGraph.kt`.
- Строки новых поверхностей НЕ добавлять в `UiStrings`: там 252 поля из 254 допустимых.
- Порядок `WorkspacePage` — часть контракта: индекс = позиция и в пейджере, и в доке. На нём же
  держится порядок отрисовки страниц при «наезде» (`zIndex = index`).
- `onBack == null` теперь означает «кнопки назад нет» (Inspect, Settings, `GlassMenuHeader`).
  Маршруты обязаны передавать `onBack` явно.

## Deviations that affect later work

- `PageTransform` без поля `zIndex` (порядок задаётся индексом страницы).
- Кросс-фейд двух слоёв стекла в доке не понадобился — режим переключает
  `rememberAdaptiveGlassEffects` числами на одном узле.
- Правило автоскрытия переехало из `HomeScreen` в общий `ui/shared/DockAutoHide.kt`.

## Current repository state

Ветка `main`, ничего не коммитил. В рабочем дереве, помимо этой задачи, лежат незакоммиченные
изменения пользователя (плеер, загрузки) — не трогать.

Файлы этой задачи:

- новые: `ui/workspace/WorkspacePage.kt`, `WorkspaceDock.kt`, `WorkspaceScreen.kt`,
  `WorkspacePageTransform.kt`, `ui/shared/DockAutoHide.kt`,
  `test/.../WorkspacePageTest.kt`, `test/.../WorkspacePageTransformTest.kt`
- изменены: `DevPreferencesKeys.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`,
  `SettingsScreen.kt`, `NavGraph.kt`, `HomeScreen.kt`, `AddEditScreen.kt`, `InspectScreen.kt`,
  `InspectHeader.kt`, `ui/shared/components/GlassMenuHeader.kt`

## Relevant commits

Нет.

## Verification already performed

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — BUILD SUCCESSFUL
- `WorkspacePageTest` — 6 тестов, `WorkspacePageTransformTest` — 7 тестов, все проходят

## Найдено пользователем на устройстве и исправлено (2026-08-08)

1. **Наезда не было видно** — перепутан знак `currentPageOffsetFraction` в формуле смещения,
   обе видимые страницы попадали в одну роль. Формула вынесена в `workspacePageOffset()` и
   закрыта тестами.
2. **Средняя кнопка верхнего дока** открывала панель подключения: `middleAction` передавали
   только плавающему виду шапки, а не тому, что виден при списке вверху.
3. Подпись в капсуле дока стояла далеко от иконки; порядок разделов изменён на
   Главная · Настройки · Добавить · Кадр (D19).

Всё исправленное **повторно на устройстве не проверялось**.

## Known failures or blockers

Ничего не падает. Открытые вопросы — только устройство:

1. **Стекло.** Теперь над контентом страниц ещё и `graphicsLayer` наезда. Смотреть в первую
   очередь: верхний правый док главной и сам док рабочей области после 10+ переходов — не
   залились ли сплошным цветом.
2. Передача горизонтального жеста на краю внутреннего пейджера Кадра (риск 2 плана).
3. Диалог, открытый фоновой операцией на НЕактивной странице, может перехватить Back.
4. Контраст белой капсулы дока в СВЕТЛОЙ теме на светлом стекле — проверить глазами.

## Files most relevant to the next ticket

TICKET-06: `ui/addedit/AddEditScreen.kt`, `AddEditViewModel.kt` (сброс черновика при уходе со
страницы), `ui/workspace/WorkspaceScreen.kt:96` (отдельная инстанция VM уже есть).

## Exact recommended next action

Собрать debug, включить «Разработчик → Навигация свайпом» и пройти чек-листы тикетов 11 → 03 →
04 → 07 (в этом порядке: вид дока, переход, стекло/автоскрытие, страницы). По результату
закрывать тикеты или заводить правки.
