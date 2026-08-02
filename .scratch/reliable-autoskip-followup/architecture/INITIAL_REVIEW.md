# Initial architecture review

## REQUIRED_BEFORE_IMPLEMENTATION

- Keep source-specific search behavior inside its source module; SourceEngine should only
  orchestrate validated results.
- Put enrichment pause counting behind one small interface. Activities only acquire a token and
  close it; they do not know WorkManager names or policies.

## REQUIRED_DURING_IMPLEMENTATION

- Keep cross-source compatibility as deterministic resolver policy and preserve exact-source
  priority.
- Make the worker consult the same pause state used by scheduling, preventing cancel/reschedule
  races.
- Keep playback telemetry privacy-safe at its formatting seam: pass a host, never an arbitrary URL.
- Centralize Supabase throwable summarization so individual repositories cannot accidentally print
  original exceptions.

## FOLLOW_UP

- CDN scoring or automatic host health ranking can use the new diagnostics after real samples
  exist.
- A persistent, shared title-slug index would reduce third-party search round trips but is not
  required for this repair.

## NOT_RELEVANT_TO_SCOPE

- Player UI redesign, download scheduling, and general collection-enrichment architecture.

## Module depth decision

`CollectionEnrichmentCoordinator.acquireInteractiveMediaPause()` is the lifecycle seam. Its token
hides reference counting, WorkManager cancellation, scheduling suppression, and last-release
resumption. Deleting that module would force all three Activities and the worker to duplicate those
rules, so the module earns its interface.
