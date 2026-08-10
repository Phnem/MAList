# TICKET-05: Multi-candidate aggregation and ranking

## Status

DONE

## Objective

Каскад собирает кандидатов со всех провайдеров и ранжирует их единым слоем вместо неупорядоченного
списка и захардкоженного `reliabilityRank()`.

## Dependencies

TICKET-01, TICKET-04.

## Acceptance criteria

- [x] `A error + B not found + C found` → C.
- [x] Несколько кандидатов доходят до вызывающего.
- [x] Ranking детерминирован и покрыт тестами.
- [x] Точность матча приоритетнее качества.
- [x] Health подключается без переделки ранжирования.

## TDD classification

REQUIRED

## Implementation notes

`MovieSeriesCandidate` несёт провайдера, точность, язык, перевод, latency и `hosterUrl`. Эти поля
живут на кандидате, а не на `VetroVideo`, чтобы служебные данные провайдера не доходили до плеера.

Порядок задан явной цепочкой сравнений, а не одним непрозрачным числом:
`accuracy → язык запроса → health → близость качества → isPreferred → latency → стабильный tiebreak`.

`ProviderResolution.Found` получил опциональные `accuracy` и `language`. Отсутствующая точность
ранжируется как слабейшая улика, а не как сильнейшая: адаптер, который не может идентифицировать
тайтл, не должен выигрывать от молчания.

`healthPenalty` — инжектируемый параметр каскада, поэтому TICKET-06 подключается без переделки.

## Deviations

- Planned: «заменяет захардкоженный `reliabilityRank()`».
- Actual: `rankVideosForResolution`/`reliabilityRank` оставлены нетронутыми; новый слой действует
  только на MOVIE/SERIES.
- Reason: `VideoRanking` общий с аниме-каскадом, и предпочтение `libria.fun` — аниме-специфичное.
  Его замена изменила бы порядок источников в ANIME, что запрещено разделом 23 задания.
- Consequence: для MOVIE/SERIES хардкода нет; в ANIME он остаётся.
- Follow-up: удаление `reliabilityRank` возможно только вместе с отдельным пересмотром ANIME-ранжирования.

## Review findings

Два теста каскада упали на сравнении объектов целиком. Разбор показал два изменения round-trip:

1. `video.sourceName` теперь заполняется именем хостера — это восстановление атрибуции, на которую
   плеер и так рассчитывал (`flattenVideosWithSource`). Оставлено, тесты приведены к проверке URL.
2. `hoster.url` подставлялся из URL видео — это выдумывание данных за провайдера. **Исправлено**:
   кандидат несёт `hosterUrl` и возвращает его дословно.

## Completion evidence

- Command: `gradlew :core:network:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- app: 486 tests, 0 failures, 0 errors (было 472). `MovieSeriesRankingTest` — 14 тестов.
- Commit: `fe4e474`.
