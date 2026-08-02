# Current handoff

## Original goal

Вернуть выдачу видео по сезонам, которые не резолвятся ни одним RU-источником. Для каждого
источника — лучшая стратегия плюс запасные ступени. Инвариант «лучше пусто, чем чужой сезон»
сохраняется.

Триггер: «Повар-боец Сома», S2 и S6 не дают ни одного играбельного видео.

## Canonical artifacts

- [`MASTER_PLAN.md`](./MASTER_PLAN.md) · [`spec.md`](./spec.md) ·
  [`architecture/INITIAL_REVIEW.md`](./architecture/INITIAL_REVIEW.md) ·
  [`EXECUTION_LOG.md`](./EXECUTION_LOG.md) · тикеты `issues/01`…`issues/09`

## Current workflow state

WAITING_FOR_USER_DECISION. Выполнено 7 тикетов: 01, 02, 04, 05, 06, 07, 08.
TICKET-03 остановлен, TICKET-09 заведён как продолжение TICKET-08.

## Completed tickets

| ID | Итог | Что сделано |
|---|---|---|
| 01 | DONE | исход каждого источника в логе + провенанс строки сезона |
| 02 | DONE_WITH_DEVIATIONS | спайк: jut.su отдаёт заглушки вместо видео-URL |
| 04 | DONE | `SeasonSourceQuery` вместо `Anime?`; русский алиас сохранён |
| 05 | DONE_WITH_DEVIATIONS | Kodik: односезонный релиз со своей нумерацией |
| 06 | DONE_WITH_DEVIATIONS | AniLibria: лестница вместо обрыва на точном совпадении |
| 07 | DONE | AnimeGo: русская лестница запросов + подтверждение сезона |
| 08 | DONE_WITH_DEVIATIONS | сопоставление шкал по числу серий, применено в слиянии |

## Active ticket

Нет.

## Next eligible ticket

TICKET-03 — после решения пользователя по jut.su (три варианта в теле тикета).
TICKET-09 — доступен независимо.

## Decisions that must be preserved

1. «Endpoint» = ступень лестницы в коде, не тумблер в настройках (решение пользователя).
2. **Инвариант «лучше пусто, чем чужой сезон»**. Он трижды пересилил букву плана: в 05
   не реализован перебор сезонов карты Kodik, в 06 не ослаблены `singleOrNull` и не добавлена
   нечёткая ступень, в 08 недоказуемая трансляция отказывает вместо догадки.
3. jut.su больше не источник видео — только таймскипов (факт, не выбор; см. TICKET-02).

## Deviations that affect later work

- Индексная зависимость осталась во франшизном пути AniLibria (TICKET-09). Чужой сезон она
  выдать не может — подтверждение из TICKET-06 обязательно, — но теряет резолвы.
- `ONA` в `ANILIBRIA_SEASON_TYPES` перенесён в TICKET-09.
- `CURRENT_SCHEMA` не поднят: состав полей `SeasonInfo` не менялся.

## Current repository state

Ветка `vetro-todo`. **Ничего не коммичено** — рабочее дерево изначально содержало обширные
несвязанные правки пользователя, изолировать коммиты без явного разрешения нельзя.

Изменены этой работой:

```
app/src/main/java/com/example/myapplication/media/source/SourceEngine.kt
app/src/main/java/com/example/myapplication/media/source/SeasonSourceAnime.kt
app/src/main/java/com/example/myapplication/media/source/AniLibriaSource.kt
app/src/main/java/com/example/myapplication/media/source/AnimeGoSource.kt
app/src/main/java/com/example/myapplication/media/source/AnimeGoTitleSearch.kt        (новый)
app/src/main/java/com/example/myapplication/media/source/KodikSource.kt
app/src/main/java/com/example/myapplication/media/source/KodikDirectSearch.kt
app/src/main/java/com/example/myapplication/domain/seasons/SeasonEpisodesResolver.kt
app/src/main/java/com/example/myapplication/domain/seasons/StreamingSeasonDiscovery.kt
app/src/main/java/com/example/myapplication/domain/seasons/SeasonIndexTranslation.kt  (новый)
app/src/test/.../SeasonSourceAnimeTest.kt, KodikSeasonSelectionTest.kt,
               AniLibriaSeasonSelectionTest.kt, JutSuEpisodePageParserTest.kt
app/src/test/.../AnimeGoTitleSearchTest.kt                                            (новый)
app/src/test/.../domain/seasons/SeasonIndexTranslationTest.kt                         (новый)
app/src/test/resources/jutsu/season2-episode2-new-player.html                         (новый)
```

## Relevant commits

Нет.

## Verification already performed

- `./gradlew.bat :app:testDebugUnitTest` → `tests=299 failures=0 errors=0`, наборов 52
- `./gradlew.bat :app:assembleDebug` → `BUILD SUCCESSFUL`
- Тестов было 269 на старте, стало 299.

Базовая линия оказалась чище ожидаемой: `StatsRatingBucketTest`, числившийся падающим,
проходит.

## Known failures or blockers

- TICKET-03 ждёт решения по jut.su.
- Ручная проверка на устройстве не выполнялась ни по одному тикету — нужен прогон приложения.

## Files most relevant to the next ticket

TICKET-09: `media/MediaGateway.kt`, `media/MediaGatewayImpl.kt`,
`media/source/SourceEngine.kt`, `media/source/AniLibriaSource.kt` (строка ~247,
`.getOrNull(seasonInfo.seasonNumber - 1)`), `domain/seasons/SeasonIndexTranslation.kt`.

## Exact recommended next action

Прогнать приложение на «Повар-боец Сома» S2E2 и S6E1, снять логкат и сверить четыре строки
исхода источников — это единственная непроверенная часть работы. Затем выбрать вариант
по TICKET-03.
