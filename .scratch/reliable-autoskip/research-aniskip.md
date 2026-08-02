# AniSkip v2: контракт `GET /skip-times/{malId}/{episodeNumber}`

Дата проверки: 2026-07-31.

Источники: живая официальная [OpenAPI-схема AniSkip](https://api.aniskip.com/api-docs-json) версии `2.0.576`, [Swagger UI](https://api.aniskip.com/api-docs), официальный репозиторий [`aniskip/aniskip-api`](https://github.com/aniskip/aniskip-api) на текущем `main` (`02bdc469c5acdd4c517244a22c49e9a0b333c6fa`) и официальный клиент [`aniskip/aniskip-extension`](https://github.com/aniskip/aniskip-extension).

## Критический вывод

`episodeLength=0` **не даёт клиенту все варианты длительности для каждого типа сегмента**.

Документация параметра говорит: “If the input is 0, it will return all episodes” ([request DTO, строки 19–29](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/models/v2/get-skip-times/get-skip-times.request-query.v2.ts#L19-L29)). Реальная реализация точнее:

1. Ноль отключает только SQL-фильтр совместимости длительности: условие имеет вид `episodeLength = 0 OR ABS(storedLength - episodeLength) <= 20`. Фильтр по конкретному `anime_id`, `episode_number` и `skip_type` остаётся; кандидаты сортируются по голосам, не по близости длительности ([repository, строки 95–121](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/repositories/skip-times.repository.ts#L95-L121)).
2. Репозиторий может найти до десяти кандидатов одного типа, но сервис безусловно возвращает только `skipTimes[0]` для каждого запрошенного типа ([service, строки 65–99](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/skip-times.service.v2.ts#L65-L99)). Это поведение также закреплено [unit-тестом сервиса](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/__tests__/skip-times.service.v2.unit.test.ts#L93-L128).

Следовательно, точная семантика нуля в текущей v2-реализации: «не ограничивать внутренний набор кандидатов длительностью, после чего вернуть самый голосованный кандидат каждого запрошенного `skipType`». Это не означает «все номера серий» и не означает «все duration-варианты в JSON».

Из этого следует важное ограничение для проекта: схема «один запрос с `episodeLength=0`, затем выбрать ближайшую совместимую длительность локально» сейчас невыполнима через этот endpoint. В ответе нет остальных кандидатов одного типа, из которых можно было бы выбирать.

## Запрос

```http
GET https://api.aniskip.com/v2/skip-times/{animeId}/{episodeNumber}
    ?types=op
    &types=ed
    &types=mixed-op
    &types=mixed-ed
    &types=recap
    &episodeLength=0
```

- `animeId`: MAL ID, целое число `>= 1`.
- `episodeNumber`: число `>= 0`; схема допускает дробные серии.
- `types`: обязательный непустой в практическом запросе массив уникальных значений. Допустимы `op`, `ed`, `mixed-op`, `mixed-ed`, `recap`; одиночное значение сервер преобразует в массив ([request DTO, строки 7–17](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/models/v2/get-skip-times/get-skip-times.request-query.v2.ts#L7-L17), [официальные типы, строки 15–32](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/skip-times.types.ts#L15-L32)).
- `episodeLength`: обязательное число `>= 0`. При ненулевом значении сервер допускает кандидатов с абсолютной разницей не более 20 секунд, но выбирает среди них по голосам, а не по минимальной разнице ([repository, строки 111–121](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/repositories/skip-times.repository.ts#L111-L121)).

Для массива `types` официальный endpoint принимает повторяющиеся query-параметры, как в примере выше.

## Успешный ответ и duration-варианты

Успех — HTTP `200` и плоский объект:

```json
{
  "found": true,
  "results": [
    {
      "interval": {
        "startTime": 1.039,
        "endTime": 91.039
      },
      "skipType": "op",
      "skipId": "00a20a12-3ce3-43c2-a5ff-bbec339021c7",
      "episodeLength": 1377.312
    },
    {
      "interval": {
        "startTime": 1286.051,
        "endTime": 1356.451
      },
      "skipType": "ed",
      "skipId": "854fe7d6-4a7f-4e78-99c0-0e2bb5554d5e",
      "episodeLength": 1377.08
    }
  ],
  "message": "Successfully found skip times",
  "statusCode": 200
}
```

Это реальный ответ для [Death Note, MAL 1535, episode 1, `op+ed`, `episodeLength=0`](https://api.aniskip.com/v2/skip-times/1535/1?types=op&types=ed&episodeLength=0) на дату проверки.

Форма закреплена в [response DTO](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/models/v2/get-skip-times/get-skip-times.response.v2.ts#L4-L50):

- `found`: найдена ли хотя бы одна запись;
- `results`: плоский массив сегментов;
- `message`: диагностическое сообщение;
- `statusCode`: число, дублирующее смысл HTTP-статуса.

Один элемент `results` содержит:

- `interval.startTime` и `interval.endTime`: начало и конец seek-интервала;
- `skipType`: один из пяти перечисленных типов;
- `skipId`: UUID записи, используемый также для голосования;
- `episodeLength`: длительность того видео, относительно которого была создана именно эта запись.

Временные значения — секунды. Официальный клиент использует их вместе с длительностью HTML-видео, рассчитывает `offset = currentDuration - episodeLength`, а `endTime` передаёт в seek; его `setCurrentTime` прямо документирован как принимающий секунды ([official extension, строки 533–552](https://github.com/aniskip/aniskip-extension/blob/0a7e8f64dbd32ea69ffcc31b68b0563116a3b77d/src/players/base-player.ts#L533-L552)).

Разные элементы ответа могут иметь разные `episodeLength`, как `op` и `ed` в примере. Это не набор альтернатив одной разметки: публичный сервис возвращает не более одной записи на каждый уникальный запрошенный `skipType`, то есть максимум пять элементов. Внутри одного типа ближайший duration-вариант локально выбрать нельзя.

Нюанс строгой десериализации: OpenAPI помечает обязательными четыре верхнеуровневых поля, но во вложенной схеме элемента `results` нет собственного массива `required`. Серверный TypeScript-тип и фактическое преобразование из БД всегда формируют перечисленные поля ([mapping, строки 124–132](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/repositories/skip-times.repository.ts#L124-L132)).

## `404` и прочие сбои

Если после фильтрации нет ни одной записи, endpoint отвечает именно HTTP `404`, а не HTTP `200` с `found=false`:

```json
{
  "found": false,
  "results": [],
  "message": "No skip times found",
  "statusCode": 404
}
```

Контроллер формирует этот объект и бросает `HttpException` со статусом `404` ([controller, строки 89–112](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/skip-times.controller.v2.ts#L89-L112)). Поведение подтверждается живым запросом к [несуществующей записи](https://api.aniskip.com/v2/skip-times/999999999/1?types=op&types=ed&episodeLength=0).

При этом текущая OpenAPI-схема описывает для GET только ответ `200`; `404` в разделе `responses` не задокументирован. Клиенту поэтому нельзя полагаться только на сгенерированную happy-path модель: нужно обрабатывать error body либо хотя бы статус отдельно.

Дополнительно официальный контроллер ограничивает GET до 120 запросов в минуту ([controller, строки 77–80](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/skip-times.controller.v2.ts#L77-L80)), поэтому возможен `429`.

Сетевые ошибки — DNS, TLS, timeout, обрыв соединения — не являются ответом AniSkip и не имеют гарантированной JSON-формы или HTTP-статуса. Их нужно отличать от валидного `404`. Официальный клиент AniSkip представляет ошибку без HTTP response как `status: 0, ok: false` ([base HTTP client, строки 36–58](https://github.com/aniskip/aniskip-extension/blob/0a7e8f64dbd32ea69ffcc31b68b0563116a3b77d/src/api/base-http-client/base-http-client.ts#L36-L58)). Аналогично `429` и `5xx` являются временными ошибками, а не доказательством отсутствия таймкодов.

## Что кэшировать

Безопасная практическая политика:

- Кэшировать в памяти процесса только успешно декодированный HTTP `200` с `found=true`.
- Если всегда запрашивается фиксированный полный набор типов и `episodeLength=0`, достаточно ключа `(MAL ID, episodeNumber)`. Если набор `types` меняется, он тоже должен входить в ключ.
- Не кэшировать сетевые исключения, timeout, `429` и `5xx`.
- Не кэшировать `404` бессрочно. AniSkip допускает создание новых записей и изменение их рейтинга, поэтому отсутствие и выбранный «первый» кандидат могут измениться ([создание записи, controller строки 115–154](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/skip-times/skip-times.controller.v2.ts#L115-L154), [голосование меняет votes, repository строки 15–54](https://github.com/aniskip/aniskip-api/blob/02bdc469c5acdd4c517244a22c49e9a0b333c6fa/src/repositories/skip-times.repository.ts#L15-L54)). Если отрицательный кэш всё же нужен против частых повторов, он должен иметь короткий TTL.

Даже успешный ответ не является неизменяемым справочником: сервер выбирает запись по текущим голосам. Кэш на время одного процесса — разумный компромисс для стабильного воспроизведения и снижения нагрузки, но это клиентская политика свежести, а не гарантия immutable-контракта AniSkip.

## Последствие для плана autoskip

Тест «один запрос с `episodeLength=0`, выбор ближайшего варианта локально» нельзя честно реализовать на основе текущего официального ответа v2: альтернативы одного `skipType` отбрасываются на сервере. Реалистичные варианты требуют изменения дизайна:

1. передавать известную текущую длительность в AniSkip и принять серверный фильтр `±20 секунд` с выбором по голосам; либо
2. использовать `episodeLength=0` как единственный best-voted fallback, но затем самостоятельно проверять совместимость полученной `episodeLength` и при несовместимости отвергать результат — без возможности выбрать вторую по близости альтернативу.

Второй вариант сохраняет «один запрос на серию», но не обеспечивает заявленный выбор ближайшего duration-варианта.
