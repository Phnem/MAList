# TICKET-02: TmdbRemoteDataSource — типизированный DTO-слой TMDB

## Status

PENDING

## Objective

Вынести TMDB из инлайн-парсинга в `VetroApiService` в отдельный типизированный data source с
DTO, починить баг MOVIE→`/search/tv`, ввести разделение released/known episode count.

## User or system value

Сейчас TMDB-код (`searchTmdbMovie`/`searchTmdbTv`/`parseTmdbResults`/`checkTmdb`) — сырой
`JsonObject`-парсинг внутри 900-строчного `VetroApiService`, без типобезопасности, с багом:
MOVIE ищет через `/search/tv`. Этот тикет — сетевой фундамент, от которого зависит вообще всё
остальное (поиск, детали, отслеживание серий).

## Dependencies

TICKET-01 (`LookupResult` контракт должен существовать).

## Scope

- `core/network/.../network/dto/TmdbDto.kt` — `@Serializable` DTO поиска/деталей (movie/tv/season):
  `overview`, `poster_path`, `vote_average`, `genres`, `status`, `in_production`, `id`,
  `title`/`name`, `seasons[]` (`season_number`, `episode_count`, `air_date`), season-details
  (`episodes[].air_date`), `runtime`/`episode_run_time`.
- `core/network/.../network/tmdb/TmdbRemoteDataSource.kt`:
  - `searchMovie(query, language, year: Int? = null): LookupResult<List<ApiSearchResult>>`
    (через `/search/movie`, `primary_release_year`).
  - `searchTv(query, language, year: Int? = null): LookupResult<List<ApiSearchResult>>`
    (через `/search/tv`, `first_air_date_year`).
  - `movieDetails(id, language): LookupResult<...>`, `tvDetails(id, language): LookupResult<...>`
    — `NotFoundById` на TMDB 404, `Failure` на прочие ошибки.
  - `seasonDetails(tvId, seasonNumber, language): LookupResult<...>`.
  - `releasedEpisodesForTv(id, clock: Clock): LookupResult<Int>` — см. алгоритм в разделе 3
    плана: season 0 не считать; сезон целиком по `episode_count` ТОЛЬКО если существует более
    поздний уже начавший выходить сезон; последний начавший выходить сезон — через
    `seasonDetails`, считать эпизоды с `air_date <= now(clock)`; `seasonDetails` недоступен →
    не досчитывать текущий сезон вовсе, вернуть только доказанно закрытые предыдущие.
  - `knownEpisodesForTv(id): LookupResult<Int>` — простая сумма `episode_count`, `season_number > 0`
    — только display, никуда, кроме display, не идёт (не тянуть за собой семантику "released").
  - `seriesStatus(tvDetails): SeriesStatus` маппер (`enum { UPCOMING, ONGOING, ENDED, CANCELLED, UNKNOWN }`)
    из TMDB `status`, не по голому `in_production`.
- Баг чинится естественно самой заменой: MOVIE больше не ходит в `/search/tv`.
- `BuildConfig.TMDB_API_KEY` инкапсулируется внутри класса (уже объявлен в
  `core/network/build.gradle.kts`, только переносится место использования).
- **Старый инлайн-код в `VetroApiService` (`searchTmdbMovie`, `searchTmdbTv`,
  `parseTmdbResults`, `checkTmdb`) удаляется в этом тикете** — не оставлять dead code рядом с
  новым; замена вызовов на новый класс — часть TICKET-04, здесь достаточно, чтобы старый код
  был физически удалён и `VetroApiService` временно не компилировался бы без TICKET-04
  (нормально: тикеты этой цепочки идут последовательно, промежуточное состояние не публикуется).

  Если раздельная сборка TICKET-02 без TICKET-04 нежелательна (ломает CI на промежуточном
  коммите), альтернатива — оставить старый инлайн-код нетронутым в TICKET-02 и физически удалить
  его только в TICKET-04 одновременно с переключением вызовов. **Решение: второй вариант** —
  TICKET-02 добавляет новый класс, ничего не удаляет из `VetroApiService`, поэтому дерево
  компилируется на каждом шаге.

## Out of scope

- Kinopoisk (TICKET-03).
- `MovieSeriesRepository`/переключение `VetroApiService` на новый класс (TICKET-04).
- Реальная замена старого инлайн-кода — только добавление нового, старое остаётся мёртвым до
  TICKET-04 (см. решение выше).

## Acceptance criteria

- [ ] `TmdbRemoteDataSource` компилируется, покрыт юнит-тестами на `releasedEpisodesForTv` с
      фейковыми/моковыми данными сезонов и фиксированным `Clock`.
- [ ] Тест: сезон без более позднего начавшегося сезона → НЕ засчитывается по одной эвристике
      `air_date` (то есть требует `seasonDetails`; при недоступности `seasonDetails` — не
      засчитывается вовсе).
- [ ] Тест: сезон с более поздним уже начавшимся сезоном → засчитывается целиком по
      `episode_count`, без обращения к `seasonDetails`.
- [ ] Тест: MOVIE-поиск использует `/search/movie`, не `/search/tv` (баг исправлен — проверяется
      через мок HTTP-клиента, что запрошен правильный путь).
- [ ] `movieDetails`/`tvDetails` возвращают `LookupResult.NotFoundById` на 404, `Failure` на
      прочих ошибках (таймаут/5xx/парсинг), `NoMatch` не используется на точечных запросах по id
      (это семантика поиска, не lookup по id).
- [ ] `seriesStatus` маппер покрыт тестом на все известные строки TMDB `status`.
- [ ] `./gradlew :core:network:compileDebugKotlin` зелёный; существующая сборка `VetroApiService`
      не сломана (новый класс не подключён никуда, значит и не может его сломать).

## Verification plan

```
./gradlew :core:network:compileDebugKotlin
./gradlew :core:network:testDebugUnitTest --tests "*Tmdb*"
```

## TDD classification

REQUIRED — `releasedEpisodesForTv` это в точности "calculations"/"API mapping"/"fallback
selection" из обязательного TDD-списка ticket-autopilot, и это самая рискованная логика всей
фичи (регресс-предупреждение из ревью плана про season air_date).

## Expected architecture impact

Новый модуль внутри `core/network`, не меняет публичные контракты `ApiService`/`VetroApiService`
в этом тикете (изолированное добавление).

## Risks

- Season air_date heuristic — главный риск всей фичи, уже дважды провален в черновиках плана
  до финальной версии. Тесты должны explicitly покрывать оба некорректных варианта из истории
  ревью ("air_date сезона = дата премьеры, не завершения" и "заявленное ≠ вышедшее").
- TMDB season-details endpoint может быть недоступен/менять формат — `Failure`-путь обязан
  тестироваться отдельно от happy path.

## Implementation notes

Empty before implementation.

## Deviations

Empty before implementation.

## Review findings

Empty before review.

## Completion evidence

Empty before completion.
