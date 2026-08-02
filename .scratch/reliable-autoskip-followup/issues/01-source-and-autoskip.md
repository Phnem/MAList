# TICKET-01: Restore validated sources and usable opening references

## Status

COMPLETED

## Objective

Make jut.su metadata discoverable without JavaScript, apply segment-kind-aware reference
compatibility, and safely recover AniLibria for later seasons.

## User or system value

Food Wars S3E6 receives a valid opening segment and real alternate sources appear when available.

## Dependencies

None.

## Scope

jut.su title discovery, AniLibria season search/validation, resolver compatibility and JVM tests.

## Out of scope

Player lifecycle and buffering.

## Acceptance criteria

- [x] Food Wars S3E6 jut.su opening is accepted for 1470.123s without scaling.
- [x] Its ending does not receive the relaxed opening policy.
- [x] JS-only `/lookfor` output has a tested fallback.
- [x] AniLibria later-season results are validated and cannot become season one.

## Verification plan

Targeted source/resolver unit tests, then app unit-test compilation.

## TDD classification

REQUIRED

## Expected architecture impact

Source-specific complexity remains local; resolver gains kind-aware compatibility policy.

## Risks

Third-party search behavior can change; fail closed on ambiguous titles/seasons.

## Implementation notes

- Punctuation-normalized jut.su queries follow authoritative aliases; static candidates and direct
  redirects share the strict `0.91` selector.
- AniLibria selection validates season identity and fails closed on incomplete or ambiguous data.
- Only jut.su opening references use the wider `2%` and `30s` compatibility ceiling.

## Deviations

## Review findings

Standards and specification re-reviews: PASS.

## Completion evidence

Targeted jut.su, AniLibria, source propagation, and skip resolver JVM tests: BUILD SUCCESSFUL.
