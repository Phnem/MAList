# Обзор архитектуры под задачу

Область — три несвязанных места: слой загрузочного UI плеера, вычисление прогресса на главном
экране, yummy-путь `KodikSource`. Общей архитектурной проблемы у них нет, поэтому обзор
ограничен вопросами, влияющими на декомпозицию.

## 1. Какие границы помогают, какие мешают

**Помогает.** `shouldShowStreamLoading` (`media/ui/StreamLoadingVisibility.kt`) уже выделен в
чистую функцию с тестами — решение «показывать ли загрузку» отделено от Compose. Расширение
контракта на PiP ложится в существующий шов, новый слой не нужен.

**Помогает.** `SeasonSourceQuery` (`media/source/SeasonSourceAnime.kt`) — готовый контракт
«запрос, сужённый до сезона», с явным флагом `seasonIdentifiable`. Direct-путь Kodik им уже
пользуется. Yummy-путь его игнорирует не потому, что контракта нет, а потому что до него
не дотянули руки в TICKET-05.

**Мешает.** Индикатор загрузки живёт в `localplayer/ui/PlayerControls.kt` — файле, общем для
локального и стримового плееров. Это не позволяет починить его «только для стрима»: правка
затрагивает оба плеера. Для FR-1 это скорее плюс (локальный плеер получает то же исправление),
но требует проверки, что локальный плеер не сломан.

**Мешает.** `if (!isInPip) { PlayerControlsOverlay(...) }` в `StreamPlayerSurface.kt:233` —
единственный тумблер на весь Compose-слой. Индикатор нельзя оставить в PiP, не вынув его из
`PlayerControlsOverlay`. Это и есть основная структурная правка задачи.

## 2. Нужен ли prefactoring

Нет отдельным тикетом. Единственное необходимое структурное изменение — поднять индикатор
загрузки из `PlayerControls` в `StreamPlayerSurface` как самостоятельный слой `Box`. Это часть
FR-1/FR-2, а не подготовка к ним, и делается тем же тикетом.

## 3. Что обязано остаться стабильным

- `StreamingPlaybackSessionFactory`, `StreamRecoveryPolicy`, телеметрия чанков — задача их
  не касается.
- `EpisodePlaybackStore` и сквозная франшизная семантика `watchedEpisodes`: пользователь
  выбрал править знаменатель, а не числитель.
- `KodikDirectSearch` — его сезонная логика уже верна и покрыта тестами.
- Публичный контракт `SeasonEpisodesStore`: читаем, не меняем.

## 4. Где прятать сложность

- **Видимость загрузки** → за `shouldShowStreamLoading`. Compose получает готовый флаг и решений
  не принимает.
- **Знаменатель прогресса** → за чистой функцией от `SeasonEpisodesEntry`. `HomeScreen`
  не должен уметь суммировать сезоны в теле `remember`.
- **Принадлежность релиза сезону** → за чистой функцией по образцу
  `releaseIdentifiesSelectedSeason`. Сетевой код о правилах сопоставления знать не обязан.

## 5. Какие публичные интерфейсы меняются

- `StreamPlayerSurface` — внутренняя перестановка слоёв, сигнатура не меняется.
- `PlayerControls` — параметр `isBuffering` теряет смысл после выноса индикатора наверх; либо
  удаляется, либо остаётся только локальному плееру. Решается в тикете по факту.
- `KodikSource.yummyCandidates` — private, принимает `SeasonSourceQuery` вместо `Anime`.

## 6. Классификация находок

| Находка | Класс |
|---|---|
| Индикатор вложен в `AnimatedVisibility(controlsVisible)` | REQUIRED_DURING_IMPLEMENTATION |
| Весь Compose-слой выключен в PiP | REQUIRED_DURING_IMPLEMENTATION |
| `KatanaLoader`/`FrozenFrame` — мёртвый код (ноль вызовов у `FrozenFrame`) | REQUIRED_DURING_IMPLEMENTATION |
| Yummy-путь Kodik игнорирует сезон | REQUIRED_DURING_IMPLEMENTATION |
| `coerceAtMost(coerceAtLeast)` в `HomeScreen.kt:1248` — тождество | REQUIRED_DURING_IMPLEMENTATION |
| `BatchEpisodeCheckUseCase` не пишет `anime.episodes` | NOT_RELEVANT_TO_SCOPE (осознанный дизайн) |
| `AniLibria` франшизный индекс (TICKET-09) | FOLLOW_UP (чужой пакет) |
| ~25 разнородных `CircularProgressIndicator` | FOLLOW_UP |
| `SupabaseSync`: `media_type` NOT NULL, RLS на passphrase | FOLLOW_UP (видно в логах, вне объёма) |

## 7. За чем следить в ревью тикетов

- Правка `PlayerControls` не должна изменить поведение локального плеера — файл общий.
- Не структурировать модификаторы над `layerBackdrop` (известная ловушка проекта): индикатор
  добавляется отдельным `Box` в `StreamPlayerSurface`, а не вставкой узла в существующую цепочку
  доков.
- Знаменатель по франшизе не должен молча превращать «расклад не готов» в 0.
- Отказ yummy-пути обязан быть закреплён отрицательным тестом, иначе инвариант «лучше пусто»
  разъедется при следующей правке.
