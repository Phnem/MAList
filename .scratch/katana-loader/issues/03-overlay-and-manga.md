# TICKET-03: Оверлей полноэкранной загрузки + применение в ридере манги

## Status

PENDING

## Objective

Оверлей `KatanaLoadingOverlay(visible, modifier)`: затемнение, плавные появление/исчезновение,
перехват кликов, катана по центру. Первое применение — открытие главы манги.

## User or system value

Первый видимый пользователю результат: вместо оранжевого спиннера при открытии главы —
фирменная анимация.

## Dependencies

TICKET-02.

## Scope

- `ui/shared/loading/KatanaLoadingOverlay.kt`.
- Затемнение поверх содержимого; fade-in/out (`MotionTokens.scrimFade()`).
- Перехват кликов, пока оверлей виден.
- Блюр контента — обязанность вызывающей стороны (`Modifier.blur` на контенте под оверлеем);
  оверлей его не делает.
- `MangaReaderScreen`: ветка `MangaReaderUiState.Loading` → оверлей вместо
  `CircularProgressIndicator`.

## Out of scope

Плеер. Details, лист выбора источника — вне спеки.

## Acceptance criteria

- [ ] Оверлей затемняет фон и показывает катану по центру.
- [ ] Появление и исчезновение плавные, не мгновенные.
- [ ] Клики сквозь оверлей не проходят.
- [ ] В ридере манги при `Loading` виден оверлей, спиннер удалён.
- [ ] Плейсхолдеры отдельных страниц ленты (`MangaReaderScreen:695`) не тронуты.
- [ ] Ошибка и готовое состояние ридера работают как раньше.
- [ ] Ниже API 31 экран не ломается (блюра нет — только затемнение).
- [ ] `./gradlew :app:assembleDebug` проходит.

## Verification plan

- Сборка.
- Прогон существующих unit-тестов: `./gradlew :app:testDebugUnitTest`.
- Чтение диффа: убедиться, что затронута только ветка `Loading`.

## TDD classification

NOT_NEEDED — состояние булево, логики нет. Проверка: сборка + существующие тесты + диффревью.

## Expected architecture impact

Третий слой пакета. `manga/` получает зависимость на `ui/shared/loading/` — направление
правильное (фича → общий UI).

## Risks

Ридер живёт внутри `IosSheetScaffold`; оверлей должен лечь поверх контента, но не поверх шторки
оглавления. Ставить его внутрь `content`, а не рядом со scaffold.

## Implementation notes

## Deviations

## Review findings

## Completion evidence
