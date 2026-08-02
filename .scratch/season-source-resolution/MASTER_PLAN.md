# Резолв сезонов по источникам — Master Plan

## Workflow

Current workflow state: READY_FOR_IMPLEMENTATION
Current ticket: None
Last completed ticket: TICKET-03
Next eligible ticket: TICKET-09
Last updated: 2026-08-01

Все восемь исходных тикетов выполнены (01–08). TICKET-09 заведён из TICKET-08 как
продолжение. Финальное ревью не запускалось: непроверенной остаётся ровно одна вещь —
поведение на устройстве, а присланный логкат 22:01 её не покрывает (окно 29 секунд,
началось уже посреди воспроизведения).

Требования спеки F1 и F2 (извлечение видео из jut.su) сняты решением пользователя по
TICKET-03 — вариант A: jut.su признан источником таймскипов.

**Ветка:** работа велась на `vetro-todo`; 2026-08-01 ветка влита в `main` перемоткой
(была на 36 коммитов впереди и ноль позади) и удалена. Дальше — `main`,
на 54 коммита впереди `origin/main`.

## Goal

Вернуть выдачу видео по сезонам, которые сейчас не резолвятся ни одним из четырёх RU-источников.
Каждый источник получает лестницу стратегий: лучшая — первой, остальные варианты — запасными
ступенями. Инвариант «никогда не подсовывать чужой сезон молча» сохраняется.

Триггер: «Повар-боец Сома», сезоны S2 и S6 не дают ни одного играбельного видео.
Логи устройства SM-S936B от 2026-08-01 (18:37 и 20:01).

## Canonical specification

