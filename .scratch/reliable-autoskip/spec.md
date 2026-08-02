# Надёжный autoskip опенингов

## Problem

Autoskip часто не получает таймкоды: AniSkip вызывается повторно при каждом уточнении длительности и отвечает `404`, метаданные jut.su не читаются, а Compose-состояние может продолжать наблюдать старый плеер или старое видео.

## Desired outcome

Опенинг, эндинг и recap надёжно определяются из лучшего доступного источника и одинаково пропускаются в потоковом, локальном и PiP-воспроизведении.

## Current behavior

- jut.su извлекает только HTML `<source>` и принимает изображения-заглушки за видео.
- Поиск jut.su использует один alias и сохраняет слабое совпадение ниже `0.91`.
- `PreferSourceTimestampsSkipProvider` содержит глобальное mutable-состояние, которое фактически не заполняется.
- Стриминговый плеер отдельно предпочитает `video.timestamps`; локальный всегда идёт в AniSkip.
- AniSkip запрашивается с текущей длительностью при каждом её изменении.
- Два UI самостоятельно вычисляют активный сегмент через `derivedStateOf`, не полностью привязанный к media identity.

## Required behavior

- Чисто разобрать Base64-конфигурацию jut.su, playable sources, длительность и skip-разметку.
- Последовательно искать jut.su по русскому, основному и английскому названиям и принимать только совпадения `>= 0.91`.
- Переносить reference-разметку jut.su на все варианты текущей серии.
- Разрешать сегменты единым resolver с приоритетом: exact video, совместимый jut.su reference, AniSkip.
- Получать top-voted варианты AniSkip одним запросом `episodeLength=0`, кэшировать валидный `200` на время процесса и локально применять только совместимые записи.
- Координировать кнопку и automatic seek одним media-keyed механизмом для потокового и локального плееров.

## User-visible behavior

- Autoskip остаётся выключенным по умолчанию.
- Когда autoskip выключен, внутри сегмента показывается рабочая кнопка Skip.
- Когда autoskip включён, вход в сегмент вызывает seek независимо от контролов и PiP.
- Смена серии, URL/озвучки или экземпляра плеера не наследует активный/уже пропущенный сегмент старого media.
- Resume внутри сегмента повторно оценивается и при включённом autoskip перематывается.

## Domain rules

- `VetroSkipReference` содержит `segments`, `referenceDurationMs`, `origin` и сериализуется в `VetroHoster` и `VetroVideo`.
- Exact timestamps текущего видео всегда имеют высший приоритет.
- Reference совместим, только если абсолютная разница длительностей одновременно не больше `1%` текущей длительности и `15_000 ms`.
- Времена не масштабируются; конец сегмента ограничивается фактической длительностью.
- Некорректные интервалы отбрасываются.
- Для intro start без end: `end = min(duration, start + 89s)`; при неизвестной duration используется `start + 89s`.
- Для intro end без start: `start = max(0, end - 89s)`.
- Ending без start не угадывается; `video_outro_start` образует ending до известного конца видео.
- Начало опенинга не угадывается, если нет ни start, ни end.
- `pixel.png`, изображения и явные placeholder URL не являются playable video.

## Functional requirements

1. Парсер не выполняет сеть и возвращает parsed sources, duration и timestamps.
2. Повреждённый Base64 не ломает разбор остальных HTML-данных.
3. jut.su может вернуть reference-only hoster; SourceEngine использует его для обогащения playable hosters и не отдаёт его как видео.
4. Reference распространяется без масштабирования и проверяется resolver только после появления фактической длительности.
5. AniSkip cache key — сопоставленная пара `(MAL ID, episode)`; изменение duration не вызывает второй HTTP-запрос.
6. Только валидный HTTP `200` кэшируется; `404`, `429`, `5xx`, malformed response и transport/network exception не кэшируются.
7. Общий координатор сбрасывается по `(player instance, episode/media id, URL)` и защищает от повторного automatic seek одного сегмента.
8. Один диагностический seek-log на серию содержит origin, reference/current duration, segments и применённую seek-цель.

## Non-functional requirements

- Детерминированная логика парсинга, совместимости, выбора и state transitions покрыта JVM unit-тестами.
- Новая логика не зависит от видимости Compose-контролов и PiP.
- Существующие незакоммиченные изменения плеера и SourceEngine сохраняются.

## Compatibility and migration constraints

- Новые serializable-поля имеют defaults, поэтому старые intent/cache payload продолжают декодироваться.
- Existing exact timestamps AniLiberty продолжают работать.
- Нет персистентной миграции и нет пропорционального масштабирования.

## Failure and fallback behavior

- Ошибка jut.su даёт пустой результат и не блокирует другие hosters.
- Reference с неизвестной/несовместимой duration не применяется.
- Ошибка сети AniSkip возвращает пустой результат для вызова, но допускает повтор позже.
- Малформатный успешный AniSkip response не считается кэшируемым результатом.

## Out of scope

- Включение autoskip по умолчанию.
- Детект опенинга по аудио/видео.
- Пропорциональное масштабирование таймкодов.
- Изменение правил прогресса просмотра.

## Acceptance criteria

- Death Note E1 fixture даёт intro `0..85_000`, ending `1_283_000..1_377_000`, duration `1_377_000`.
- Start-only и end-only intro восстанавливаются на `89_000 ms` по заданным правилам.
- Поиск проходит aliases в порядке RU/main/EN и отбрасывает score ниже `0.91`.
- Resolver проходит exact/reference/AniSkip priority и duration compatibility.
- AniSkip выполняет один `episodeLength=0` запрос для пары после валидного `200` и не повторяет его при новой duration.
- Переключение media и resume regression покрыты unit-тестом координатора.
- `.\gradlew.bat :app:testDebugUnitTest` и `.\gradlew.bat :app:assembleDebug` проходят.

## Open questions

Нет blocking-вопросов. Правило end-only intro является утверждённым отклонением от исходного плана пользователя.

Официальная реализация AniSkip не выполняет заявленную планом выдачу всех duration-вариантов:
`episodeLength=0` снимает duration filter, но service оставляет только top-voted запись каждого
`skipType`. Поэтому локально выбрать ближайший из всех вариантов невозможно. Подтверждение и
ссылки: `.scratch/reliable-autoskip/research-aniskip.md`.

## Test seams

- `JutSuEpisodePageParser.parse(html, pageUrl)`.
- Чистые alias/search-candidate функции jut.su.
- `propagateSkipReference` и `SkipSegmentResolver.resolve(request)`.
- Injected AniSkip transport.
- `MediaSkipCoordinator` transitions and seek decisions.
