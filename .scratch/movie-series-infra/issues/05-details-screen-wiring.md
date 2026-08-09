# TICKET-05: Details-экран для MOVIE/SERIES

Status: DONE

## Status

DONE

## Objective

Подключить `fetchDetails` к `MovieSeriesRepository` для MOVIE/SERIES — сейчас Details-экран для
таких записей не подгружает и не обновляет данные вообще.

## User or system value

Открытие Details ранее добавленного фильма/сериала сейчас либо пусто, либо не обновляется.
После этого тикета — работает так же, как у аниме.

## Dependencies

TICKET-04.

## Scope

- `core/network/.../network/ApiService.kt` — `fetchDetails` получает `externalIds: ExternalIds`,
  `appContentType: AppContentType? = null`.
- `VetroApiService.fetchDetails` — ветка `appContentType in {MOVIE, SERIES} ->
  movieSeriesRepository.fetchDetails(...)`.
- `app/.../ui/details/DetailsViewModel.kt` (вызов ~строка 113) — передать
  `ExternalIds(tmdb = anime.tmdbId, kinopoisk = anime.kinopoiskId)`, `appContentType` из
  `anime.mediaType`.
- **КРИТИЧНО**: этот путь не имеет права трогать `Anime.episodes` для SERIES — Details-refresh
  обновляет описание/постер/жанры/id, не эпизод-счётчик. Явная проверка/тест на это (пересекается
  с инвариантом из TICKET-08, но должна быть покрыта уже здесь, потому что именно здесь
  открывается первая реальная возможность случайно затронуть `episodes` через `fetchDetails`).
- **Проверить фактическую модель названий**: если MOVIE/SERIES использует `titleEn`/`titleRu`
  раздельно от `title` (как аниме), убедиться, что добавление на RU не «теряет» название при
  переключении приложения на EN. Задокументировать найденное поведение в Implementation notes —
  это открытый вопрос из spec.md.

## Out of scope

- `AddFromApiUseCase` (TICKET-06).
- Season-level UI, чипы «Сезоны» — вне скоупа всей фичи (бэклог).

## Acceptance criteria

- [ ] Открытие Details для существующей MOVIE/SERIES записи с сохранённым `tmdbId` подгружает
      описание/постер/жанры (не пусто).
- [ ] Открытие Details для записи БЕЗ `tmdbId` (легаси) резолвит по названию и результат не
      теряется молча.
- [ ] Регресс-тест: `fetchDetails` для SERIES не изменяет `episodes` ни при каком входе.
- [ ] Ручной сценарий: добавить сериал на RU, переключить приложение на EN, открыть Details —
      название корректно показывается (или зафиксировано как известное ограничение с описанием
      поведения, если модель title не поддерживает раздельное хранение для MOVIE/SERIES).
- [ ] `./gradlew :app:compileDebugKotlin` зелёный.

## Verification plan

```
./gradlew :core:network:compileDebugKotlin
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*DetailsViewModel*"
```

Ручная проверка: открыть Details реального фильма/сериала на RU и EN.

## TDD classification

RECOMMENDED — в основном wiring, но регресс-тест "episodes не тронут" обязателен (см. Scope).

## Expected architecture impact

Минимальный — расширение существующего интерфейса `ApiService.fetchDetails` опциональными
параметрами, обратная совместимость для ANIME/MANGA сохраняется (новые параметры не влияют на
их путь).

## Risks

- Если модель `titleEn`/`titleRu` не подходит для MOVIE/SERIES "из коробки" — есть риск скрытого
  UX-регресса (название "теряется" при смене языка). Тикет обязан явно проверить и
  задокументировать поведение, не может закрыться молча, если проверка не проводилась.

## Implementation notes

- Введён единый `DetailsLookupRequest` на сетевой границе; `DetailsViewModel` строит его из
  `Anime`, а `AnimeRepository`/`ApiService` передают как один value object.
- MOVIE/SERIES передают `ExternalIds(tmdb, kinopoisk)` и точный `AppContentType`; EN-гейт,
  требующий anime-id/titleEn, применяется только к ANIME. Legacy MOVIE/SERIES без id
  резолвятся по основному title.
- `MovieSeriesRepository.fetchDetails` принудительно очищает `episodesAired`/`episodesTotal`
  для SERIES на TMDB-only, RU merged и Kinopoisk-only ветвях. Stored `Anime.episodes` в details
  path вообще не передаётся и не изменяется.
- **Проверка модели названий**: `Anime` хранит `title`, `titleEn`, `titleRu` раздельно, а
  `DetailsScreen` выбирает локализованное поле с fallback на `title`. Для legacy RU-записи без
  `titleEn` после переключения на EN заголовок остаётся RU (не теряется и не блокирует lookup),
  при этом EN-details загружаются через TMDB. Автосохранение найденного EN alias в TICKET-05 не
  добавлялось; корректное заполнение локалей при новом добавлении относится к TICKET-06.

## Deviations

- Вместо расширения уже длинной сигнатуры `fetchDetails` ещё двумя параметрами использован
  `DetailsLookupRequest`. Поведение из спеки сохранено, но интерфейс стал глубже и следующие id
  не потребуют синхронных правок четырёх слоёв.

## Review findings

- Первое ревью: кодовая проводка корректна; найдены слабые tests для SERIES invariant,
  незаполненная документация title-language поведения и data-clump длинной сигнатуры.
- Исправлено: единый request object, явная sanitation episode counts, тесты TMDB-only/RU
  merge/Kinopoisk-only, описание legacy RU→EN поведения.
- Повторное ревью: все обязательные находки RESOLVED, новых блокеров нет.

## Completion evidence

- `./gradlew.bat :core:network:testDebugUnitTest :app:testDebugUnitTest
  :core:network:compileDebugKotlin :app:compileDebugKotlin` → BUILD SUCCESSFUL;
  401 тест, failures=0, errors=0.
- `git diff --check` → без ошибок.
- Commit: `TICKET-05_COMMIT`.
