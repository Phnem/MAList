# TICKET-08: Stremio addon import

## Status

DONE

## Dependencies

TICKET-03, TICKET-07.

## Acceptance criteria

- [x] Манифест и потоки транслируются с фикстур.
- [x] Каждая отклоняемая форма покрыта тестом.
- [x] `behaviorHints.p2p` отклоняется при импорте.
- [x] `proxyHeaders` проходят через allowlist.
- [x] id эпизода в форме `tt…:{season}:{episode}`.
- [x] Download остаётся запрещённым.

## Implementation notes

Отдельный адаптер, а не трансляция в Vetro-манифест: id эпизода `tt0412142:9:17` — один сегмент пути
с двоеточиями, который percent-кодируемые подстановки выразить не могут.

Принимается только прямой http(s) `url`. `infoHash`, `nzbUrl`, архивы, `ytId`, `externalUrl`
отбрасываются: в Vetro нет torrent/usenet/архивного стека, а `externalUrl` — ссылка в браузер, не
поток. Поток с отклоняемой формой рядом с пригодным `url` всё равно играет.

`proxyHeaders` фильтруются до User-Agent/Referer/Origin/Accept: возможность выставить `Authorization`
или `Cookie` превратила бы любой установленный аддон в примитив пересылки учётных данных.

Протокол сверен с документацией SDK (manifest, stream, transport URL).

## Deviations

Нет.

## Review findings

Блокирующих находок нет.

## Completion evidence

- app 543+ tests, 0 failures; debug APK собирается.
- `StremioImporterTest` — 8 тестов; `StremioAddonProviderTest` — 14.
- Commit: `49a5834`.
