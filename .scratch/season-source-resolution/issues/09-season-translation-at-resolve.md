# TICKET-09: Трансляция номера сезона на резолве

## Status

PENDING

## Objective

Довести трансляцию шкал до места, где номер сезона ещё используется как индекс: франшизный
путь AniLibria. Заведён из TICKET-08.

## User or system value

`SeasonIndexTranslation` из TICKET-08 применён только в слиянии раскладов, потому что там обе
цепочки доступны. На резолве источники получают единственный `SeasonInfo` и полной цепочки
не видят, поэтому `selectAniLibriaSeasonRelease` по-прежнему берёт релиз по сырому индексу
`.getOrNull(seasonInfo.seasonNumber - 1)`.

Сейчас это не приводит к выдаче чужого сезона — TICKET-06 оставил обязательное подтверждение
`releaseIdentifiesSelectedSeason`, поэтому неверный индекс даёт пусто. Но это потерянные
резолвы там, где релиз есть.

## Dependencies

TICKET-08

## Scope

- Прокинуть цепочку сезонов каталога (или готовую трансляцию) через `MediaGateway` →
  `SourceEngine` → источники. Цепочка уже лежит в `SeasonEpisodesStore`, сеть не нужна.
- Применить `translateCatalogueSeason` во франшизном пути AniLibria вместо сырого индекса.
- Добавить `ONA` в `ANILIBRIA_SEASON_TYPES` — перенесено из TICKET-06: список типов задаёт
  шкалу сезонов источника, и менять его осмысленно только вместе с трансляцией.

## Out of scope

- Сетевые вызовы `findSeasons` на резолве.
- Kodik и AnimeGo: у них индексной зависимости не осталось (TICKET-05 и TICKET-07).

## Acceptance criteria

- [ ] Источники получают трансляцию без дополнительных сетевых запросов
- [ ] Франшизный путь AniLibria выбирает релиз по транслированному номеру
- [ ] `ONA`-релизы участвуют в шкале сезонов, спешлы — нет
- [ ] Недоказуемая трансляция по-прежнему даёт пусто, а не догадку
- [ ] Существующие тесты AniLibria остаются зелёными

## Verification plan

```
./gradlew.bat :app:testDebugUnitTest
```

## TDD classification

REQUIRED

## Expected architecture impact

Расширяется контракт между `SourceEngine` и источниками. Проектировать так, чтобы источник
получал уже готовый номер в своей шкале, а не цепочку для самостоятельного разбора.

## Risks

- Прокидывание цепочки через `MediaGateway` затрагивает четыре вызывающих `resolveHosters`.
- `ONA` может протащить спешлы и сдвинуть индекс — проверять на фикстуре франшизы Food Wars.

## Implementation notes

Empty before implementation.

## Deviations

Empty before implementation.

## Review findings

Empty before review.

## Completion evidence

Empty before completion.
