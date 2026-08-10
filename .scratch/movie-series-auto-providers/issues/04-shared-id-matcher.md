# TICKET-04: Shared ID-first matcher

## Status

DONE

## Objective

Вынести правила точности сопоставления из Jellyfin/Emby в общий чистый matcher с приоритетом
TMDB → IMDb → Kinopoisk → normalized title + year.

## User or system value

Раздел 13 задания требует ID-first сопоставления с едиными правилами точности. Сейчас они заперты
внутри `PersonalMediaServerPlaybackSource.identityRelation()`, не знают про IMDb и не проверяют год,
а каждый будущий провайдер (TICKET-07/08) вынужден был бы изобретать их заново — и почти наверняка
слабее.

## Dependencies

TICKET-01, TICKET-03.

## Scope

- `MediaIdentity` (tmdbId, imdbId, kinopoiskId, title, year).
- `MatchAccuracy`: TMDB_ID → IMDB_ID → KINOPOISK_ID → TITLE_AND_YEAR → TITLE_ONLY.
- `IdentityMatch`: `Matched(accuracy)` | `Conflict` | `NoEvidence`.
- `MediaIdentityMatcher.match` + `selectUnique`.
- Общая нормализация названия и IMDb id.
- Перевод Jellyfin/Emby на общий matcher без изменения поведения.

## Out of scope

Ранжирование по точности (TICKET-05 использует `MatchAccuracy`, но не задаёт его здесь).

## Acceptance criteria

- [x] Каждый уровень точности покрыт тестом.
- [x] Конфликтующий ID дисквалифицирует кандидата, даже если другой ID совпадает.
- [x] Неоднозначное совпадение (несколько кандидатов на сильнейшем уровне) не выбирается.
- [x] Совпадение только по названию при расходящихся годах отвергается.
- [x] IMDb id сравнивается без учёта регистра.
- [x] Поведение Jellyfin/Emby не изменилось (существующие тесты зелёные).

## Verification plan

Тесты чистого matcher'а; существующие `PersonalMediaServerPlaybackSourceTest`; полные суиты.

## TDD classification

REQUIRED

## Expected architecture impact

Закрывает пункт D из INITIAL_REVIEW.

## Risks

Изменение правил отбора может незаметно ослабить точность Jellyfin/Emby.

## Implementation notes

`MediaIdentity`, `MatchAccuracy`, `IdentityMatch`, `MediaIdentityMatcher` в пакете `movieseries`.
Порядок объявления enum и есть ранжирование: `compareTo` идёт по `ordinal`, поэтому отдельной
таблицы весов, способной разойтись с реальностью, не существует.

Ключевые правила:
- конфликтующий ID дисквалифицирует кандидата, даже если другой ID совпадает;
- рассматривается только сильнейший уровень, давший совпадения (слабое совпадение не должно
  разрешать ничью между сильными);
- то же название в другом году отвергается — ремейки делят имя;
- неоднозначность внутри уровня даёт `null`, а не произвольный выбор.

Jellyfin/Emby переведены на общий matcher и попутно получили сопоставление по IMDb и
`ProductionYear`, которых раньше не использовали.

## Deviations

Нет.

## Review findings

Блокирующих находок нет. Отсутствие регресса Jellyfin/Emby подтверждено отдельно: 10 тестов
`PersonalMediaServerPlaybackSourceTest` не менялись и проходят.

## Completion evidence

- Command: `gradlew :core:network:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- app: 472 tests, 0 failures, 0 errors (было 452). `MediaIdentityMatcherTest` — 20 тестов
  (подтверждено по XML-отчёту).
- `IdentityRelation` и локальный `normalizeTitle` удалены.
- Commit: `7f23901`.
