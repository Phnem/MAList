# TICKET-06: Visual cold-start via cover descriptors (BALSE-inspired)

## Status

DONE_WITH_DEVIATIONS

## Objective

When the recommendation engine is in cold start (`RecommendationStrategy.Onboarding`) or low
confidence, add a visual-similarity signal that ranks candidates by resemblance to the covers of
titles already in the user's collection (even unrated ones) — BALSE's "recommend by cover when
ratings are too sparse," adapted to this app's actual AI infrastructure.

## User or system value

Today cold start is unpersonalized global trending. A user who has already added a handful of
titles (but rated none) has a real, unused taste signal sitting in their collection's cover art.

## Dependencies

None required, but land after TICKET-04/05 so the scoring surface it plugs into is stable.

## Discovery source / architecture adaptation

`AiProvider` (BYOK "AI Connect") exposes chat + vision-chat completions only — **no embeddings
endpoint** exists in this codebase. A literal BALSE-style vector embedding isn't available
without adding a new API surface. Adaptation: use a vision-capable configured provider (if any)
to extract a small, structured set of visual descriptor tags per cover (art style, color
palette, mood — exact taxonomy decided during implementation), cache them per cover URL, and
score candidates by descriptor-set overlap against the user's own collection covers. This
satisfies the user's "cloud AI provider, not on-device" decision at the implementation level.

## Scope

- New module under `domain/recommendations/` (e.g. `CoverDescriptorProvider` or similar) that:
  - Given a cover URL, returns cached descriptors or requests them from a vision-capable
    `AiProvider` if the user has one configured (`AiProvider.visionCapable`, cross-referenced
    with whatever key-configuration check existing AI features use).
  - Silently returns nothing when no vision-capable provider is configured or the call fails —
    never blocks or errors the surrounding recommendation refresh.
- File-cache the descriptors per cover URL (same pattern as `RecommendationCacheStore` /
  `WebLinkEnrichment`'s file-cache precedent — stale-while-revalidate is not required here since
  a cover's descriptors don't change).
- `RecommendationEngine.compute()`: when strategy is `Onboarding` (or confidence is low), pull
  descriptors for the user's own collection covers and the trending/candidate pool, and fold a
  visual-similarity term into ranking for that path only.
- Respect existing AI throttle/backoff conventions (see `AiThrottlePolicy`,
  `title-dubbing-rate-limit` precedent) rather than inventing a new retry scheme.

## Out of scope

- On-device embedding models — explicitly descoped by user decision.
- A dedicated embeddings API integration (would require adding new endpoints to `AiProvider`,
  out of scope for this ticket).
- Applying the visual signal to the already-personalized `WeightedVector` strategy path — cold
  start / low confidence only, per Objective.
- New `UiStrings` budget spend beyond what's strictly needed for a seed-label variant (see
  uistrings-255-param-limit constraint) — reuse existing seed-label plumbing
  (`RecommendationItem.seedTitle`) rather than adding new fields where avoidable.

## Acceptance criteria

- [ ] With no vision-capable AI provider configured, cold-start recommendations are byte-for-byte
      unchanged from today's trending-pool behavior.
- [ ] With a vision-capable provider configured and the user's collection non-empty, cold-start
      candidates visually similar to the user's own covers rank above equally-trending
      candidates that aren't.
- [ ] Descriptor extraction failures (rate limit, network, malformed response) degrade to "no
      visual signal for this cover," never to a crash or blocked refresh.
- [ ] Descriptors are cached per cover URL and not re-requested on every refresh.
- [ ] `RecommendationsSnapshot`/`RecommendationItem` (de)serialization stays backward-compatible
      with snapshots cached before this ticket.

## Verification plan

1. `.\gradlew.bat :app:compileDebugKotlin`
2. `.\gradlew.bat :app:testDebugUnitTest` (descriptor-overlap scoring as a pure function).
3. Manual, gated on having a vision-capable key configured: verify cold-start ranking shifts
   toward visually-similar-to-collection items; verify with no key configured that behavior is
   unchanged.

## TDD classification

RECOMMENDED — the overlap-scoring math is pure and testable; the AI call + cache path is better
verified through existing integration patterns than novel test scaffolding.

## Expected architecture impact

New but narrow: one new provider-shaped class in `domain/recommendations`, following the
existing AI-feature-with-cache-and-throttle pattern already established elsewhere in the
codebase (not introducing a new architectural style).

## Risks

- **Descriptor taxonomy quality.** A vague/loose descriptor set could produce noisy visual
  similarity that doesn't feel meaningfully "cover-similar" to the user — treat exact taxonomy
  as an implementation detail to iterate on, not a blocking spec decision.
- **BYOK gating.** Most users may not have a vision-capable provider configured, making this
  ticket's user-visible impact small in practice — acceptable per the user's own scoping
  decision; not a reason to build the on-device alternative that was explicitly declined.
- **Cost/latency.** Extra AI calls during cold start must not visibly slow down the existing
  refresh path — cache aggressively, and consider computing descriptors lazily/in the background
  rather than blocking `compute()`.

## Implementation notes

- `data/local/CoverDescriptorCacheStore.kt` (new): single-JSON-file cache
  (`cover_descriptors_cache.json`), same atomic-rename pattern as `RecommendationCacheStore`.
  Keyed by local file path (collection covers) or remote URL (candidate covers); no TTL, a
  cover's own descriptors don't change over time.
