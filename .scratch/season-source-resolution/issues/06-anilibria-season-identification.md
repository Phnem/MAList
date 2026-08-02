# TICKET-06: AniLibria — лестница опознания сезона

## Status

DONE_WITH_DEVIATIONS

## Objective

Заменить требование точного равенства английскому названию сезона на лестницу опознания,
пригодную для русского каталога.

## User or system value

Лог показывает полный проход AniLibria впустую: поиск по трём алиасам, затем обход франшиз
`421, 2628, 5150, 8510, 8620` — и ноль результатов. Пять сетевых запросов, выброшенных
предикатом:

```kotlin
if (exactSeasonTitle.isNotEmpty()) {
    val normalizedSeasonTitle = normalizeAniLibriaTitle(exactSeasonTitle)
    return releaseTitles.any { normalizeAniLibriaTitle(it) == normalizedSeasonTitle }
}
```

`seasonInfo.title` — английское название из AniList (`titleEnglish ?: titleRomaji`).
Названия релизов AniLibria русские («Повар-боец Сома 2»). Нормализация снимает только
пунктуацию и регистр, но не язык, поэтому равенство ложно **всегда** для сезона ≥2.
Предикат гасит и `findRelease(requiredSeason)`, и франшизный `selectAniLibriaSeasonRelease`.

## Dependencies

—

## Scope

Лестница в `releaseIdentifiesSelectedSeason`, по убыванию точности:

1. **Точное совпадение с сезонным названием** — как сейчас, остаётся первой ступенью
   (работает, когда у релиза есть английское имя).
2. **Оценка `TitleMatcher.bestScore` по всем именам релиза** (`main`, `english`,
   `alternative`) против всех алиасов запроса, включая русский из TICKET-04, с порогом.
3. **Порядковый маркер сезона** — существующая ветка с цифрой в названии/алиасе; оставить
   как последнюю ступень, она уже написана.

Дополнительно ослабить fail-closed во франшизном пути:

- `matches.singleOrNull()` → при нескольких совпадениях выбирать лучшее по счёту,
  а не отказывать;
- обязательное равенство `episodes_total == expectedEpisodes` → допуск, когда сезон
  онгоинг либо `expectedEpisodes` неизвестен;
- `ANILIBRIA_SEASON_TYPES = {"TV"}` → добавить `ONA` (часть сиквелов помечена так).

## Out of scope

- Парсер эпизодов AniLibria (`parseAniLibriaV1Episode`) — работает.
- Прямые ссылки из `WebLinks` — работают.
- Кэш франшиз (follow-up).

## Acceptance criteria

- [ ] Релиз с русским названием и цифрой сезона опознаётся как запрошенный сезон
- [ ] Релиз, принадлежащий другому сезону франшизы, по-прежнему отвергается
- [ ] Сезон 1 продолжает опознаваться без маркера (ранний `return true`)
- [ ] Франшизный путь выбирает лучшее совпадение вместо отказа при нескольких
- [ ] Онгоинг с неизвестным `episodes_total` не отбраковывается
- [ ] Каждая ступень покрыта тестом в `AniLibriaSeasonSelectionTest`
- [ ] Число сетевых запросов на успешном пути не выросло

## Verification plan

```
./gradlew :app:testDebugUnitTest --tests "*AniLibria*"
./gradlew :app:testDebugUnitTest
```

Ручная проверка: S2E2 «Повар-боец Сома» — в логе `Franchise season 2 -> '…'` либо
играбельное видео от AniLiberty.

## TDD classification

REQUIRED — чистый предикат и чистый выбор из JSON, оба шва уже под тестом.

## Expected architecture impact

Внутри `AniLibriaSource`, публичная сигнатура не меняется. Ступени остаются `internal`
чистыми функциями.

## Risks

- Ослабление предиката рискует вернуть сезон-сосед. Митигация: ступень 2 сравнивает со
  **всеми** алиасами, включая сезонное название первым, и порог остаётся; тест на
  «сосед не проходит» обязателен.
