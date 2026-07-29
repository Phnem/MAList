# Current handoff

Обновлено: 2026-07-29, после TICKET-02.

## Original goal

Четыре пункта по плееру от 2026-07-29 (`spec-source.md`). Три реализуются, четвёртый (переделка загрузки) ждёт описания пользователя.

## Canonical artifacts

`MASTER_PLAN.md`, `spec.md`, `spec-source.md`, `architecture/INITIAL_REVIEW.md`, `EXECUTION_LOG.md`, `issues/01..04-*.md`.

## Current workflow state

`READY_FOR_IMPLEMENTATION`. Ветка `vetro-todo`, дерево чистое (кроме 27 удалённых файлов под `.claude/skills/` — были до начала работы, не стейджить).

## Completed tickets

| ID | Пункт | Commit | Статус |
|---|---|---|---|
| TICKET-01 | 1 автопропуск | `4772aa1` | DONE |
| TICKET-02 | 3 жест зума | `614e773` | DONE_WITH_DEVIATIONS |

## Active ticket

Нет.

## Next eligible ticket

**TICKET-03** (`issues/03-stream-next-prev-episode.md`, пункт 2) — кнопки серий в стриминговом плеере. Блокировка снята. Самый крупный тикет: меняется модель состояния активности, а не вёрстка.

Что уже готово ему навстречу: `PlayerZoomState.reset()` существует и вызывается при смене серии в локальном плеере — стриминговому останется вызвать его же на своём переключении (FR-3a).

Дальше — хвосты предыдущего прогона `.scratch/vetro-todo/`: **TICKET-09** (прогресс манги на карточке) и **TICKET-10** (вычистка существующих дубликатов, необратимо удаляет данные).

**TICKET-04** не начинать — ждёт описания пользователя. **TICKET-05** — попутно найденный красный тест, чинить отдельно.

## Decisions that must be preserved

- D-1: автопропуск — один переключатель на все виды сегментов.
- D-2: жест — **свободный зум с перетаскиванием**, не переключение RESIZE_MODE. Пользователь выбрал это, зная о конфликте с однопальцевыми жестами.
- D-3: соседняя серия резолвится **внутри плеера**; предзагрузка отклонена.
- D-4: пункт 4 только записать, не реализовывать.

## Deviations that affect later work

Пока нет.

## Current repository state

Компилируется: `.\gradlew.bat :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

## Relevant commits

`6f8d613` планирование, `4772aa1` TICKET-01. Ранее на этой ветке — прогон `vetro-todo` (`3b3cee6`…`3121d00`).

## Verification already performed

Компиляция зелёная. Юнит-тесты: 10 новых в `PlayerZoomTest` проходят.

**Полный набор — `117 tests completed, 1 failed`.** Падает `StatsRatingBucketTest.buckets_continuous_noGaps` — существовавший дефект, не регрессия (пакет `domain/stats` не тронут, файл теста от `43706ad`). Зелёным набор считать нельзя.

**Ни одной проверки на устройстве.** Жесты, воспроизведение, перемотка сегментов не проверены и пройденными не отмечены.

## Known failures or blockers

- `StatsRatingBucketTest.buckets_continuous_noGaps` красный (TICKET-05), не блокирует.
- Ожидает пользователя: подтверждение, что автопропуск реально перематывает; что жест ведёт себя как задумано; описание пункта 4.

## Files most relevant to the next ticket

TICKET-03:

- `media/ui/StreamPlayerSurface.kt:237-238` — `hasPrev = false, hasNext = false` литералами
- `media/ui/StreamPlayerActivity.kt:104-116` — контекст серии читается один раз в `onCreate` в неизменяемые `val`; нужно наблюдаемое состояние
- `media/ui/StreamWatchViewModel.kt` — существующий путь резолва ссылок, переиспользовать за узким интерфейсом
- `localplayer/ui/PlayerZoomGestures.kt` — `PlayerZoomState.reset()` вызвать при смене серии

## Exact recommended next action

Прочитать `issues/03-stream-next-prev-episode.md`. Начать с **чистой функции** «есть ли соседняя серия» от (`season`, `episode`, `seasonInfo.episodes`) и тестов к ней — TDD здесь REQUIRED. Только потом трогать состояние активности.

Не нарушить правило спеки: «есть соседняя серия по номеру» и «удалось зарезолвить ссылку» — разные состояния; неудачный резолв нельзя показывать как «серии нет». И отбить гонку при быстрых нажатиях так же, как отбито повторное добавление в `vetro-todo` TICKET-08.