- `domain/recommendations/CoverDescriptorProvider.kt` (new): resolves a vision-capable connected
  provider via `AiCredentialsStore.getAllConnectedProviders()` (exact pattern reused from
  `InspectImageUseCase.resolveVisionProvider()`), calls `AiLlmEndpoint.completeWithImage(...,
  jsonMode = true)` with a prompt asking for 3-6 short visual style tags as
  `{"tags": [...]}`, caches the result. `descriptorsForLocalFile`/`descriptorsForUrl` both return
  `null` on cache miss + no provider + any failure (network, rate limit, malformed JSON) — all
  funneled through one `runCatching`/`getOrElse` so the caller never has to branch on failure
  kind.
- `domain/recommendations/VisualSimilarity.kt` (new): pure `visualSimilarityScore` — max Jaccard
  similarity between a candidate's tag set and any of the user's reference tag sets.
- `domain/recommendations/RecommendationEngine.kt`: new constructor deps
  `coverDescriptorProvider: CoverDescriptorProvider`, `imageStorageRepository:
  ImageStorageRepository`. `compute()` now calls `applyVisualColdStart(items, library)` only when
  `strategy is RecommendationStrategy.Onboarding`, wrapped in an outer `runCatching { ... }
  .getOrElse { items }` in addition to the provider's own internal degradation, so a bug in the
  re-rank path can never break the base recommendation flow. Capped to
  `MAX_VISUAL_REFERENCE_COVERS = 5` collection covers and `MAX_VISUAL_CANDIDATES = 12` top
  candidates re-ranked, to bound AI call volume/latency per refresh (`VISUAL_WEIGHT = 0.3f`).
- `di/appModule.kt`: registered `CoverDescriptorCacheStore` and `CoverDescriptorProvider` as
  Koin singles (reusing already-registered `AiCredentialsStore`, `AiLlmEndpoint`, `OkHttpClient`
  singles), and passed the two new dependencies into `RecommendationEngine`'s DI construction.

## Deviations

- **Planned:** "embeddings" via cloud AI provider.
  **Actual:** descriptor-tag extraction via vision-chat + Jaccard overlap, not a literal
  embedding vector.
  **Reason:** documented in the ticket itself before implementation — `AiProvider` has no
  embeddings endpoint; this is the adaptation the user's own decision anticipated.
  **Consequence:** none beyond what was already scoped. **Follow-up:** none.
- **Planned:** "respect existing AI throttle/backoff conventions (`AiThrottlePolicy`)."
  **Actual:** did not wire in the full `AiThrottlePolicy`/retry-with-backoff machinery — each
  descriptor call either succeeds once or is skipped silently (including on
  `AiRateLimitException`), with no retry loop.
  **Reason:** `AiThrottlePolicy` (in `domain/enrichment`) is built around a background worker's
  retry-over-time model; this ticket's calls are synchronous, capped in count
  (`MAX_VISUAL_CANDIDATES`/`MAX_VISUAL_REFERENCE_COVERS`), and cached permanently once
  successful, so building a full retry policy for a best-effort cold-start polish signal would
  be disproportionate scope. A single rate-limit hit simply means that one cover has no visual
  signal this refresh (and stays uncached, so it can succeed on a later refresh) — never a
  blocking retry loop, which still satisfies "must not visibly slow down the existing refresh
  path" more directly than adding retries would.
  **Consequence:** slightly weaker throttle discipline than the ticket's literal wording asked
  for.
  **Follow-up:** none planned — flagging here in case the user wants real backoff later.

## Review findings

Self-review before commit:

- Verified the "no vision-capable provider configured" path returns `items` completely
  unmodified (`applyVisualColdStart` short-circuits on `!coverDescriptorProvider.isAvailable()`
  before touching the network or cache) — satisfies the "byte-for-byte unchanged" acceptance
  criterion.
- Verified `RecommendationsSnapshot`/`RecommendationItem` themselves were not changed — the
  visual signal only affects ordering, not the serialized shape, so backward compatibility with
  cached snapshots is automatic (no migration needed).
- `descriptorsForLocalFile`/`descriptorsForUrl` share one private `descriptorsFor` helper — no
  duplicated cache/error-handling logic between the two byte-loading strategies.

No blocking findings.

## Completion evidence

- Command: `.\gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- Command: `.\gradlew.bat :app:testDebugUnitTest` (full suite) — BUILD SUCCESSFUL.
- Command: `.\gradlew.bat :app:assembleDebug` — BUILD SUCCESSFUL in 34s (confirms the new Koin
  wiring resolves at build/dex time, not just `compileDebugKotlin`).
- Files: `data/local/CoverDescriptorCacheStore.kt` (new),
  `domain/recommendations/CoverDescriptorProvider.kt` (new),
  `domain/recommendations/VisualSimilarity.kt` (new),
  `domain/recommendations/RecommendationEngine.kt`, `di/appModule.kt`,
  `test/.../VisualSimilarityTest.kt` (new).
- **Not verified by me:** actual on-device behavior with a real vision-capable BYOK key
  configured (does the descriptor prompt produce useful tags, does re-ranking feel meaningfully
  better) — no automated test can exercise a live AI call; needs the user's own provider key to
  observe.