- Добавление `ONA` в типы сезонов может протащить спешлы. Проверить на тестовых данных
  франшизы из лога (`421, 2628, 5150, 8510, 8620`).

## Implementation notes

Корень оказался не в строгости предиката, а в **преждевременном `return`**:

```kotlin
if (exactSeasonTitle.isNotEmpty()) {
    return releaseTitles.any { normalize(it) == normalize(exactSeasonTitle) }   // ← обрыв
}
val number = ...  // порядковый маркер — сюда не доходили никогда
```

Сезонное название приходит из AniList по-английски, релизы AniLibria названы по-русски,
поэтому первая ступень ложна всегда при сезоне ≥2 — и гасила вторую, которая как раз
работает («Повар-Боец Сома 2» содержит токен «2»).

Заменено на честную лестницу: точное совпадение → при неудаче **проваливаемся** на
порядковый маркер. Сигнатура не изменилась.

## Deviations

Три пункта исходного объёма не выполнены, каждый — осознанно.

1. **Нечёткая ступень `TitleMatcher.bestScore` по названиям релиза — НЕ добавлена.**
   - Reason: пользы нет (русское имя релиза с английским сезонным не сходится ни при каком
     пороге), а вред есть — усечённое название соседнего сезона проходило бы по порогу.
     Существующий тест `subset title from another season is not accepted as exact season`
     прямо требует отклонять «Shokugeki no Souma» при запросе «…San no Sara»; нечёткая
     ступень его ломает.
   - Consequence: лестница из двух ступеней вместо трёх, обе точные.

2. **`matches.singleOrNull()` → «лучшее по счёту» — НЕ сделано.**
   - Reason: это намеренный fail-closed, закреплённый тестом `ambiguous matching franchises
     fail closed`. Выбор «лучшего» из неоднозначных франшиз — это ровно выдача соседнего
     сезона за запрошенный, что запрещено инвариантом плана.
   - Consequence: неоднозначные франшизы по-прежнему дают пусто.

3. **Допуск по `episodes_total` и добавление `ONA` в типы сезонов — НЕ сделано.**
   - Reason по эпизодам: требование уже выполняется. `expectedEpisodes` вычисляется как
     `totalEpisodes ?: episodes.takeIf { !ongoing && it > 0 }`, то есть у онгоинга с
     неизвестным итогом он `null`, и обе строгие проверки пропускаются. Ослаблять нечего.
   - Reason по `ONA`: список типов управляет тем, что считается сезоном при индексации
     `.getOrNull(seasonNumber - 1)`. Менять его — значит менять шкалу сезонов источника,
     а это предмет TICKET-08, а не точечной правки. Иначе легко сдвинуть индекс и сломать
     тест `food wars third tv release is selected without counting specials`.
   - Consequence: перенесено в TICKET-08.

## Review findings

- **Сосед не проходит** — закреплено новым тестом `ordinal marker of a neighbouring season
  is not accepted`: «Повар-Боец Сома 3» и «Повар-Боец Сома» при запросе сезона 2 дают `false`.
- **Реальный случай закрыт** — тест `russian release with an ordinal marker satisfies an
  english season title` воспроизводит связку из лога: сезонное название
  «Shokugeki no Souma: Ni no Sara», релиз «Повар-Боец Сома 2».
- **Ранний выход на сезоне 1** сохранён.
- **Ложные срабатывания маркера** — регэксп требует номер отдельным токеном, поэтому
  «2nd» в алиасе не считается за сезон 2.
- **Сеть** — число запросов не изменилось: правка чисто предикатная.

Блокирующих замечаний нет.

## Completion evidence

- Command: `./gradlew.bat :app:testDebugUnitTest` → `BUILD SUCCESSFUL`,
  агрегат: `tests=279 failures=0 errors=0` (+2 к TICKET-05)

Файлы: `media/source/AniLibriaSource.kt`, `test/.../AniLibriaSeasonSelectionTest.kt`.
