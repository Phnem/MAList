# TICKET-05: Падающий тест StatsRatingBucketTest.buckets_continuous_noGaps

## Status

PENDING — независимый дефект, обнаружен попутно. Не блокирует ничего в этом прогоне.

## Objective

`StatsRatingBucketTest > buckets_continuous_noGaps` проходит.

## User or system value

Красный тест в наборе обесценивает весь набор: следующий разработчик привыкает, что «один всегда падает», и перестаёт замечать настоящие регрессии.

## Dependencies

Нет.

## Происхождение тикета

**Discovery source.** Обнаружен при первом полном прогоне `:app:testDebugUnitTest` в TICKET-02 (до этого тесты гонялись по фильтру `--tests`, и падение не попадало в выборку).

**Не регрессия от моей работы.** Доказательства:

- Тест живёт в `app/src/test/java/com/example/myapplication/domain/stats/StatsPhraseUnitTest.kt`, последний коммит файла — `43706ad` «huge update», задолго до начала прогонов `vetro-todo` и `vetro-player`.
- `git diff --name-only HEAD~6..HEAD -- app/src/main/java/com/example/myapplication/domain/stats/` пуст: ни один файл в пакете статистики мной не тронут.

**Почему отдельным тикетом, а не правкой на месте:** по правилам скилла независимый дефект чинится немедленно только если мешает верификации, грозит потерей данных или делает текущую реализацию недействительной. Ничего из этого здесь нет — падение изолировано в статистике и к жестам плеера отношения не имеет.

## Scope

`app/src/main/java/com/example/myapplication/domain/stats/StatsRatingBucket.kt` и тест в `StatsPhraseUnitTest.kt`.

Симптом: `org.junit.ComparisonFailure: expected:<[24]> but was:<[02]>`. Судя по значениям, речь о границах соседних корзин рейтинга — вероятно, разрыв или перехлёст диапазона. Диагностику проводить по `/diagnosing-bugs`, а не гадать.

## Out of scope

Остальная статистика. Переписывание модели корзин.

## Acceptance criteria

- [ ] Установлена корневая причина: неверны границы корзин или неверно ожидание в тесте.
- [ ] `.\gradlew.bat :app:testDebugUnitTest` зелёный целиком.
- [ ] Если ошибка была в продакшен-коде — добавлена регрессионная проверка на соседние границы.

## Verification plan

`.\gradlew.bat :app:testDebugUnitTest`.

## TDD classification

REQUIRED — воспроизводимый дефект детерминированной логики; тест уже есть и уже красный.

## Expected architecture impact

Нет.

## Risks

Ожидание в тесте может оказаться неверным, а код правильным — тогда «починка» кода сломала бы настоящее поведение статистики. Сначала выяснить, какая сторона права.

## Implementation notes

## Deviations

## Review findings

## Completion evidence
