# TICKET-03: Оверлей полноэкранной загрузки + применение в ридере манги

## Status

DONE

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

`ui/shared/loading/KatanaLoadingOverlay.kt` — затемнение 0.82 поверх переданной области, катана
по центру, `AnimatedVisibility` с `MotionTokens.scrimFade()` на вход и `dialogExit()` на выход.

- **Оверлей живёт в композиции постоянно, а показ переключается флагом.** Если монтировать его
  веткой `when`, появление было бы рывком: `AnimatedVisibility`, входящий в композицию уже
  видимым, ничего не анимирует.
- **Гасятся все указательные события, а не только тапы** (`awaitPointerEvent().changes.consume()`).
  Перехвата одних тапов не хватило бы: сквозь оверлей проходили бы скролл и щипок ридера.
- Размер рига — 0.66 меньшей стороны, но не больше 320 dp: на планшете анимация иначе
  превращается в плакат.
- Блюр фона оверлей не делает — так решено в обзоре архитектуры. У ридера под ожиданием и так
  чёрный экран, размывать нечего.

`MangaReaderScreen`: ветка `Loading` больше ничего не рисует (`-> Unit` с пояснением), оверлей
добавлен последним элементом того же `Box` внутри `content` у `IosSheetScaffold` — так он
накрывает контент, но не шторку оглавления.

## Deviations

Нет.

## Review findings

Самопроверка диффа:

- Тронута только ветка `Loading`; `Error` и `Ready` не изменены. ✓
- Плейсхолдер отдельной страницы ленты (`MangaReaderScreen:695`, `CircularProgressIndicator` с
  `BrandOrange`) не тронут — импорты `CircularProgressIndicator`/`BrandOrange` остались нужны. ✓
- Оверлей внутри `content`, не рядом со `IosSheetScaffold` — риск из тикета снят. ✓
- Пружины и длительности взяты из `MotionTokens`. ✓
- `layerBackdrop` в затронутом экране не используется — контракт не задет. ✓
- BLOCKING-замечаний нет.

## Completion evidence

- Сборка: `.\gradlew.bat :app:assembleDebug` — успешно.
- Тесты: `.\gradlew.bat :app:testDebugUnitTest` — 175 тестов, 1 падение:
  `StatsRatingBucketTest.buckets_continuous_noGaps` (`expected:<24> but was:<02>`). Дефект
  **предшествующий и чужой**, заведён отдельно в `.scratch/vetro-player/issues/05-stats-rating-bucket-test-failure.md`,
  к загрузке отношения не имеет. Остальные 174 зелёные.
- Файлы: `ui/shared/loading/KatanaLoadingOverlay.kt` (новый),
  `manga/ui/MangaReaderScreen.kt` (ветка `Loading` + импорт).
