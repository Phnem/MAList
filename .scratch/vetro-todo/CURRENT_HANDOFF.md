# Current handoff

Обновлено: 2026-07-29, после TICKET-08.

## Original goal

Закрыть список правок пользователя от 2026-07-29 (`spec-source.md`, 11 пунктов). Принято 10: пункт 3 (keiyoushi extensions) отклонён пользователем. Пункт 11 — обязательство финального отчёта, не код.

## Canonical artifacts

- `MASTER_PLAN.md` — состояние, таблица тикетов, решения, риски
- `spec.md` — канонические требования
- `spec-source.md` — дословный исходник пользователя
- `architecture/INITIAL_REVIEW.md` — обзор перед декомпозицией
- `EXECUTION_LOG.md` — журнал открытий
- `issues/01..09-*.md` — тикеты

## Current workflow state

`IMPLEMENTING_TICKET`. Ветка `vetro-todo` (отведена от `main`). Рабочее дерево чистое, кроме 27 удалённых файлов под `.claude/skills/` — они были удалены до начала работы и **намеренно не стейджатся**.

## Completed tickets

| ID | Пункт | Commit | Статус |
|---|---|---|---|
| TICKET-01 | 1 | `79a9d92` | DONE |
| TICKET-02 | 10 | `cee4f98` | DONE |
| TICKET-03 | 4 | `a1ab48d` | DONE |
| TICKET-04 | 5 | `b74e123` | DONE_WITH_DEVIATIONS |
| TICKET-05 | 6 | `c68d161` | DONE_WITH_DEVIATIONS |
| TICKET-06 | 7 | `82d7f0e` | DONE |
| TICKET-07 | 8 | `1e6e448` | DONE (17+4 теста зелёные) |
| TICKET-08 | 9 | `fc347d7` | SUPERSEDED — разделён, вычистка существующих дубликатов → TICKET-10 |

Плюс два подготовительных коммита: `3b3cee6` (чекпоинт незакоммиченной работы пользователя), `a224a2b` + `9b4fe8f` (конфиг скиллов и артефакты планирования).

## Active ticket

Нет.

## Next eligible ticket

**TICKET-09** (`issues/09-manga-progress-on-card.md`, пункт 2) — прогресс чтения и признак новых глав на карточке главного экрана. Блокировка снята (TICKET-05 завершён).

Затем **TICKET-10** (`issues/10-collapse-existing-duplicates.md`) — вычистка уже существующих дубликатов. Блокировка снята (TICKET-08 завершён). **Самый рискованный тикет пакета: необратимо удаляет пользовательские записи.** Правило и `collapseDuplicates` уже готовы и покрыты 17 тестами — предстоит только путь сохранения выжившей записи и точка запуска. Сохранение обязано завершиться до удаления отброшенных.

## Decisions that must be preserved

Приняты пользователем на интервью 2026-07-29, **переспрашивать нельзя** (полный список — в `MASTER_PLAN.md`, раздел Decisions):

- D-3: пункт 3 (keiyoushi) убран совсем.
- D-4: пункт 8 — только показ в поиске, `coerceAtLeast(1)` не трогать, миграций нет.
- D-2: пункт 2 — карточка главного экрана, не ридер.

## Deviations that affect later work

- **TICKET-04**: ряд акцентов `SortAccent{Primary..Quaternary}` = `#E85002 / #FF7A1A / #FF9500 / #FFB300` выбран мной, ждёт оценки пользователя.
- **TICKET-05**: золотой сведён в `OverlayThemeTokens.FavoriteGold`. **TICKET-09 правит тот же `AnimeCard.kt`** — не заводить там новых литералов цвета.
- Форма чипса избранного — приближение к референсу.

## Current repository state

Компилируется. `.\gradlew.bat :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

## Verification already performed

Только компиляция. **Ни одна визуальная проверка не выполнена** — устройства/эмулятора в сессии нет. В каждом тикете это записано как невыполненная проверка, а не как пройденная. Unit-тесты пока не запускались: первый тикет с тестами — TICKET-07.

## Known failures or blockers

Блокеров нет. Открытые вопросы к пользователю:

1. TICKET-03 — верно ли опознан виджет (`EpisodeQualitySheet`, меню качества)?
2. TICKET-04 — устраивает ли ряд оттенков?
3. TICKET-05 — соответствует ли чипс референсу; ок ли, что подтверждение «убрать из избранного» тоже стало золотым?

Ни один не блокирует продолжение.

## Files most relevant to the next ticket

- `app/src/main/java/com/example/myapplication/ui/details/DetailsScreen.kt` — строка чипсов под названием
- `app/src/main/java/com/example/myapplication/ui/details/DetailsFactCards.kt` — мини-карточки фактов
- `RatingScale` — форматирование 10-балльного рейтинга

Образец внешнего вида мини-карточек — скриншот `images/photo_2026-07-27_13-36-08.jpg` (СТАТУС / ЭПИЗОДЫ / ФОРМАТ / РЕЛИЗ / ИСТОЧНИК / СТУДИЯ).

## Exact recommended next action

Прочитать `issues/06-details-rating-fact-card.md`, затем в `DetailsScreen.kt` **сначала выписать состав верхних чипсов** и сверить со списком мини-карточек — критерий «ни один факт не потерян» проверяется этим списком, а не на глаз. Только после сверки удалять строку чипсов.
