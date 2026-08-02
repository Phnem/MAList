# TICKET-02 review

Result: PASS WITH DOCUMENTED DEVIATION.

- Strict malformed-response handling, retry semantics and duration compatibility verified.
- Jut.su reference selection is origin-specific and independent of hoster ordering.
- Invalid negative starts are rejected.
- RU/EN SourceEngine paths propagate reference metadata before removing reference-only hosters.
- No unresolved blocking/high/medium finding.

Deviation: official AniSkip currently returns only the top-voted record per skip type for
`episodeLength=0`; the unavailable duration alternatives cannot be selected locally.
