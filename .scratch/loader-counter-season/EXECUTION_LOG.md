# Журнал выполнения

## Initial codebase discovery — 2026-08-02

### Relevant modules

- `media/ui/` — `StreamPlayerActivity`, `StreamPlayerSurface`, `StreamLoadingVisibility`,
  `FrozenFrame` (мёртвый)
- `localplayer/ui/PlayerControls.kt` — общий для локального и стримового плееров
- `ui/shared/loading/` — `BubbleClusterLoader` + `BubbleClusterMotion`, `Katana*` (к удалению)
- `ui/home/` — `HomeScreen`, `HomeViewModel`, `AnimeCard`
- `media/source/` — `KodikSource`, `KodikDirectSearch`, `SeasonSourceAnime`, `AniLibriaSource`
- `domain/seasons/`, `data/local/SeasonEpisodesStore`

### Existing behavior

Разобрано по трём дефектам в [`spec.md`](./spec.md), раздел «Current behavior».

### Existing terminology

`SeasonSourceQuery` / `seasonIdentifiable`, `SeasonInfo.episodes` («доступно сейчас») против
`totalEpisodes` («анонсировано»), сквозная франшизная нумерация просмотра.

### Existing tests

299 тестов на 2026-08-01 (замер пакета `season-source-resolution`). Есть
`StreamLoadingVisibilityTest`, `KatanaCycleTest`, тесты Kodik direct-пути. У yummy-пути
`KodikSource` тестов нет вовсе.

### Constraints discovered

Лимит 255 полей `UiStrings`; ловушка `layerBackdrop`; `R`-класс только `com.phnem.vetro.R`;
`minSdk = 26`.

### Questions answerable from code

- Где считается `loading` и где теряется → `StreamPlayerSurface.kt:233,245`,
  `PlayerControls.kt:531-558`.
- Откуда знаменатель карточки → `HomeScreen.kt:648`, `anime.episodes`.
- Почему Kodik отдал чужой сезон → `KodikSource.yummyCandidates` не знает о сезоне.

### Remaining material uncertainties

Сняты интервью 2026-08-02: судьба катаны, способ починки знаменателя, поведение в PiP.
См. «Decisions» в [`MASTER_PLAN.md`](./MASTER_PLAN.md).

## Диагностика по логкату — 2026-08-02

Пользователь приложил `vetro_logcat_2.txt` (1.6 МБ, сессия 18:40–18:46).

### Что показал лог

Сессия по тайтлу «Низкоуровневый персонаж Томодзаки» / «Bottom-Tier Character Tomozaki».
Переходы: S1E11 → S1E12 → S2E1 → S2E2.

| Строка | Событие |
|---|---|
| 2134 | `AniLibriaSource: No release matched … S2E1` — честный отказ |
| 2146 | `AnimeGoSource: No season-confirmed match for S2E1` — честный отказ |
| 2151 | `KodikSource: Resolved Kodik S2E1 dubbings=8 (yummy=7, direct=8)` |
| 5110 | `AnimeGoSource: No season-confirmed match for S2E2` |
| 5115 | `KodikSource: Resolved Kodik S2E2 dubbings=8 (yummy=7, direct=8)` |

Все чанки играли с `selectedSource=Kodik___Озвучка_AniLibria`.

### Ключевой поворот: лог сам по себе дефекта не показывает

По строкам лога резолв S2E1 выглядит успешным — номер сезона в сообщениях правильный. Дефект
опознан только после уточнения пользователя: «запустил S1E11, открыл S2E1 — включился S1E1,
затем S2E2 — включился S1E2, всё из Kodik».

Это и есть суть ошибки: `KodikSource` **логирует запрошенный** номер сезона
(`"Resolved Kodik S${seasonInfo?.seasonNumber ?: 1}E$episodeNumber"`, строка 91), а не тот,
которому принадлежит выданный релиз. Лог не мог показать расхождение — он печатал то, что
просили, а не то, что нашли.

### Корневая причина

`KodikSource.yummyCandidates` (строка 100) о сезоне не знает ничего. `findRelease` ищет по набору
`[titleRu, title, titleEn]`; у сезона ≥2 `titleRu` намеренно остаётся франшизным русским
названием (`SeasonSourceAnime.kt:19`) — то есть названием **первого** сезона. Оно же лежит в
`localTitles`, по которым считается счёт, поэтому релиз первого сезона получает максимальный
балл. Дальше серия выбирается по одному лишь номеру (`episodeMatches`, строка 177): запросили
серию 1 второго сезона — получили серию 1 первого.

Direct-путь при этом корректен: он получает `seasonNumber` и `seasonIdentifiable`, сверяется с
`last_season` и картой `seasons.{N}.episodes.{M}` (`KodikDirectSearch.kt:104-110`), — это было
исправлено в TICKET-05 пакета `season-source-resolution`. Yummy-путь тем тикетом не тронули.

