# Current handoff

Обновлено: 2026-07-29, после TICKET-03.

## Original goal

Четыре пункта по плееру от 2026-07-29 (`spec-source.md`). Три реализованы, четвёртый (переделка загрузки) ждёт описания пользователя.

## Canonical artifacts

`MASTER_PLAN.md`, `spec.md`, `spec-source.md`, `architecture/INITIAL_REVIEW.md`, `EXECUTION_LOG.md`, `issues/01..05-*.md`.

## Current workflow state

`READY_FOR_IMPLEMENTATION`. Ветка `vetro-todo`, дерево чистое (кроме 27 удалённых файлов под `.claude/skills/` — были до начала работы, не стейджить).

## Completed tickets

| ID | Пункт | Commit | Статус |
|---|---|---|---|
| TICKET-01 | 1 автопропуск | `4772aa1` | DONE |
| TICKET-02 | 3 жест зума | `614e773` | DONE_WITH_DEVIATIONS |
| TICKET-03 | 2 кнопки серий | `f5adccc` | DONE |

## Active ticket

Нет.

## Next eligible ticket

В этом пакете реализуемых тикетов не осталось. Дальше — **хвосты прогона `.scratch/vetro-todo/`**:

- **TICKET-09** (`.scratch/vetro-todo/issues/09-manga-progress-on-card.md`) — прогресс чтения манги и признак новых глав на карточке главного экрана. Правит тот же `AnimeCard.kt`, что и TICKET-05 того прогона: новых литералов цвета там не заводить, золотой уже сведён в `OverlayThemeTokens.FavoriteGold`.
- **TICKET-10** (`.scratch/vetro-todo/issues/10-collapse-existing-duplicates.md`) — вычистка уже существующих дубликатов. **Самый рискованный тикет: необратимо удаляет пользовательские записи.** Правило и `collapseDuplicates` готовы и покрыты 17 тестами; остаётся путь сохранения выжившей записи и точка запуска. Сохранение обязано завершиться до удаления отброшенных.

**TICKET-04** не начинать — ждёт описания пользователя. **TICKET-05** — попутно найденный красный тест, чинить отдельно.

## Decisions that must be preserved

- D-1: автопропуск — один переключатель на все виды сегментов.
- D-2: жест — **свободный зум с перетаскиванием**, не переключение RESIZE_MODE.
- D-3: соседняя серия резолвится **внутри плеера**; предзагрузка отклонена.
- D-4: пункт 4 только записать, не реализовывать.

## Deviations that affect later work

Пока нет.

## Current repository state

Компилируется: `.\gradlew.bat :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

Новый пакет `media/episode/`: `EpisodeRange` (чистая арифметика номеров серий) и `EpisodeStreamResolver` (узкий интерфейс «дай ссылки для серии»). Серия в `StreamPlayerActivity` — наблюдаемое состояние, а не значение, прочитанное из интента один раз.

## Relevant commits

`6f8d613` планирование, `4772aa1` TICKET-01, `614e773` TICKET-02, `f5adccc` TICKET-03. Ранее на этой ветке — прогон `vetro-todo` (`3b3cee6`…`3121d00`).

## Verification already performed

Компиляция зелёная. Юнит-тесты: 7 новых в `EpisodeRangeTest` проходят (написаны до реализации), 10 в `PlayerZoomTest` проходят.

**Полный набор — `124 tests completed, 1 failed`.** Падает `StatsRatingBucketTest.buckets_continuous_noGaps` — существовавший дефект, не регрессия (пакет `domain/stats` не тронут, файл теста от `43706ad`). Зелёным набор считать нельзя.

**Ни одной проверки на устройстве.** Жесты, воспроизведение, перемотка сегментов, переключение серий по сети не проверены и пройденными не отмечены.

## Known failures or blockers

- `StatsRatingBucketTest.buckets_continuous_noGaps` красный (TICKET-05), не блокирует.
- Ожидает пользователя: подтверждение, что автопропуск реально перематывает; что жест ведёт себя как задумано; что кнопки серий переключают серию и корректно ведут себя на краях сезона; описание пункта 4.

## Files most relevant to the next ticket

TICKET-09 (`.scratch/vetro-todo/`):

- `ui/home/AnimeCard.kt` — карточка главного экрана
- `manga/data/MangaReadingStore.kt` — прогресс чтения (`saveProgress`, ключи глав)
- `ui/shared/theme/OverlayThemeTokens.kt` — токены цвета, новых литералов не заводить

## Exact recommended next action

Открыть `.scratch/vetro-todo/CURRENT_HANDOFF.md` и `issues/09-manga-progress-on-card.md`. Первым делом установить **источник признака «вышли новые главы»**: в планировании он не был найден, и если пайплайна для манги нет — остановиться и вынести добычу данных отдельным тикетом, а не растить объём молча (записанный риск №2 того прогона).
