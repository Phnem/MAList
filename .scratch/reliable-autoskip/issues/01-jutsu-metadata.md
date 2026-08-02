# TICKET-01: Надёжная metadata-разметка jut.su

Status: DONE

## Objective

Получать из episode HTML playable sources, duration и exact/reference skip metadata, а title искать по aliases только при score `>= 0.91`.

## User or system value

Даёт таймкоды даже когда video jut.su недоступно или пользователь воспроизводит другой hoster.

## Dependencies

Нет.

## Scope

`VetroSkipReference`, pure parser, placeholder filtering, alias search, jut.su adapter.

## Out of scope

Применение reference к другим hosters.

## Acceptance criteria

- [x] Full/start-only/end-only/outro/malformed Base64 fixtures покрыты.
- [x] Placeholder sources отбрасываются.
- [x] Alias order RU/main/EN покрыт.
- [x] Weak result ниже `0.91` отвергается.
- [x] jut.su hoster несёт exact timestamps и reference metadata.

## Verification plan

Новые parser/search JVM-тесты и `:app:testDebugUnitTest` с фильтром по ним.

## TDD classification

REQUIRED

## Expected architecture impact

Новый глубокий parser module; сетевой source остаётся adapter.

## Risks

Варианты HTML и Base64 с переносами.

## Implementation notes

Добавлены pure episode parser и отдельный title-search module. `JutSuSource` остаётся сетевым
adapter и допускает reference-only hoster.

## Deviations

End-only intro реализован как `max(0, end-89s)` по прямому уточнению пользователя.

## Review findings

Первое review обнаружило ошибочное использование global threshold `0.85`, недостаточный playable
filter и недостающие boundary tests. Исправлено: local threshold `0.91`, HTML candidate test,
strict media/placeholder filtering и точные 89-second fixtures.

## Completion evidence

Targeted `JutSuEpisodePageParserTest` и `JutSuTitleSearchTest` — BUILD SUCCESSFUL.

`git diff --check` по файлам тикета — без замечаний.
