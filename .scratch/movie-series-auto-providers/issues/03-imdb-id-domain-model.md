# TICKET-03: IMDb ID in domain model and persistence

## Status

DONE

## Objective

Довести IMDb ID от metadata-провайдеров до `Anime`, `PlaybackIdentity` и БД, включая миграцию схемы.

## User or system value

IMDb ID — второй уровень приоритета идентификации (раздел 13 задания) и **единственный** адресный
ключ Stremio-транспорта: id фильма `tt1254207`, id эпизода `tt0898266:9:17`. Без него TICKET-08
нереализуем, а TICKET-04 теряет уровень точности.

Сейчас IMDb ID существует только как `KinopoiskDto.imdbId` → `KinopoiskDetails.externalImdbId` и
дальше никуда не доходит: ни в `ExternalIds`, ни в `Anime`, ни в схему.

## Dependencies

None.

## Scope

- Миграция `15.sqm`: `ALTER TABLE anime ADD COLUMN imdb_id TEXT`.
- `Anime.sq`: колонка + участие в selects, `insertAnime`, `updateAnime`, `updateAnimeKeepingDirty`,
  `upsertFromSync` (через self-select, как `tmdb_id`), `setImdbId`.
- `Anime.imdbId: String?`.
- `PlaybackIdentity.imdbId` + `from()`/`toAnime()`.
- `ExternalIds.imdb: String?`; Kinopoisk-маппер заполняет его из уже существующего `imdbId`.
- TMDB details запрашивают `append_to_response=external_ids` и мапят `imdb_id`.
- Персистенс IMDb ID там же, где сохраняются tmdb/kinopoisk id.

## Out of scope

Синхронизация IMDb ID в облако (следует прецеденту `tmdb_id`/`kinopoisk_id`: локально, сохраняется
через self-select при pull). Использование IMDb ID в матчинге — TICKET-04.

## Acceptance criteria

- [x] Миграция применяется к существующей БД; старые строки валидны с `imdb_id IS NULL`.
- [x] IMDb ID сохраняется и читается через `AnimeLocalDataSource`.
- [x] `upsertFromSync` не обнуляет локально проставленный `imdb_id`.
- [x] `PlaybackIdentity` переносит IMDb ID в обе стороны.
- [x] Kinopoisk details отдают IMDb ID в `ExternalIds`.
- [x] TMDB details отдают IMDb ID в `ExternalIds`.
- [x] IMDb ID хранится как TEXT (`tt0412142`), не как число.

## Verification plan

MockEngine-тесты на маппинг TMDB/Kinopoisk; тест миграции/round-trip через `AnimeLocalDataSource`;
тест `PlaybackIdentity`; полные суиты + debug-сборка.

## TDD classification

REQUIRED

## Expected architecture impact

Закрывает пункт C из INITIAL_REVIEW. Разблокирует TICKET-04 и TICKET-08.

## Risks

Миграция схемы затрагивает существующие пользовательские данные. `upsertFromSync` переписывает
строку целиком (`INSERT OR REPLACE`) — пропущенная колонка молча обнуляется при каждом pull.

## Implementation notes

Миграция `15.sqm` добавляет `anime.imdb_id` как TEXT: канонический id (`tt0412142`) имеет значащий
префикс и ведущие нули, INTEGER их бы потерял.

Колонка локальная, как `tmdb_id`/`kinopoisk_id`, и переносится через pull self-select'ом в
`upsertFromSync` — иначе `INSERT OR REPLACE` молча стирал бы её при каждой синхронизации.

TMDB: `/3/movie/{id}` отдаёт `imdb_id` штатно, `/3/tv/{id}` — нет, поэтому `tvDetails` теперь
запрашивает `append_to_response=external_ids`.

Пустая строка нормализуется в null и в сети, и в `setImdbId`: `""` читался бы как «уже разрешено» и
навсегда остановил бы повторное обогащение.

## Deviations

- Planned: только добавление колонки и проброс модели.
- Actual: дополнительно исправлен `Migration13Test`.
- Reason: тест останавливается на схеме 14, но читал БД через сгенерированные запросы, которые
  всегда описывают ПОСЛЕДНЮЮ схему. С добавлением `imdb_id` он упал. Это не случайность, а
  системная хрупкость: он ломался бы на любой следующей миграции.
- Consequence: тест теперь проверяет через raw SQL, как соседний `mediaTypeOf`.
- Follow-up: нет.

## Review findings

Регрессия поймана прогоном, а не ревью: `Migration13Test > no rows are lost...` упал после
добавления колонки. Исправлено переводом на raw SQL. Остальные AC подтверждены тестами.

## Completion evidence

- Command: `gradlew :core:network:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- app: 444 tests, 0 failures, 0 errors (было 439). core/network: 60 tests (было 55).
- `Migration15Test` — 3 теста, включая защиту от обнуления при pull.
- `TmdbImdbIdTest` — 5 тестов (подтверждено по XML-отчёту, не только по BUILD SUCCESSFUL).
- Commit: `3b4f966`.
