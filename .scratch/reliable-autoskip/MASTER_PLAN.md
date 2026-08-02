# Надёжный autoskip — Master Plan

## Workflow

Current workflow state: COMPLETED
Current ticket: None
Last completed ticket: TICKET-03
Next eligible ticket: None
Last updated: 2026-07-31

## Goal

Реализовать надёжный autoskip по `.scratch/reliable-autoskip/spec.md`, включая утверждённое правило `start = end - 89s` для end-only intro.

## Canonical specification

`.scratch/reliable-autoskip/spec.md`

## Architecture review

`.scratch/reliable-autoskip/architecture/INITIAL_REVIEW.md`

## Global constraints

- Сохранить все существующие незакоммиченные пользовательские изменения.
- Autoskip default-off.
- Не масштабировать timestamps.
- Не заявлять manual/device verification без устройства.

## Non-goals

Audio/video detection, default-on, unrelated player refactors.

## Verification commands

### Fast checks

`.\gradlew.bat :app:testDebugUnitTest --tests "<target>"`

### Ticket checks

Новые targeted JVM tests плюс compilation затронутых source sets.

### Full checks

`.\gradlew.bat :app:testDebugUnitTest`

`.\gradlew.bat :app:assembleDebug`

## Ticket overview

| ID | Title | Status | Blocked by | Commit | Review |
|---|---|---|---|---|---|
| TICKET-01 | Надёжная metadata-разметка jut.su | DONE | — | — | passed after fixes |
| TICKET-02 | Episode-wide reference и единый resolver | DONE_WITH_DEVIATIONS | TICKET-01 | — | passed after fixes |
| TICKET-03 | Общий media-keyed playback coordinator | DONE | TICKET-02 | — | passed after fixes |

## Decisions

- End-only intro восстанавливает start как `max(0, end - 89s)`.
- Только валидный AniSkip `200` cache-ится; non-200/transport exception — нет.
- Reference-only jut.su result допускается внутри SourceEngine до enrichment, но не выходит как playable hoster.

## Global deviations

- Исходный пункт «игнорировать end без start» заменён по прямому указанию пользователя.
- План предполагал, что AniSkip `episodeLength=0` возвращает все duration-варианты. Официальный
  backend возвращает только top-voted запись каждого skipType, поэтому реализация может лишь
  локально проверить совместимость этих записей. Причина и последствия:
  `.scratch/reliable-autoskip/research-aniskip.md`.
- Full suite был заблокирован stale stats test после перехода рейтинга 0…5 → 0…10. Test/KDoc
  синхронизированы с уже существующим runtime contract; production behavior не менялся.

## Known risks

- Рабочее дерево уже содержит overlapping изменения SourceEngine и player UI.
- Реальный PiP и реальный Death Note seek требуют устройства/доступного video; pure parsing fixture и coordinator покрываются локально.

## Deferred work

- Optional device/instrumentation matrix for PiP, rendition switching and resume.
- Optional franchise-mapping cache and SourceEngine reference-only integration test.

## Final acceptance checklist

- [x] Every required ticket completed
- [x] Full test suite or agreed equivalent run
- [x] Specification reviewed requirement by requirement
- [x] No unresolved blocking review findings
- [x] Compatibility behavior verified
- [x] User-visible behavior verified to available local-test extent
- [x] Deferred work explicitly recorded
- [x] Final architecture checkpoint completed
