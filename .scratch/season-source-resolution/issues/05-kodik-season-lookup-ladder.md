# TICKET-05: Kodik — лестница выбора ссылки на серию

## Status

DONE_WITH_DEVIATIONS

## Objective

Вернуть Kodik способность находить серию, когда релиз нумерует сезоны по-своему, не потеряв
защиту от подмены сезона.

## User or system value

Лог: `No Kodik candidates for 'Food Wars! The Second Plate' ep=2 / ep=3 / ep=4` — Kodik
не отдал ни одного кандидата на трёх сериях подряд.

Причина в `selectKodikSerialEpisodeLink`:

```kotlin
if (linksBySeason != null) {
    // An explicit map is authoritative.
    return selectKodikEpisodeLink(linksBySeason, season, episode)
}
```

Карта объявлена авторитетной и индексируется номером сезона **приложения**. У Kodik сиквел —
как правило отдельный релиз со своей нумерацией (`seasons: {"1": …}`), поэтому ключ `2`
не находит ничего. Прежний `episodeLink()` перебирал все сезоны карты и находил серию.

Комментарий в коде верен по намерению — откат на релизный iframe действительно давал
S1E4 вместо S2E4. Но лечение оказалось строже болезни.

## Dependencies

TICKET-04 (запрос к источнику несёт русский алиас и признак доказуемости)

## Scope

Лестница в `selectKodikSerialEpisodeLink`, по убыванию точности:

1. **Точный ключ сезона в карте** — `linksBySeason[season][episode]`. Как сейчас.
2. **Односезонный релиз** — если в карте ровно один сезон и релиз найден по сезонному
   названию (признак доказуемости из TICKET-04), его единственный сезон и есть запрошенный;
   берём `episode` из него.
3. **Перебор сезонов карты по номеру серии** — только когда сезон не доказуем иначе; ступень
   помечается неточной и стоит последней.
4. **Query-параметры на релизном iframe** — существующая ветка `withKodikEpisodeParams`,
   допустима лишь при `lastSeason == season`.

`isKodikStandaloneEligible(season, episode)` (`season == 1 && episode == 1`) пересмотреть:
фильм/OVA, привязанный к сезону ≥2, сейчас недостижим.

## Out of scope

- Экстрактор Kodik (расшифровка iframe) — работает.
- yummy-путь как таковой; он оживает от TICKET-04.
- Трансляция номера сезона (TICKET-08).

## Acceptance criteria

- [ ] Релиз с картой `{"1": {…}}`, найденный по названию второго сезона, отдаёт серию
- [ ] Релиз с картой `{"1": …, "2": …}` по-прежнему отдаёт серию запрошенного сезона
- [ ] Ни одна ступень не отдаёт серию другого сезона, когда сезон доказуем
- [ ] Неточная ступень не срабатывает, пока доступна точная
- [ ] `episode > last_episode` по-прежнему отсекается до экстрактора
- [ ] Каждая ступень покрыта тестом в `KodikSeasonSelectionTest`
- [ ] Регресс-тест на исходный дефект: S1E4 не выдаётся за S2E4

## Verification plan

```
./gradlew :app:testDebugUnitTest --tests "*KodikSeasonSelection*"
./gradlew :app:testDebugUnitTest
```

Ручная проверка: S2E2 «Повар-боец Сома» — в логе `Kodik direct '…' S2E2 candidates=N`, N > 0.

## TDD classification

REQUIRED — чистый выбор по структуре данных, шов `KodikSeasonSelectionTest.kt` уже есть.

## Expected architecture impact

Лестница прячется внутри `KodikDirectSearch`, сигнатура `findEpisodeCandidates` не растёт
(кроме уже запланированной замены `seasonNumber` на транслированный в TICKET-08).

## Risks

- Ступень 3 — точка возврата исходного бага «играет чужой сезон». Она обязана быть
  недоступна при доказуемом сезоне; это проверяется отдельным тестом.
- `last_season` у Kodik бывает `0` — не путать с «сезон 1».

## Implementation notes

`selectKodikSerialEpisodeLink` получил параметр `seasonIdentifiable` (из
`SeasonSourceQuery`, TICKET-04) и лестницу:

1. Точный ключ сезона в карте — как было.
2. Односезонный релиз: если карта содержит ровно один сезон, берём серию оттуда. Ступень
   доступна, только когда подмене взяться неоткуда — релиз найден по названию самого сезона
   (`seasonIdentifiable`) либо просят первый сезон.
3. Релизный iframe с query-параметрами — только при отсутствии карты и `lastSeason == season`.

Это закрывает корень отказа: у Kodik сиквел — отдельная запись, её единственный сезон лежит
под ключом «1» независимо от того, какой это сезон франшизы, а мы спрашивали ключ «2».

`isKodikStandaloneEligible` переписан с `season == 1 && episode == 1` на
`episode == 1 && (season == 1 || seasonIdentifiable)`: фильм/OVA, привязанный к сезону ≥2
и найденный по его названию, стал достижим.

Флаг прокинут `KodikSource` → `findEpisodeCandidates` → `pickCandidates` → `toCandidate`.

## Deviations

- Planned: ступень 3 «перебор сезонов карты по номеру серии», доступная при недоказуемом
  сезоне и помеченная неточной.
- Actual: ступень **не реализована**.
- Reason: она срабатывала бы ровно в том случае, для которого опасна. Недоказуемый сезон
  означает, что релиз найден только по франшизному названию; перебор тогда почти наверняка
  вернёт серию первого сезона — тот самый дефект «открыл 8-й сезон, попал в 1-й», ради
  которого карту и сделали авторитетной. Инвариант плана «лучше пусто, чем чужой сезон»
  прямо это запрещает.
- Consequence: недоказуемый сезон ≥2 с многосезонной картой и без точного ключа даёт пусто.
  Такие случаи закрывает TICKET-08 (трансляция номера), а не угадывание.
- Follow-up: нет; TICKET-08 покрывает остаток.

## Review findings

- **Возврат исходного бага** — проверено тестами: `serial with only season one cannot satisfy
  an unprovable season two` (пусто) и `multi season map without the requested key refuses even
  when provable` (пусто). Неточного пути, способного отдать чужой сезон, в коде не осталось.
- **Совместимость** — прежние проверки сохранены: точный ключ, отсечение `episode >
  last_episode`, query-параметры только для своего сезона. Старые тесты не переписаны по
  смыслу, к ним добавлен явный аргумент.
- **`last_season == 0`** — не путается с «сезон 1»: ветка query-параметров по-прежнему
  требует `lastSeason > 0`.
- **Поверхность** — сигнатура `findEpisodeCandidates` выросла на один булев параметр,
  функция `internal`.

Блокирующих замечаний нет.

## Completion evidence

- Command: `./gradlew.bat :app:testDebugUnitTest` → `BUILD SUCCESSFUL`,
  агрегат: `tests=277 failures=0 errors=0` (+4 к TICKET-04)

Файлы: `media/source/KodikDirectSearch.kt`, `media/source/KodikSource.kt`,
`test/.../KodikSeasonSelectionTest.kt`.