Оба набора кандидатов сливаются через `distinctBy { it.iframeUrl }` (строка 70), поэтому семь
кандидатов чужого сезона попали в общий список вперемешку с корректными.

### Побочные наблюдения (вне объёма)

- `SeasonEpisodesWorker` трижды отменялся; `franchise batch failed (2 ids) — entry stays
  incomplete`, `resolve failed for "Восхождение героя щита": Job was cancelled`.
- `BatchEpisodeCheck: byId distrusted` — два русских названия против
  `Re:Zero … 2nd Season Part 2`; недоверие сработало правильно.
- jut.su по-прежнему отдаёт `0 qualities, reference=true` — источник таймскипов, не видео
  (подтверждение вывода TICKET-02 прошлого пакета).

### Проверка старой гипотезы

Первым подозреваемым был незакрытый TICKET-09 пакета `season-source-resolution` — франшизный
индекс AniLibria (`.getOrNull(seasonInfo.seasonNumber - 1)`, `AniLibriaSource.kt:247`). Строка
действительно не исправлена, но к этому дефекту отношения не имеет: в логе AniLibria отказала
оба раза, а видео пришло от Kodik. TICKET-09 остаётся отложенным.

### Next eligible ticket

TICKET-01.

## 2026-08-02 — Прогон тикетов 01–04

### Outcome

Все четыре закрыты. Блокирующих находок ревью нет.

| ID | Итог | Тесты после |
|---|---|---|
| 01 | DONE_WITH_DEVIATIONS | 313 |
| 02 | DONE | 297 |
| 03 | DONE | 304 |
| 04 | DONE | 311 |

Базовая линия оказалась выше записанной в плане: 313, а не 299 — с 2026-08-01 пакет
`player-and-reco-upgrade` добавил тестов.

### Главный поворот: дефект №1 был не там, где предполагалось

Разведка исходила из того, что в плеере работает `KatanaLoadingOverlay` из пакета
`katana-loader`. Оказалось, что он не подключён к плееру вовсе: `FrozenFrame.kt` (TICKET-04
того пакета) не имеет НИ ОДНОГО вызова с момента появления, а плеер показывает совсем другой
`BubbleClusterLoader`. То есть тикеты 01–04 пакета `katana-loader` числятся DONE, но их результат
до пользователя не дошёл.

Это выяснилось до правок и изменило постановку: пользователь на вопрос ответил, что катана была
неудачным экспериментом и её надо вычистить, а не подключать. Так появился TICKET-02, которого
в исходной заявке не было.

### Дефект №3: почему лог молчал

`KodikSource` логировал ЗАПРОШЕННЫЙ номер сезона, а не сезон найденного релиза:
`"Resolved Kodik S${seasonInfo?.seasonNumber}E$episodeNumber"`. Поэтому строка
`Resolved Kodik S2E1` в логкате выглядела успехом, хотя играла S1E1. Дефект был структурно
невидим в диагностике — опознан только по описанию пользователя.

Корень: `yummyCandidates` о сезоне не знал ничего, а русский алиас в наборе запросов — это
название первого сезона. Direct-путь был починен ещё в TICKET-05 пакета
`season-source-resolution`, yummy-путь тогда не тронули.

Добавлена строка на отказ: `Yummy: no season-confirmed release for S{n} (N rejected)`.

### Дефект №2: две шкалы в одной строке

Числитель сквозной по франшизе, знаменатель — плоское поле одного сезона. Попутно найдено
нерабочее ограничение `progress.coerceAtMost(totalEpisodes.coerceAtLeast(progress))` —
алгебраически тождество `progress`: защита, которая никогда не срабатывала. Заменять её
работающим зажимом отказались: он показывал бы «12 / 12» вместо реальных 15 просмотренных.

### Инвариант снова пересилил удобство

TICKET-04: отсев неподтверждённых релизов поставлен ДО выбора лучшего по счёту, а не после.
Наивный порядок обнулял бы yummy-путь целиком — релиз первого сезона выигрывает счёт у
правильного всегда, когда искали франшизным русским названием.

### Verification

`./gradlew.bat :app:testDebugUnitTest` → `suites=56 tests=311 failures=0 errors=0`;
`./gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL. Оба новых набора писались тестом вперёд,
красный зафиксирован. Ручная проверка на устройстве не выполнялась ни по одному тикету.

### New risks

- Yummy-путь может замолчать на сезоне ≥2, если yani.tv не отдаёт релиз по английскому названию
  сезона. Сетевого прогона не было — это главный непроверенный вопрос пакета.
- Неполный расклад сезонов занижает знаменатель карточки.

### Follow-up work

- Тумблер `if (!isInPip)` в `StreamPlayerSurface` выключает весь Compose-слой одним махом.
- `.scratch/season-source-resolution/` TICKET-09 (франшизный индекс AniLibria) и TICKET-03
  (решение по jut.su) остаются открытыми — к этим трём дефектам отношения не имеют.

### Next eligible ticket

Нет. Пакет закрыт по коду, дальше приёмка на устройстве.
