# Execution log — Select-item dock navigation

## 2026-08-08 — Initial codebase discovery

### Relevant modules

| Area | File | Notes |
|---|---|---|
| Nav graph | `ui/navigation/NavGraph.kt`, `Routes.kt`, `NavExtensions.kt` | NavHost, type-safe routes: Splash, Welcome, Home, Details, AddEdit, Settings, Inspect |
| Bottom dock | `glass.kt:294 GlassBottomNavigation` | 4 items: Кадр (Inspect route), Статистика (in-place overlay), Добавить (AddEdit route), Настройки (Settings route) |
| Home | `ui/home/HomeScreen.kt` (1262 lines) | Scaffold + LazyColumn + docks + overlays; owns `showCSheet`, `animeToDelete`, `animeToFavorite`, search |
| Card | `ui/home/AnimeCard.kt` | `OneUiAnimeCard`, tap → Details, Edit button → AddEdit, favorite corner chip |
| Card swipe | `HomeScreen.kt:614-636` | `SwipeToDismissBox` per row: StartToEnd → favorite confirm sheet, EndToStart → delete confirm sheet; `SwipeBackground` in `HomeComponents.kt:735` |
| Stats | `ui/home/StatsOverlay.kt` (532) + `stats/StatsCardDeckContent.kt`, `stats/StatsCardDetailContent.kt`, `StatsChartsRow.kt` | Bottom sheet: `iosSheetContainer` + `rememberIosSheetSwipe` + scrim, opened by `showCSheet` |
| Reference select-dock | `ui/details/DetailsScreen.kt:917 DetailsMiniDock` + `HorizontalPager` (2 pages) | Selected tab = pill + label expands horizontally |
| Reference select-dock | `ui/inspect/InspectModeDock.kt` + `InspectScreen.kt:107` pager | Same pattern, static fill (no backdrop sampling — shared-element crash) |
| "Наезд" reference | `HomeScreen.kt:473-487` (`homePushProgress`) | Background scales 1 → 0.94, top-anchored `TransformOrigin(0.5f, 0f)`, bottom corners 16dp, driven by `MotionTokens.sheetPresent()` |

### Existing behavior

- The dock is a launcher: each icon either navigates (`navigateToInspect`, `navigateToAddEdit`, `navigateToSettings`) or toggles an overlay (stats).
- Dock icons are shared-element sources: `inspect_container`/`inspect_icon`, `fab_container`/`fab_icon`, `settings_container`/`settings_icon`. Inspect and Settings screens are the shared-element targets.
- Dock auto-hides on scroll down (`nestedScrollConnection` → `isDockVisible`) and reappears on scroll up.
- Horizontal drag on a card row is consumed by `SwipeToDismissBox`; both directions open a confirmation sheet (`AnimeMenuSheet`) rather than acting immediately.
- Home also carries a top-right `GlassActionDock` (sort / notifications / media-type) and a floating search button.

### Existing terminology

`Dock`, `MiniDock`, `select item dock` (Details/Inspect pattern), `iosSheetContainer`, `homePush` («вдавливание»), `layerBackdrop` / `adaptiveGlassBackdrop` (live glass sampling), `MotionTokens` (spring vocabulary).

### Existing tests

No UI/instrumentation tests for Home, dock, cards or stats. Only `app/src/test/.../HlsSegmentDownloaderTest.kt` (unrelated). Verification for this feature will be build + manual/device.

### Constraints discovered

1. **`layerBackdrop` fragility (documented in code + memory).** Structurally adding/removing a modifier node above a `layerBackdrop` invalidates it and the glass docks render flat. `RenderEffect`/`scale`/`clip` on an ancestor of the backdrop node breaks the recording (`shouldBlur` explicitly excludes `showRecsSheet` for this reason). A pager that scales/translates the Home page is exactly this hazard.
2. **Shared-element + `layerBackdrop` = RenderThread SIGSEGV** (`InspectScreen.kt:136-139`). Inspect deliberately has no backdrop.
3. Dock shared-element transitions disappear if destinations stop being NavHost routes.
4. Home is a single 1262-line composable holding all overlay state; pages will need extraction.
5. Working tree currently has uncommitted work (player changes by the user + the notification auto-accept change from the previous task). None of it touches Home/dock/stats except `HomeScreen.kt` and `HomeViewModel.kt` (episode-update stack wiring).

### Questions answerable from code

- Which destinations the dock currently opens — answered above.
- How the "наезд" is currently done — `homePushProgress` graphicsLayer block.
- How the select-dock looks — `DetailsMiniDock` / `InspectModeDock`.

### Remaining material uncertainties

Taken to the interview: page set of the new dock, fate of AddEdit/search/back semantics, destructive-action confirmation in the new card menu, "наезд" direction/ownership, Inspect camera/AI page cost when kept alive in a pager, Settings sub-navigation.

---

## 2026-08-08 — Интервью и планирование

Проведён допрос по 11 развилкам, все решения зафиксированы как D1–D14 в `MASTER_PLAN.md`. Написаны `spec.md`, `architecture/INITIAL_REVIEW.md`, восемь тикетов. План подтверждён пользователем.

---

## 2026-08-08 — TICKET-01

### Outcome

IN_PROGRESS — реализация завершена, ручная проверка на устройстве не выполнена.

### Work completed

Dev-флаг `SELECT_DOCK_NAVIGATION` со сквозной проводкой (ключ → UiState → ViewModel → строка в настройках). Пакет `ui/workspace`: чистый `WorkspacePage`, `WorkspaceDock` на пять сегментов, `WorkspaceScreen` с пейджером, BackHandler'ом и доком. Ветвление по флагу в `composable<HomeRoute>`. Параметры-крючки в четырёх существующих экранах (`showBottomDock`, `onSaved`, `onBack` ×2).

### Decisions made

- Строки новых поверхностей — инлайн, не в `UiStrings` (252 из 254 полей, за пределом RELEASE падает).
- Пилюля дока и BackHandler считают от `pagerState.targetPage`, а не от `settledPage`.

### Deviations

Три, все в пользу «не оставлять заведомо сломанное состояние между тикетами»: `onSaved`, `onBack`, отдельная VM у страницы «Добавить» вытянуты из TICKET-06/07. Подробности — в тикете.

### Root causes discovered

Нет — сборка ни разу не падала.

### Verification

`:app:assembleDebug` + `:app:testDebugUnitTest` — успешно. `WorkspacePageTest` — 6 тестов.

### Review result

Самопроверка по четырём рискам архитектурного разбора пройдена. Один открытый пункт — влияние трансформации пейджера на `layerBackdrop`, проверяемо только на устройстве.

### Architecture observations

Пакет `ui/workspace` встал чистым слоем между `NavHost` и экранами; экраны получили ровно по одному необязательному крючку и не знают про флаг.

### New risks

Диалог, открытый фоновой операцией на неактивной странице, может перехватить Back раньше рабочей области. Редкий сценарий, кандидат в TICKET-08.

### Follow-up work

Проверка на устройстве по списку из `CURRENT_HANDOFF.md`.

### Next eligible ticket

TICKET-02.
