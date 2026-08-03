# Лоадер, счётчик серий, сезон в Kodik — Master Plan

## Workflow

Current workflow state: READY_FOR_FIELD_VERIFICATION
Current ticket: None
Last completed ticket: TICKET-04
Next eligible ticket: None — все четыре закрыты
Last updated: 2026-08-02

Код закрыт и проверен сборкой и тестами. `COMPLETED` не выставлен: у всех четырёх тикетов
непроверенной осталась ровно одна и та же часть — поведение на устройстве. Compose-слой и
сетевые пути источников автотестами в проекте не покрываются. Ничего не коммичено.

## Goal

Закрыть три дефекта, заявленных пользователем 2026-08-02:

1. Анимация загрузки видна только вместе с доками и отсутствует в PiP.
2. На карточке главного экрана бывает «просмотрено 15 / 12».
3. Вместо второго сезона играет первый (Kodik).

## Canonical specification

- Спека: [`spec.md`](./spec.md)
- Обзор архитектуры: [`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md)
- Журнал: [`EXECUTION_LOG.md`](./EXECUTION_LOG.md)
- Тикеты: [`issues/`](./issues/)

Трекер — локальный markdown (`docs/agents/issue-tracker.md`).

## Global constraints

- Ветка `main`, рабочее дерево содержит несвязанные пользовательские файлы (см. «Current
  repository state» в handoff) — не трогать и не коммитить их.
- Инвариант «лучше пусто, чем чужой сезон» пересиливает букву плана.
- «Неизвестно» не превращать в 0 / false / пусто без явного доменного решения.
- Не менять структурно модификаторы над `layerBackdrop`.
- Новые строки — не в `UiStrings` (лимит 255 полей).
- Пружины и длительности — из `MotionTokens`.
- `R`-класс только `com.phnem.vetro.R`.

## Non-goals

Полноэкранный оверлей загрузки; кнопки управления в PiP; детерминированный прогресс; запись
разрешённого числа серий в `anime.episodes`; сетевые запросы с главного экрана; франшизный
индекс AniLibria (TICKET-09 чужого пакета); сведе́ние ~25 разнородных `CircularProgressIndicator`.

## Verification commands

### Fast checks

```
./gradlew.bat :app:compileDebugKotlin
```

### Ticket checks

```
./gradlew.bat :app:testDebugUnitTest --tests "*StreamLoadingVisibility*"
./gradlew.bat :app:testDebugUnitTest --tests "*Kodik*"
```

### Full checks

```
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

Базовая линия до начала работы: **299 тестов, 0 падений** (замер пакета
`season-source-resolution`, 2026-08-01). Подтвердить на старте TICKET-01.

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Лоадер независим от доков и виден в PiP | DONE_WITH_DEVIATIONS | — | не коммичено | самопроверка, без блокеров |
| TICKET-02 | Удалить катану, ридер манги — на пузырьки | DONE | — | не коммичено | самопроверка, без блокеров |
| TICKET-03 | Знаменатель карточки в шкале франшизы | DONE | — | не коммичено | самопроверка, без блокеров |
| TICKET-04 | Yummy-путь Kodik подтверждает сезон | DONE | — | не коммичено | самопроверка, без блокеров |

Все четыре независимы. Порядок — заявленный пользователем, TICKET-02 осмысленно идёт после
TICKET-01 (тот закрепляет пузырьки как единственную анимацию загрузки).

## Ticket details

### TICKET-01 — Лоадер независим от доков и виден в PiP

Status: PENDING
Tracker reference: [`issues/01-loading-independent-of-docks.md`](./issues/01-loading-independent-of-docks.md)
Dependencies: —
Acceptance criteria: см. тикет
Implementation summary: —
Deviations: —
Architecture notes: индикатор перестаёт быть частью доков, становится слоем поверхности
Verification evidence: —
Commit: —
Follow-up tickets: —

### TICKET-02 — Удалить катану, ридер манги — на пузырьки

Status: PENDING
Tracker reference: [`issues/02-remove-katana.md`](./issues/02-remove-katana.md)
Dependencies: — (осмысленно после TICKET-01)
Acceptance criteria: см. тикет
Implementation summary: —
Deviations: —
Architecture notes: `ui/shared/loading/` схлопывается до одной анимации
Verification evidence: —
Commit: —
Follow-up tickets: —

### TICKET-03 — Знаменатель карточки в шкале франшизы

Status: PENDING
Tracker reference: [`issues/03-franchise-scale-denominator.md`](./issues/03-franchise-scale-denominator.md)
Dependencies: —
Acceptance criteria: см. тикет
Implementation summary: —
Deviations: —
Architecture notes: знание о шкале оседает рядом с `HomeViewModel.watchedEpisodes`
Verification evidence: —
Commit: —
Follow-up tickets: —

### TICKET-04 — Yummy-путь Kodik подтверждает сезон

Status: PENDING
Tracker reference: [`issues/04-kodik-yummy-season-confirmation.md`](./issues/04-kodik-yummy-season-confirmation.md)
Dependencies: —
Acceptance criteria: см. тикет
Implementation summary: —
Deviations: —
Architecture notes: оба пути `KodikSource` становятся сезонно-осведомлёнными
Verification evidence: —
Commit: —
Follow-up tickets: —

## Decisions

1. **Катана удаляется, а не подключается в плеер.** Эксперимент признан пользователем неудачным;
   вместо неё уже живут локальные «шарики» (`BubbleClusterLoader`). Интервью 2026-08-02.
   Отменяет замысел пакета `.scratch/katana-loader/` — историю там не переписываем.
2. **Знаменатель — сумма по франшизе из `SeasonEpisodesStore`**, без записи в БД и без сети.
   Выбрано из трёх вариантов: расклад уже разрешён и закэширован, а числитель уже сквозной,
   поэтому шкалы сходятся без новых источников данных. Интервью 2026-08-02.
3. **В PiP показывается компактный индикатор загрузки** — иначе неотличимо от зависшего плеера.
   Интервью 2026-08-02.
4. **Чиним подтверждение результата, а не сужение запроса** (TICKET-04). `titleRu` намеренно
   остаётся франшизным: он единственный, чем русские каталоги находят тайтл.

## Global deviations

Пока нет.

## Known risks

- Строгое подтверждение сезона в yummy-пути обнулит часть выдачи там, где раньше был результат.
  Размен уже разрешён инвариантом проекта.
- `PlayerControls.kt` общий для локального и стримового плееров — правка задевает оба.
- Знаменатель у онгоинга будет расти со временем (семантика `SeasonInfo.episodes` = «доступно
  сейчас»); убедиться, что это не читается как ошибка.
- Ручная проверка на устройстве нужна всем четырём тикетам и остаётся за пользователем:
  Compose-слой и сетевые пути источников автотестами здесь не покрываются.

## Known limitations

1. **Неполный расклад сезонов занижает знаменатель** (TICKET-03). Если резолвнут только первый
   сезон многосезонного тайтла, «просмотрено > всего» теоретически может уцелеть. Строго лучше
   прежнего, но без сети не лечится, а сеть на главном экране исключена спекой.
2. **Yummy-путь может замолчать на сезоне ≥2** (TICKET-04). Подтверждено, что чужой сезон больше
   не проходит; НЕ подтверждено, что yani.tv отдаёт нужный релиз по английскому названию сезона.
   Если не отдаёт — вся нагрузка ляжет на direct-путь. Виден по новой строке лога
   `Yummy: no season-confirmed release for S{n}`.
3. **Знаменатель у онгоинга растёт со временем** — следствие семантики «доступно сейчас».
4. **Взаимная исключительность слоёв индикатора держится структурой, а не тестом** (TICKET-01).

## Deferred work

- `.scratch/season-source-resolution/` TICKET-09 — франшизный индекс AniLibria
  (`AniLibriaSource.kt:247`, `.getOrNull(seasonInfo.seasonNumber - 1)`). Чужой сезон выдать не
  может, но теряет резолвы. И TICKET-03 того же пакета — ждёт решения по jut.su.
- Свести ~25 разнородных `CircularProgressIndicator` к согласованному набору.
- Разделить тумблер PiP в `StreamPlayerSurface`: сейчас `if (!isInPip)` выключает весь
  Compose-слой целиком (см. финальный чекпойнт архитектуры).
- `SupabaseSync`: `Push anime failed: null value in column "media_type"`, RLS-политика на
  bootstrap passphrase — видно в логах, вне объёма.
- `api.jikan.moe` отвечает 504 на всех запросах (наблюдение из логов двух прогонов).
- Резолв титульной страницы jut.su через `lookfor` стоит до 10 с на каждую серию — кандидат
  на кэш.

## Requirement-by-requirement audit

| Требование | Итог | Свидетельство |
|---|---|---|
| FR-1 индикатор не зависит от доков | PASS (код) / NOT_VERIFIED (устройство) | вынесен в отдельный слой до ветки `locked`; Compose автотестами не покрыт |
| FR-2 индикатор виден в PiP | PASS (код) / NOT_VERIFIED (устройство) | слой `if (isInPip && showLoading)` вне `if (!isInPip)` |
| FR-3 катана удалена | PASS | `grep "Katana\|FrozenFrame" app/src` → 0; −16 тестов ровно, ридер на `BubbleClusterLoader` |
| FR-4 знаменатель в шкале франшизы | PASS | `FranchiseEpisodeTotalTest` 7/7, включая перебор «просмотрено ≤ всего» |
| FR-5 yummy-путь уважает сезон | PASS (логика) / NOT_VERIFIED (сеть) | `KodikYummySeasonTest` 7/7 на контрпримере из логката; сетевого прогона не было |
| AC-7 сборка и тесты зелёные | PASS | `suites=56 tests=311 failures=0 errors=0`; `assembleDebug` успешна |

Ни одно требование не помечено FAIL или PARTIAL. Все `NOT_VERIFIED` — один и тот же пробел:
поведение на реальном устройстве.

## Final scope audit

- Диффом затронуто 7 файлов + 3 новых, все — в объёме тикетов. Постороннего рефактора нет.
- Отладочного и прототипного кода не осталось; ни один критерий не отброшен молча.
- Незакоммиченные пользовательские файлы (`katana.html`, `katana_animation.html`,
  `Screen_Recording_*.mp4`, `vetro_logcat.txt`, `.codex-remote-attachments/`) не тронуты.
- **Шум переводов строк.** `StreamPlayerSurface.kt` в `HEAD` имел смешанные окончания
  (286 CRLF + 25 LF); правка нормализовала файл к CRLF целиком. Содержательных изменений там
  ровно 14 строк — 4 импорта и блок PiP (`git diff --ignore-cr-at-eol`). Остальные шесть файлов
  сохранили исходный стиль байт-в-байт. Функциональных последствий нет.

## Final acceptance checklist

- [x] Every required ticket completed
- [x] Full test suite or agreed equivalent run
- [x] Specification reviewed requirement by requirement
- [x] No unresolved blocking review findings
- [x] Migration and compatibility behavior verified — миграций нет, схема `SeasonEpisodesEntry`
      не менялась, `CURRENT_SCHEMA` поднимать не потребовалось
- [ ] **User-visible behavior verified** — единственный незакрытый пункт, нужен прогон на устройстве
- [x] Deferred work explicitly recorded
- [x] Final architecture checkpoint completed (см. ниже)

## Final architecture checkpoint

Долг не вырос ни по одному тикету, по трём — уменьшился.

- **Слои загрузки.** Индикатор перестал быть частью доков; `ui/shared/loading/` схлопнулся с
  двух конкурирующих анимаций до одной. Мёртвый `FrozenFrame` удалён.
- **Шкалы прогресса.** Знание о франшизной шкале осело в одной чистой функции без зависимостей
  от Android, вместо арифметики в теле `remember`. Числитель и знаменатель читают ОДИН расклад.
- **Сезоны в источниках.** Правило «релиз относится к сезону» осталось в единственном месте на
  весь проект (`releaseIdentifiesSelectedSeason`) — копии не появилось. Оба пути `KodikSource`
  теперь сезонно-осведомлённые.

Обнажившаяся, но не исправленная структурная проблема: `if (!isInPip)` в `StreamPlayerSurface`
по-прежнему выключает весь Compose-слой одним тумблером. Для задачи это не потребовалось —
индикатор единственное, что обязано пережить PiP, — но следующий элемент, которому понадобится
жить в PiP, упрётся в тот же тумблер. Записано в отложенное.