- Спека: [`spec.md`](./spec.md)
- Архитектурный обзор: [`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md)
- Журнал: [`EXECUTION_LOG.md`](./EXECUTION_LOG.md)
- Handoff: [`CURRENT_HANDOFF.md`](./CURRENT_HANDOFF.md)

Трекер — локальный markdown (`docs/agents/issue-tracker.md`), тикеты в `issues/`.

## Architecture review

Границы нарезаны верно, структурная переделка не нужна. Требуют правки три вещи:

1. `scopedToSeason` кодирует три смысла через `Anime?` (TICKET-04).
2. `SeasonInfo.seasonNumber` означает две разные величины (TICKET-08).
3. Источники молчат об отказе (TICKET-01).

Лестницы стратегий прячутся внутри своего источника, за неизменной сигнатурой `resolveEpisode`.
`SourceEngine` не знает, сколько ступеней у Kodik.

## Global constraints

- Ветка `vetro-todo`, база `main`.
- **Рабочее дерево содержит много несвязанных правок пользователя** (`media/ui/`, `sync/supabase/`,
  `localplayer/`, `manga/`, `.scratch/katana-loader/`). Не трогать, не откатывать, не коммитить
  вместе с тикетами.
- **Не трогать** удалённые файлы под `.claude/skills/` — удаление было в дереве до начала работы.
- Новые строки — не в `UiStrings` (лимит 255 полей, иначе `VerifyError` в release).
- `R`-класс только `com.phnem.vetro.R`.
- Бюджеты таймаутов `SourceEngine` не растут.
- Зеркало jut.su (`mirrorProvider`) продолжает работать; ничего не хардкодить на `jut.su`.

## Non-goals

EN-путь и AnimeHeaven; переработка обхода франшизы в `SeasonEpisodesResolver`; UI выбора
сезона; кэш франшиз и титульных страниц; сведение четырёх источников к общему интерфейсу;
обход защиты сайтов и эмуляция JS.

## Verification commands

### Fast checks

```
./gradlew :app:compileDebugKotlin
```

### Ticket checks

```
./gradlew :app:testDebugUnitTest --tests "*JutSu*"
./gradlew :app:testDebugUnitTest --tests "*Kodik*"
./gradlew :app:testDebugUnitTest --tests "*AniLibria*"
./gradlew :app:testDebugUnitTest --tests "*Season*"
```

### Full checks

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Известно: `StatsRatingBucketTest` падал до начала работы — чужое, предшествующее падение.

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Исход резолва по каждому источнику в логе | DONE | — | не коммичен | самопроверка, без замечаний |
| TICKET-02 | Спайк: почему jut.su не отдаёт `<source>` | DONE_WITH_DEVIATIONS | — | не коммичен | самопроверка, без замечаний |
| TICKET-03 | jut.su — источник таймскипов (вариант A) | DONE_WITH_DEVIATIONS | 02 | не коммичен | самопроверка, без замечаний |
| TICKET-04 | Запрос к источнику вместо `Anime?` | DONE | — | не коммичен | самопроверка, без замечаний |
| TICKET-05 | Kodik — лестница выбора ссылки | DONE_WITH_DEVIATIONS | 04 | не коммичен | самопроверка, без замечаний |
| TICKET-06 | AniLibria — лестница опознания сезона | DONE_WITH_DEVIATIONS | — | не коммичен | самопроверка, без замечаний |
| TICKET-07 | AnimeGo — лестница алиасов | DONE | 04 | не коммичен | самопроверка, без замечаний |
| TICKET-08 | Трансляция номера сезона каталог → источник | DONE_WITH_DEVIATIONS | 05, 06, 07 | не коммичен | самопроверка, без замечаний |
| TICKET-09 | Трансляция номера сезона на резолве (+`ONA` у AniLibria) | PENDING | 08 | — | заведён из 08 |

Порядок после спайка: 01 ✓ → 02 ✓ → **04** → 06 → 05 → 07 → 08. TICKET-03 вне очереди,
ждёт решения; зависимость TICKET-08 от него снята — jut.su больше не отдаёт видео,
транслировать его номер сезона незачем.

## Ticket details

### TICKET-01 — Исход резолва по каждому источнику в логе

Status: DONE
Tracker reference: [`issues/01-per-source-outcome-logging.md`](./issues/01-per-source-outcome-logging.md)
Dependencies: —
Acceptance criteria: выполнены, кроме ручной проверки на устройстве — за пользователем
TDD: NOT_NEEDED (логирование, поведение не меняется)
Implementation summary: исход каждой ветки в `SourceEngine.safeResolve` (хостеры, играбельные
видео, длительность); явные отказы в `AniLibriaSource` и `AnimeGoSource`; провенанс строки
сезона (`источник`,`titled`/`untitled`) в `SeasonEpisodesResolver`
Deviations: `StatsRatingBucketTest` оказался зелёным — оговорка про его падение снята
Architecture notes: закрывает п. 2.3 обзора; структурных изменений нет
Verification evidence: `compileDebugKotlin` успешно; `testDebugUnitTest` → tests=269
failures=0 errors=0

### TICKET-02 — Спайк: почему jut.su не отдаёт `<source>`

Status: PENDING
Tracker reference: [`issues/02-jutsu-real-page-spike.md`](./issues/02-jutsu-real-page-spike.md)
Dependencies: —
Acceptance criteria: см. тикет
TDD: REQUIRED — результат тикета это красный тест на реальной разметке
Architecture notes: закрывает пробел «ни один тест не работает с реальной разметкой jut.su»

### TICKET-03 — jut.su: лестница извлечения видео

Status: PENDING
Tracker reference: [`issues/03-jutsu-source-extraction-ladder.md`](./issues/03-jutsu-source-extraction-ladder.md)
Dependencies: TICKET-02
Acceptance criteria: см. тикет
TDD: REQUIRED
Architecture notes: парсер остаётся чистым, лестница внутри него

### TICKET-04 — Запрос к источнику вместо `Anime?`

Status: PENDING
Tracker reference: [`issues/04-season-source-query.md`](./issues/04-season-source-query.md)
Dependencies: —
Acceptance criteria: см. тикет
TDD: REQUIRED
Architecture notes: закрывает п. 2.1 обзора; разблокирует 05 и 07

### TICKET-05 — Kodik: лестница выбора ссылки

Status: PENDING
Tracker reference: [`issues/05-kodik-season-lookup-ladder.md`](./issues/05-kodik-season-lookup-ladder.md)
Dependencies: TICKET-04
Acceptance criteria: см. тикет
TDD: REQUIRED

### TICKET-06 — AniLibria: лестница опознания сезона

Status: PENDING
Tracker reference: [`issues/06-anilibria-season-identification.md`](./issues/06-anilibria-season-identification.md)
Dependencies: —
Acceptance criteria: см. тикет
TDD: REQUIRED

### TICKET-07 — AnimeGo: лестница алиасов

Status: PENDING
Tracker reference: [`issues/07-animego-season-alias-ladder.md`](./issues/07-animego-season-alias-ladder.md)
Dependencies: TICKET-04
Acceptance criteria: см. тикет
TDD: REQUIRED для чистых функций; нужен новый тестовый шов — у AnimeGo его нет

### TICKET-08 — Трансляция номера сезона каталог → источник

Status: PENDING
Tracker reference: [`issues/08-catalogue-to-source-season-index.md`](./issues/08-catalogue-to-source-season-index.md)
Dependencies: TICKET-03, TICKET-05, TICKET-06, TICKET-07
Acceptance criteria: см. тикет
TDD: REQUIRED
Architecture notes: закрывает п. 2.2 обзора

## Decisions

1. **«Другие варианты как endpoint» = ступени лестницы в коде, не тумблеры в настройках.**
   Решение пользователя, 2026-08-01: «сделай лучший для каждого источника, а потом другие
   варианты поставь как endpoint». Пользователю нечем осмысленно выбирать между стратегиями
   парсинга, поэтому выбор делает код по порядку точности. Ступени разделены, так что вынести
   их в настройки позже можно без переделки.

2. **Инвариант «лучше пусто, чем чужой сезон» сохраняется.** Он был осознанно введён прошлой
   правкой (комментарий в `episodeUrlCandidates`: пользователь открывал 8-й сезон и попадал в
   1-й). Лестницы не отменяют инвариант — неточные ступени стоят последними и недоступны,
   пока сезон доказуем.

3. **Диагностика идёт первым тикетом.** Три из четырёх источников молчат при отказе; без
   логов проверка остальных тикетов на устройстве невозможна.

4. **Трансляция номера сезона — последним тикетом.** Она бессмысленна, пока источники не
   умеют находить видео по правильному номеру, и её эффект иначе не отличить от эффекта
   починки источников.

## Global deviations

Пока нет.

## Known risks

- **Реальная разметка jut.su может расходиться с тем, что видит устройство** (гео, куки).
  Сверять по признаку `reference=true`: base64-конфиг должен парситься в обоих случаях.
- **Возврат русских алиасов рискует вернуть матч на франшизное название вместо сезонного** —
  ровно то, что `scopedToSeason` пытался предотвратить. Держится порядком алиасов и
  отдельной проверкой принадлежности сезону в каждом источнике.
- **Неточные ступени — точка возврата исходного бага** «играет чужой сезон». Каждой нужен
  тест «при доказуемом сезоне ступень недоступна».
- **Широкий дифф TICKET-04** по четырём источникам сразу; держать механическим.
- **Рабочее дерево грязное** несвязанными правками пользователя — риск смешать их в коммит.

## Deferred work

- Свести четыре источника к общему интерфейсу с декларативной лестницей.
- Три пересекающихся порога матчера: `TitleMatcher.MATCH_THRESHOLD = 0.85`,
  `JUTSU_TITLE_MATCH_THRESHOLD = 0.91`, `VetroHttpSource.TITLE_MATCH_THRESHOLD = 0.91`.
- Кэш резолва титульной страницы: `lookfor` бьётся заново на каждую серию, в логе это
  до 10 секунд на попытку.
- Кэш франшиз AniLibria: пять запросов на каждую попытку резолва.
- `jikan` отвечает 504 на всех запросах в обоих логах — отдельный дефект, вне объёма.

## Final acceptance checklist

- [ ] Every required ticket completed
- [ ] Full test suite or agreed equivalent run
- [ ] Specification reviewed requirement by requirement
- [ ] No unresolved blocking review findings
- [ ] Migration and compatibility behavior verified
- [ ] User-visible behavior verified
- [ ] Deferred work explicitly recorded
- [ ] Final architecture checkpoint completed
