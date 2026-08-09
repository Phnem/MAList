# TICKET-08: SeriesEpisodeCheckUseCase — отслеживание вышедших серий

Status: DONE_WITH_DEVIATIONS

## Status

DONE_WITH_DEVIATIONS

## Objective

Новый юзкейс, отслеживающий вышедшие серии SERIES-записей через TMDB, с центральным
инвариантом: `Anime.episodes` для SERIES = только вышедшие серии, никогда заявленное.

## User or system value

Второй из двух приоритетных пайплайнов пользователя. Сейчас сериалы вообще не участвуют в
проверке обновлений — «вышла новая серия» работает только для аниме.

## Dependencies

TICKET-02 (`releasedEpisodesForTv`), TICKET-04 (`resolveTmdbId`), TICKET-01 (`Anime.tmdbId`).

## Scope

- Новый файл `app/src/main/java/com/example/myapplication/updates/SeriesEpisodeCheckUseCase.kt`
  (сосед `BatchEpisodeCheckUseCase`, НЕ слияние — не трогать существующий файл структурно, кроме
  точки вызова из общего триггера, см. ниже).
- Алгоритм:
  1. `localDataSource.getAllAnimeList().filter { it.mediaType == MediaType.SERIES }`.
  2. `tmdbId` — через `MovieSeriesRepository.resolveTmdbId(...)`, не через общий `searchApi`.
  3. `movieSeriesRepository.episodeState(tmdbId, clock)` → `releasedEpisodes`.
  4. **Легаси-нормализация**: если запись не помечена как "уже нормализована" (см. ниже, поле
     решить на месте — например переиспользовать существующий признак вроде
     `tmdbNotFoundAt`/новый флаг, задокументировать выбор в Implementation notes) — первый
     успешный `episodeState` для такой записи молча приравнивает `episodes` к
     `releasedEpisodes`, БЕЗ записи в `anime_update`.
  5. Иначе — сравнение `releasedEpisodes` с локальным `episodes`; при росте — запись в
     существующую `anime_update` (`anime_id/title/current_episodes/new_episodes/source="TMDB"`),
     таблица не меняется. Авто-применение — как у `BatchEpisodeCheckUseCase.applyAutomatically`.
  6. V1 без оптимизации по `SeriesStatus` — проверяются ВСЕ SERIES-записи каждый проход, без
     scheduler-полей (см. spec.md Out of scope).
- **Найти существующую точку периодического/ручного вызова `BatchEpisodeCheckUseCase.detectAndStore`**
  (вероятный кандидат — `worker/AnimeUpdateWorker.kt`, требует проверки на месте) и добавить
  параллельный вызов `SeriesEpisodeCheckUseCase.detectAndStore`.
- Проверить `EpisodeUpdateStack.kt`/`AnimeNotifier.kt` — потребители `anime_update` — не
  фильтруют строки по `mediaType == ANIME`. Если фильтруют — снять фильтр (минимально, не трогать
  остальную логику этих файлов).

## Out of scope

- Cadence-оптимизация по статусу (бэклог, требует scheduler-состояния).
- Season-level UI.

## Acceptance criteria

- [x] **Регресс-тест (центральный)**: легаси SERIES-запись `episodes = 12` (старое значение —
      заявленное), `releasedEpisodes = 7` → первый проход НЕ создаёт `anime_update "12 → 7"`,
      тихо нормализует `episodes` к `7`. После выхода 8-й серии (`releasedEpisodes = 8`) —
      обычное событие `7 → 8`.
- [x] **Регресс-тест**: запись с уже released-семантикой `episodes = 7` — повторный проход без
      изменений на стороне TMDB не создаёт события и не меняет `episodes`.
- [ ] Реальный онгоинг-сериал (текущий сезон не закончился) — `anime_update` получает
      **вышедшие**, а не все заявленные серии сезона.
      Детерминированный repository/use-case сценарий пройден; live API/device smoke отложен.
- [x] Протухший `tmdbId` (`NotFoundById`) восстанавливается через резолв по названию, не
      оставляет запись немой навсегда.
- [x] `EpisodeUpdateStack`/`AnimeNotifier` показывают SERIES-события так же, как аниме-события
      (проверено чтением кода — не фильтруют по `mediaType`).
