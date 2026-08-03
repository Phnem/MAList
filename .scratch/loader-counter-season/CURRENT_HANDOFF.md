# Current handoff

## Original goal

Три дефекта от 2026-08-02: (1) анимация загрузки видна только вместе с доками и отсутствует в
PiP; (2) на карточке главного экрана бывает «просмотрено 15 / 12»; (3) вместо второго сезона
играет первый.

## Canonical artifacts

[`MASTER_PLAN.md`](./MASTER_PLAN.md) · [`spec.md`](./spec.md) ·
[`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md) ·
[`EXECUTION_LOG.md`](./EXECUTION_LOG.md) · тикеты `issues/01`…`issues/04`

## Current workflow state

READY_FOR_FIELD_VERIFICATION. Все четыре тикета закрыты по коду.

## Completed tickets

| ID | Итог | Что сделано |
|---|---|---|
| 01 | DONE_WITH_DEVIATIONS | индикатор вынесен из доков в отдельный слой + компактный слой в PiP |
| 02 | DONE | катана и мёртвый `FrozenFrame` удалены, ридер манги на `BubbleClusterLoader` |
| 03 | DONE | знаменатель карточки — сумма по франшизе из `SeasonEpisodesStore` |
| 04 | DONE | yummy-путь Kodik подтверждает сезон, иначе отдаёт пусто |

## Active ticket

Нет.

## Next eligible ticket

Нет — пакет закрыт по коду. Дальше только приёмка на устройстве.

## Decisions that must be preserved

1. **Катана отменена как эксперимент** (решение пользователя). Единственная анимация загрузки —
   `BubbleClusterLoader`, локальная, не полноэкранная. Пакет `.scratch/katana-loader/` остаётся
   в истории с тикетами DONE на удалённый код — историю не переписывали.
2. **Знаменатель считается по франшизе, без записи в БД и без сети.** `anime.episodes`
   не мутируется; расчёт живёт в отображении.
3. **Числитель на карточке не подрезается под знаменатель** — зажим показывал бы пользователю
   не то, что он посмотрел. Прежний `coerceAtMost(coerceAtLeast(...))` был тождеством и удалён.
4. **Чиним подтверждение результата, а не сужение запроса** (Kodik). `titleRu` обязан оставаться
   франшизным: это единственный алиас, которым русские каталоги находят тайтл.
5. **Инвариант «лучше пусто, чем чужой сезон»** применён четвёртый раз в проекте.

## Deviations that affect later work

- TICKET-01: новая чистая функция видимости не заводилась — решение уже выражено существующей
  `shouldShowStreamLoading`. Взаимная исключительность двух слоёв индикатора держится структурой
  кода (guard `if (!isInPip)`), а не тестом.
- TICKET-01 задел и локальный плеер: `PlayerControls.kt` общий, дефект был общий.
- TICKET-04: строка `Resolved Kodik S{n}E{m}` не менялась (критерий был выполнен заранее);
  вместо этого добавлена строка на ОТКАЗ — её раньше не существовало.

## Current repository state

Ветка `main`. **Ничего не коммичено.** В дереве есть несвязанные пользовательские файлы,
их не трогали: `katana.html`, `katana_animation.html`, `Screen_Recording_20260730_153543_Pinterest.mp4`,
`vetro_logcat.txt`, `.codex-remote-attachments/`, `.scratch/vetro_readme_audit.png`.

Изменено этой работой:

```
M app/src/main/java/com/example/myapplication/localplayer/ui/PlayerControls.kt
M app/src/main/java/com/example/myapplication/manga/ui/MangaReaderScreen.kt
M app/src/main/java/com/example/myapplication/media/source/KodikSource.kt
M app/src/main/java/com/example/myapplication/media/ui/StreamPlayerSurface.kt
M app/src/main/java/com/example/myapplication/ui/home/HomeScreen.kt
M app/src/main/java/com/example/myapplication/ui/home/HomeViewModel.kt
D app/src/main/java/com/example/myapplication/media/ui/FrozenFrame.kt
D app/src/main/java/com/example/myapplication/ui/shared/loading/KatanaCycle.kt
D app/src/main/java/com/example/myapplication/ui/shared/loading/KatanaLoader.kt
D app/src/main/java/com/example/myapplication/ui/shared/loading/KatanaLoadingOverlay.kt
D app/src/test/java/com/example/myapplication/ui/shared/loading/KatanaCycleTest.kt
? app/src/main/java/com/example/myapplication/domain/seasons/FranchiseEpisodeTotal.kt
? app/src/test/java/com/example/myapplication/domain/seasons/FranchiseEpisodeTotalTest.kt
? app/src/test/java/com/example/myapplication/media/source/KodikYummySeasonTest.kt
```

Удаления сделаны через `git rm` — все файлы в истории, откат тривиален.

## Relevant commits

Нет.

## Verification already performed

- Базовая линия до работы: `suites=55 tests=313 failures=0`
- После TICKET-02: 297 (−16, ровно размер `KatanaCycleTest`)
- Финал: `suites=56 tests=311 failures=0 errors=0`, `:app:assembleDebug` BUILD SUCCESSFUL
- Оба новых набора писались тестом вперёд, красный зафиксирован перед реализацией
- `grep "Katana\|FrozenFrame" app/src` → 0 вхождений

## Known failures or blockers

Блокеров нет. Не выполнена ручная проверка на устройстве — ни по одному тикету.

## Files most relevant to the next ticket

Для приёмки: `media/ui/StreamPlayerSurface.kt`, `localplayer/ui/PlayerControls.kt`,
`media/source/KodikSource.kt` (`yummyReleaseServesSeason`, `findRelease`),
`domain/seasons/FranchiseEpisodeTotal.kt`.

## Exact recommended next action

Прогнать приложение и проверить четыре вещи:

1. Переключить серию и не тапать — индикатор обязан быть виден при скрытых доках; затем войти
   в PiP во время загрузки.
2. Открыть главу манги — пузырьки вместо катаны.
3. Карточка многосезонного тайтла, где было «просмотрено > всего».
4. «Низкоуровневый персонаж Томодзаки»: S1E11 → S2E1 → S2E2, снять логкат. Ключевая новая
   строка при отказе — `Yummy: no season-confirmed release for S{n} (N rejected)`.

Затем решить по коммиту: работа не закоммичена, а в дереве лежат посторонние файлы пользователя.
