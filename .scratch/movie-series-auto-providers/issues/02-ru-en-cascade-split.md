# TICKET-02: RU/EN cascade routing split

## Status

DONE

## Objective

Разделить единый `PlaybackRoute.DirectOnly` на явные RU и EN маршруты MOVIE/SERIES и переименовать
его: имя описывало прямой URL, а маршрут давно означает весь controlled-source путь.

## User or system value

Раздел 11 задания требует раздельные каскады, а не один список. Явный маршрут даёт место, куда
TICKET-05 повесит порядок и ранжирование по языку, и делает выбор набора провайдеров проверяемым
чистой функцией вместо неявного побочного эффекта фильтра внутри каскада.

## Dependencies

TICKET-01.

## Scope

- `PlaybackRoute.DirectOnly` → `MovieSeriesRu` / `MovieSeriesEn` (в стиле существующих
  `AnimeRu`/`AnimeEn`).
- `PlaybackRoutingPolicy` маршрутизирует MOVIE/SERIES по языку.
- Извлечь выбор провайдеров в именованную чистую функцию `selectMovieSeriesProviders`.
- `SourceEngine` диспатчит по новым маршрутам.

## Out of scope

Порядок и ранжирование провайдеров (TICKET-05). Ручной выбор языка в UI (TICKET-10) — архитектура
уже не запрещает его, `language` приходит в `PlaybackRequest`.

## Acceptance criteria

- [x] RU и EN дают разные наборы провайдеров для одного и того же тайтла.
- [x] ANIME-маршруты идентичны прежним.
- [x] MANGA по-прежнему `None`.
- [x] Политика и выбор провайдеров — чистые функции под тестами.
- [x] Имя `DirectOnly` больше не встречается.

## Verification plan

Обновлённый `PlaybackRoutingPolicyTest` + тесты `selectMovieSeriesProviders`; полные суиты.

## TDD classification

REQUIRED

## Expected architecture impact

Закрывает пункт B из INITIAL_REVIEW.

## Risks

Маршрутизация — общая точка для ANIME; ошибка здесь ломает аниме-воспроизведение.

## Implementation notes

`PlaybackRoute.DirectOnly` → `MovieSeriesRu`/`MovieSeriesEn`. `PlaybackRoutingPolicy` переписан на
исчерпывающий `when (mediaType)`: новый media type теперь не компилируется вместо тихого падения в
`None`.

Выбор провайдеров извлечён в чистую `selectMovieSeriesProviders`, что позволило проверить составы
RU- и EN-каскадов без запуска провайдеров.

Персональные источники остаются language-agnostic и отвечают в обоих каскадах.

## Deviations

Нет.

## Review findings

Блокирующих находок нет.

## Completion evidence

- Command: `gradlew :core:network:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- app: 452 tests, 0 failures, 0 errors (было 444).
- `DirectOnly` в коде отсутствует; остался только в KDoc, объясняющем переименование.
- Commit: `beb039c`.