- [x] `./gradlew :app:compileDebugKotlin` зелёный.

## Verification plan

```
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*SeriesEpisodeCheck*"
```

Ручная проверка: запустить проверку обновлений на реальном онгоинг-сериале с сохранённым
`tmdbId`, убедиться в ленте уведомлений и авто-применении счётчика.

## TDD classification

REQUIRED — легаси-нормализация и released-vs-known сравнение это самый важный инвариант всей
фичи (явно выделен как "главная поправка" в архитектурном ревью плана); должен быть red-green
протестирован до реализации, не после.

## Expected architecture impact

Новый юзкейс-сосед `BatchEpisodeCheckUseCase`, не структурная переделка существующего файла.
Точка интеграции в `worker/AnimeUpdateWorker.kt` (или где реально найдётся вызов) — минимальное
изменение, добавление параллельного вызова.

## Risks

- **Главный риск всей фичи**: если легаси-нормализация не сработает, первый проход после
  миграции покажет пользователям пачку ложных «серия пропала» уведомлений (`episodes` внезапно
  уменьшается). Регресс-тест на этот сценарий — не опционален.
- Точка вызова `BatchEpisodeCheckUseCase.detectAndStore` не локализована заранее — если она
  сложнее одного места (например, дублируется в воркере и в ручной кнопке), нужно найти оба и
  расширить оба, не только один.
- Файлы `AnimeNotifier.kt`/`EpisodeUpdateStack.kt`/`worker/AnimeUpdateWorker.kt` уже несут
  несвязанные незакоммиченные правки в рабочем дереве (см. `EXECUTION_LOG.md` →
  Constraints discovered) — коммит этого тикета неизбежно захватит и их. Зафиксировать как
  ожидаемое отклонение, не пытаться искусственно изолировать.

## Implementation notes

- Добавлена локальная таблица-маркер `series_episode_normalization` и миграция 14. Новые
  SERIES помечаются атомарно вместе с вставкой через `AnimeDatabase.insertNewAnime(anime)`;
  импортированные/мигрированные legacy-записи маркера не имеют.
- Первый успешный lookup legacy SERIES сохраняет только `releasedEpisodes` и ставит маркер без
  события. Последующий рост создаёт обычный `AnimeUpdate`; `knownEpisodes` не используется.
- `EpisodeUpdateFeed.publishEpisodeUpdates` централизует merge/dedup/auto-apply общей ленты, а
  `EpisodeUpdateCheckCoordinator` последовательно запускает anime и SERIES проверки из Home и
  фонового worker.
- Протухший сохранённый id проходит `NotFoundById` → повторный resolve по названию; `Failure`
  ничего не очищает и не нормализует.

## Deviations

- Вместо переиспользования несвязанного признака выбран отдельный marker-table без timestamp:
  семантика явная, а миграция не меняет модель `Anime`.
- После standards-review общий feed и две точки запуска вынесены в shared policy/coordinator,
  поэтому `BatchEpisodeCheckUseCase` получил небольшую структурную правку сверх первоначального
  ожидания «не трогать».
- Реальная ручная проверка с TMDB и Android-уведомлением не выполнялась; equivalent behavior
  проверено детерминированными unit/integration тестами и чтением consumers.

## Review findings

- Исправлен BLOCKING: новые SERIES раньше могли ошибочно считаться legacy. Теперь production
  insertion сам выводит marker из `anime.mediaType == SERIES` и делает обе записи транзакционно.
- Устранены standards IMPORTANT: дублирование merge/auto-apply общей ленты и orchestration в
  Home/Worker.
- Усилен регресс-тест production wiring: `AnimeInsertionTest` вызывает реальный no-flag seam,
  поэтому ошибка классификации SERIES больше не может быть скрыта fake-настройкой теста.
- Финальные Spec и Standards re-review: нерешённых BLOCKING/IMPORTANT нет.

## Completion evidence

- Code commit: `1777d36`.
- `:core:network:testDebugUnitTest` + `:app:testDebugUnitTest`: 435/435, failures=0, errors=0.
- `:app:assembleDebug`, `git diff --check`: успешно.
- Покрыты legacy 12→7 без события, затем 7→8; unchanged; stale-id replacement; Failure;
  shared-feed preservation; атомарный marker для новых SERIES и его отсутствие у ANIME/MOVIE.
