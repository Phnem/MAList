# TICKET-06: AddFromApiUseCase — ExternalIds вместо source-веток

## Status

PENDING

## Objective

Переключить сохранение при добавлении фильма/сериала на `ExternalIds` вместо ветвления по
строке `result.source`, зафиксировать правило `episodes` для новых MOVIE/SERIES записей.

## User or system value

Сейчас добавление фильма/сериала через поиск не сохраняет ни `tmdbId`, ни `kinopoiskId` — этот
тикет замыкает цепочку "нашёл → сохранил id", без которой TICKET-07/08 не имеют смысла (нечего
чинить/отслеживать без сохранённого id).

## Dependencies

TICKET-04.

## Scope

- `app/.../domain/search/AddFromApiUseCase.kt`:
  - Читает `result.externalIds.tmdb`/`.kinopoisk` вместо веток `result.source.equals("...")`
    для MOVIE/SERIES-путей (аниме/манга-ветки — `anilist`/`mal`/`shikimori` — не трогать).
  - Пишет в `SaveAnimeParams.tmdbId`/`.kinopoiskId`.
  - `episodes` для SERIES при добавлении = `1` (только что вышедший тайтл) — **не**
    `number_of_episodes`/сумма сезонов из карточки поиска (тот же инвариант, что в TICKET-08).
    `episodes` для MOVIE = `1` (уже так, но явно закрепить тестом).
  - `findExistingDuplicate` — та же форма пробы, сравнение через `ExternalIds`.
- `sync/DuplicateTitleRule.kt` — расширить сравнение id аналогично (если применимо к
  MOVIE/SERIES).

## Out of scope

- Repair/gap detector (TICKET-07).
- Season/episode tracking после добавления (TICKET-08).

## Acceptance criteria

- [ ] Добавление фильма/сериала из поиска (RU и EN) сохраняет `tmdbId` (и `kinopoiskId`, если
      результат пришёл с RU) в БД.
- [ ] Регресс-тест: новая SERIES-запись получает `episodes = 1`, не заявленное число серий из
      карточки поиска.
- [ ] Повторное добавление того же тайтла (по `tmdbId`/`kinopoiskId`) распознаётся как дубликат.
- [ ] Существующий ANIME/MANGA-путь `AddFromApiUseCase` не изменился по поведению (регресс-тест
      на существующих сценариях, если есть, иначе — ручная проверка).
- [ ] `./gradlew :app:compileDebugKotlin` зелёный.

## Verification plan

```
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*AddFromApiUseCase*"
```

## TDD classification

REQUIRED — id-извлечение и duplicate-detection это "fallback selection"/"data merging"; риск
регресса на существующем ANIME/MANGA-пути требует теста до правки.

## Expected architecture impact

Нулевой структурный — использование уже готового `ExternalIds` (TICKET-01) и
`MovieSeriesRepository` (TICKET-04) там, где раньше был ad-hoc парсинг строки `source`.

## Risks

- Смешение веток ANIME/MANGA (`source`-based) и MOVIE/SERIES (`ExternalIds`-based) в одном
  файле — риск случайно сломать существующий аниме-путь при рефакторинге. Не переписывать
  анимешные ветки "заодно", трогать только код, относящийся к MOVIE/SERIES.

## Implementation notes

Empty before implementation.

## Deviations

Empty before implementation.

## Review findings

Empty before review.

## Completion evidence

Empty before completion.
