# Architecture review — movie-series-infra

Проведено как три раунда критики плана в plan mode (не отдельный `/improve-codebase-architecture`
прогон — эквивалентная по глубине работа уже выполнена интерактивно с пользователем). Здесь —
итоговые ответы на обязательные вопросы фазы 5.

## 1. Какие существующие границы помогают/мешают?

Помогают: `core/network` уже физически отделён от `app` (Gradle-модуль), уже есть паттерн
"data source per provider" (`ShikimoriRemoteDataSource` и т.д.), уже есть паттерн
"unified search-result model" (`ApiSearchResult`), уже есть fill-gap (не blind-replace) философия
в `RepairAnimeDbUseCase.applyRepair`.

Мешают: `VetroApiService` — фасад, в который исторически стекалась вся оркестрация нового
источника (RU/EN-роутинг, фоллбэки, дедуп) вместо data source-специфичного слоя. Именно так
туда изначально попал TMDB-код инлайн. Первая версия этого плана была готова повторить ту же
ошибку (собрать TMDB+Kinopoisk-оркестрацию прямо в `VetroApiService`) — исправлено введением
`MovieSeriesRepository`.

## 2. Нужен ли prefactoring до фичи?

Нет отдельного prefactoring-тикета. `TICKET-01` (MediaType split + схема) сам по себе —
необходимый enabling-шаг, но не рефакторинг существующего рабочего кода за пределами того, что
прямо требуется.

## 3. Какие модули должны остаться стабильными?

`manga/` пакет (`VetroMangaSource`, `MangaSourceEngine`) — не трогается. Аниме-пайплайны
(`BatchEpisodeCheckUseCase`, title-enrichment юзкейсы, `RecommendationEngine`) — не трогаются
по логике, только по фильтру `mediaType` там, где он уже есть (не добавляется новый).

## 4. Где прятать сложность?

- Сетевая оркестрация (RU/EN routing, fallback, дедуп, merge, id-resolution) → внутри
  `MovieSeriesRepository`.
- Различие released/known episode count и определение `SeriesStatus` → внутри
  `TmdbRemoteDataSource`/`MovieSeriesRepository`, наружу выходит только
  `SeriesEpisodeState`.
- Семантика not-found vs failure → внутри `LookupResult`, вызывающий код не парсит HTTP-коды
  сам.

## 5. Какие публичные интерфейсы меняются?

- `ApiService.fetchDetails` — новые опциональные параметры (`externalIds`, `appContentType`).
- `ApiSearchResult` — новое поле `externalIds: ExternalIds` (аддитивно, старые поля не тронуты).
- `Anime`/`SaveAnimeParams` — новые опциональные поля (`tmdbId`, `kinopoiskId`, `*NotFoundAt`).
- `MediaType` — удаляется константа `TV_SERIES`, добавляются `MOVIE`/`SERIES` (breaking на
  уровне enum, компенсируется legacy-алиасом в парсинге).

## 6. Какие архитектурные улучшения обязательны сейчас?

- `MovieSeriesRepository` как отдельный слой (не в `VetroApiService`, не в `app`).
- `LookupResult` как единый контракт (не ad-hoc эвристики по HTTP-кодам/тексту исключения).
- `ExternalIds` вместо очередного bonus-поля по образцу `malId` на `ApiSearchResult`.

Классификация: **REQUIRED_BEFORE_IMPLEMENTATION** — все три; без них TICKET-04+ пришлось бы
переделывать.

## 7. Какие улучшения — просто желательное follow-up?

- Миграция существующих аниме/манга-источников на `ExternalIds` (сейчас у них остаются старые
  `externalId: String?`/`malId: Int?`) — **FOLLOW_UP**, не в этой фиче.
- `lastEpisodeCheckAt`/scheduler-состояние для cadence-оптимизации по `SeriesStatus` —
  **FOLLOW_UP**, отдельная итерация (см. spec.md Out of scope).
- Provider-specific рейтинг-колонки (`tmdb_rating`/`kinopoisk_rating`) — **FOLLOW_UP**, если
  merge-policy на едином `rating` окажется недостаточной на практике.

## 8. Архитектурные риски, за которыми должен следить ревью каждого тикета

- **Риск №1 (главный)**: незаметное протекание `knownEpisodes`/`number_of_episodes` в
  `Anime.episodes` для SERIES через любой путь, кроме `SeriesEpisodeCheckUseCase`. Каждый
  тикет, трогающий `SaveAnimeParams`/`applyRepair`/`fetchDetails` для MOVIE/SERIES, обязан
  явно подтвердить, что `episodes` не тронут (или тронут только по правилу MOVIE=1).
- **Риск №2**: `MovieSeriesRepository` начинает обрастать сеттерами БД/зависимостью от `app` —
  ревью каждого тикета проверяет отсутствие импортов `Anime`/`AnimeLocalDataSource` в
  `core/network/.../network/movie/`.
- **Риск №3**: `*_not_found_at` проставляется на `Failure` вместо `NoMatch` — легко ошибиться
  при копипасте из старого `isTransientFailure`-паттерна `BatchEpisodeCheckUseCase`. Ревью
  явно ищет каждое место простановки not-found и проверяет тип `LookupResult`, из которого оно
  вызвано.
- **Риск №4**: дедуп на ступени "localized title + year" трактуется как точное совпадение
  вместо confidence-порога — ревью TICKET-04/06 явно проверяет, что ступень 4 каскада не
  мержит автоматически без высокого порога `TitleMatcher`.

## Границы, специфичные для этой задачи

`MovieSeriesRepository` — новая граница между `core/network` data source'ами (TMDB/Kinopoisk) и
`VetroApiService`. Работает только с сетевыми моделями. Локальная БД и use-case'ы, которым
нужен доступ к `AnimeLocalDataSource`/`Anime`, остаются в `app` и обращаются к репозиторию
исключительно через `AnimeRepository`/`ApiService`, как уже сегодня для аниме/манги.
