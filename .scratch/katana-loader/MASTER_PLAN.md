# Katana Loader — Master Plan

## Workflow

Current workflow state: IMPLEMENTING_TICKET
Current ticket: TICKET-02
Last completed ticket: TICKET-01
Next eligible ticket: TICKET-02
Last updated: 2026-07-29

## Goal

Фирменная полноэкранная анимация загрузки на `Canvas`: экран затемняется, белая катана
вытягивается из ножен, остриём очерчивает окружность, возвращается в ножны, пауза, повтор.
Применяется при переключении/резолве серии в стримовом плеере и при открытии главы манги.

Закрывает отложенный `.scratch/vetro-player/issues/04-fullscreen-loading-rework.md`.

## Canonical specification

- Спека: [`spec.md`](./spec.md)
- Исходник пользователя дословно + ответы интервью: [`spec-source.md`](./spec-source.md)
- Обзор архитектуры: [`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md)
- Журнал: [`EXECUTION_LOG.md`](./EXECUTION_LOG.md)

Трекер — локальный markdown (`docs/agents/issue-tracker.md`), тикеты в `issues/`.

## Global constraints

- Ветка `vetro-todo` (продолжение работы), база `main`.
- **Не трогать** 27 удалённых файлов под `.claude/skills/` — удаление было в дереве до начала работы.
- `PlayerControls` общий для локального и стримового плееров — в объём не входит.
- Пружины и длительности — из `MotionTokens`.
- Белый цвет анимации — осознанное исключение из брендовой палитры внутри тёмного оверлея.
- Новые строки — не в `UiStrings` (лимит 255 полей).
- Не менять структурно модификаторы над `layerBackdrop`.
- `minSdk = 26`: `Modifier.blur` работает с API 31, блюр снимка кадра — по bitmap.

## Non-goals

Буферизация посреди воспроизведения; `DetailsScreen`; `StreamWatchSheet`; оглавление манги;
плейсхолдеры страниц ленты; точечные спиннеры настроек/Inspect/Home/Splash;
`ListSyncLoadingOverlay`; детерминированный прогресс; настройка отключения анимации.

## Verification commands

### Fast checks

```
./gradlew :app:compileDebugKotlin
```

### Ticket checks

```
./gradlew :app:testDebugUnitTest --tests "*KatanaCycle*"
./gradlew :app:assembleDebug
```

### Full checks

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Математика цикла катаны | DONE | — | см. коммит | самопроверка, без замечаний |
| TICKET-02 | Рисование катаны на Canvas | PENDING | 01 | — | — |
| TICKET-03 | Оверлей + ридер манги | PENDING | 02 | — | — |
| TICKET-04 | Плеер: замороженный кадр | PENDING | 03 | — | — |

## Ticket details

### TICKET-01 — Математика цикла катаны

Status: DONE
Tracker reference: [`issues/01-katana-cycle-math.md`](./issues/01-katana-cycle-math.md)
Dependencies: —
Acceptance criteria: все выполнены, см. тикет
Implementation summary: `ui/shared/loading/KatanaCycle.kt` — константы границ фаз и шесть чистых
функций от нормированной фазы. Оборот выражен в оборотах (целое ⇒ выравнивание с ножнами),
`arcSweep` = `orbitTurns` по построению, инерция ножен — аналитическая затухающая синусоида,
угловое отставание пропорционально угловой скорости.
Deviations: нет
Architecture notes: чистый слой без Compose/Android — фундамент для 02–04
Verification evidence: `tests="16" skipped="0" failures="0" errors="0"`
Commit: см. историю ветки (`feat(loading): ... [TICKET-01]`)
Follow-up tickets: нет

### TICKET-02 — Рисование катаны на Canvas

Status: PENDING
Tracker reference: [`issues/02-katana-canvas.md`](./issues/02-katana-canvas.md)
Dependencies: TICKET-01
Acceptance criteria: см. тикет
Implementation summary:
Deviations:
Architecture notes:
Verification evidence:
Commit:
Follow-up tickets:

### TICKET-03 — Оверлей + ридер манги

Status: PENDING
Tracker reference: [`issues/03-overlay-and-manga.md`](./issues/03-overlay-and-manga.md)
Dependencies: TICKET-02
Acceptance criteria: см. тикет
Implementation summary:
Deviations:
Architecture notes:
Verification evidence:
Commit:
Follow-up tickets:

### TICKET-04 — Плеер: замороженный кадр

Status: PENDING
Tracker reference: [`issues/04-player-frozen-frame.md`](./issues/04-player-frozen-frame.md)
Dependencies: TICKET-03
Acceptance criteria: см. тикет
Implementation summary:
Deviations:
Architecture notes:
Verification evidence:
Commit:
Follow-up tickets:

## Decisions

1. **Объём — два места, не четыре.** Details и `StreamWatchSheet` исключены: их загрузки не
   полноэкранные (в Details постер и сезоны уже на экране, лист — поверх Details). Решение
   пользователя, интервью 2026-07-29.
2. **Без текста.** Подписи загрузки убираются, тексты ошибок остаются.
3. **Показ без задержки и без минимального времени.** Риск вспышки на быстрых загрузках принят
   пользователем.
4. **Фон в плеере — снимок кадра `PixelCopy` с блюром по bitmap**; при неудаче (DRM, PiP, пустой
   сюрфейс) — затемнение. Живой блюр `SurfaceView` невозможен.
5. **Блюр — обязанность вызывающей стороны, не оверлея.** У Compose-контента и у видео разные
   механизмы; оверлей одинаков для обоих.
6. **Три слоя:** чистая математика цикла → рисование → оверлей-хост. Позволяет протестировать
   форму движения без Compose.

## Global deviations

Пока нет.

## Known risks

- Композиция рига (остриё на окружности, рукоять к центру) может не совпасть с тем, что
  представляет пользователь — геометрия вынесена в именованные константы для быстрой правки.
- Физика вытягивания: устье ножен со стороны цубы; если честная геометрия визуально не
  сложится, отход фиксируется в TICKET-02.
- Правка `StreamPlayerSurface` не должна задеть локальный плеер (общий `PlayerControls`).

## Deferred work

- Свести оставшиеся ~25 разнородных `CircularProgressIndicator` к согласованному набору.
- Перенести `ListSyncLoadingOverlay` в `ui/shared/loading/`.
- Реакция на системное «уменьшить движение».

## Final acceptance checklist

- [ ] Every required ticket completed
- [ ] Full test suite or agreed equivalent run
- [ ] Specification reviewed requirement by requirement
- [ ] No unresolved blocking review findings
- [ ] Migration and compatibility behavior verified
- [ ] User-visible behavior verified
- [ ] Deferred work explicitly recorded
- [ ] Final architecture checkpoint completed
