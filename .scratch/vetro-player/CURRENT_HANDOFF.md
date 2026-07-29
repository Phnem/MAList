# Current handoff

Обновлено: 2026-07-29, после TICKET-01.

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

## Active ticket

Нет.

## Next eligible ticket

**TICKET-02** (`issues/02-pinch-zoom-pan.md`, пункт 3) — зум двумя пальцами и панорамирование. Не заблокирован. Блокирует TICKET-03.

Затем **TICKET-03** (`issues/03-stream-next-prev-episode.md`, пункт 2) — кнопки серий в стриминговом плеере. Самый крупный: меняется модель состояния активности.

**TICKET-04** не начинать — ждёт описания пользователя.

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

Только компиляция. Юнит-тесты в этом прогоне пока не писались (первый с тестами — TICKET-02). **Ни одной проверки на устройстве** — жесты, воспроизведение, перемотка сегментов не проверены и не отмечены пройденными.

## Known failures or blockers

Блокеров нет. Ожидает пользователя: подтверждение, что автопропуск реально перематывает; описание пункта 4.

## Files most relevant to the next ticket

- `app/src/main/java/com/example/myapplication/localplayer/ui/PlayerScreen.kt:260` — `RESIZE_MODE_FIT`
- `app/src/main/java/com/example/myapplication/media/ui/StreamPlayerSurface.kt:215` — `RESIZE_MODE_FIT`
- `app/src/main/java/com/example/myapplication/manga/ui/MangaReaderScreen.kt:485-620` — `FittedZoomablePage` и `zoomAndPan()`, **готовый образец жеста** с объяснением (`:588`), почему `Modifier.transformable` не подошёл

## Exact recommended next action

Прочитать `issues/02-pinch-zoom-pan.md`, затем разобрать `zoomAndPan()` из ридера манги и повторить подход в плеерах. Ключевое: развести жесты по числу указателей — два пальца зум и панорамирование, один палец существующие жесты плеера (перемотка, яркость, громкость). Ограничитель масштаба и смещения вынести чистой функцией и покрыть тестом до реализации.
