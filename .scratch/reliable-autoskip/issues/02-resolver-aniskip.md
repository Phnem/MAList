# TICKET-02: Episode-wide reference и единый resolver

Status: DONE_WITH_DEVIATIONS

## Objective

Распространить reference по SourceEngine и разрешать exact/reference/AniSkip одним module.

## User or system value

Таймкоды jut.su работают на другой озвучке, а AniSkip больше не спамит запросами при уточнении duration.

## Dependencies

TICKET-01.

## Scope

SourceEngine propagation, compatibility/clipping, `SkipSegmentResolver`, AniSkip `episodeLength=0` cache и local selection.

## Out of scope

Player state transitions.

## Acceptance criteria

- [x] Exact имеет приоритет над reference и AniSkip.
- [x] Reference проверяет оба duration-порога, не масштабируется и clip-ится.
- [x] Reference-only hoster обогащает playable variants и не становится video.
- [x] AniSkip делает один запрос на mapped episode после валидного `200`, локально проверяет совместимость top-voted записей и не кэширует non-200/network exception.
- [x] Mutable `PreferSourceTimestampsSkipProvider` удалён.

## Verification plan

Resolver, propagation и injected transport tests.

## TDD classification

REQUIRED

## Expected architecture impact

Замена shallow provider cluster единым resolver interface.

## Risks

Несколько AniSkip skip types с близкими, но не идентичными episodeLength. API не возвращает все
duration-варианты; см. `research-aniskip.md`.

## Implementation notes

Reference propagation выполняется до удаления reference-only hoster. Resolver возвращает segments,
origin и reference duration. AniSkip использует per-key in-flight coalescing и strict response parse.

## Deviations

Официальный endpoint не отдаёт все duration variants; реализована локальная compatibility-проверка
top-voted записи каждого skip type. EN path делает отдельный bounded jut.su reference resolve.

## Review findings

Исправлены: malformed-200 negative caching, order-dependent reference selection, clamping negative
starts, global mutex around HTTP и отсутствие EN enrichment. Legacy `SkipSegmentProvider` bridge
оставлен только до TICKET-03, чтобы промежуточный production source set компилировался.

## Completion evidence

Targeted `SkipSegmentResolverTest`, `AniSkipSegmentProviderTest`,
`SkipReferencePropagationTest` — BUILD SUCCESSFUL.
