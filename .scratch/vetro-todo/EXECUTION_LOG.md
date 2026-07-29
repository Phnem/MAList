# Execution log — Vetro Collection todo (11 items)

## Initial codebase discovery

Date: 2026-07-29. Workflow: `/ticket-autopilot`. Source task: user-supplied `vetro-todo.md` (11 items), copied to `spec-source.md`.

### Relevant modules

All work lands in `:app` (the `:core:designsystem` module is an empty shell — build file only; the design system actually lives at `app/.../ui/shared/theme/`). `:core:network` is touched only if search result mapping changes.

| Todo item | Primary code |
|---|---|
| 1 — reader title under camera cutout | `manga/ui/MangaReaderScreen.kt:774`, `manga/ui/MangaReaderActivity.kt:85` |
| 2 — manga progress bar missing | `manga/ui/MangaReaderScreen.kt:828` (`ReaderPageSlider`), `manga/ui/ReaderDock.kt:267`, `manga/data/MangaReadingStore.kt` |
| 3 — keiyoushi extension source | `manga/source/`, `core/network/.../mangadex/` (untracked) |
| 4 — episodes-menu dropdown black → `#333333` | `ui/details/ModernDetailsEpisodesPage.kt` — **no `DropdownMenu` anywhere in the app**; the widget is custom, exact location still unidentified |
| 5 — category / sort menu colours washed out | `ui/home/HomeComponents.kt`, `ui/home/HomeScreen.kt` |
| 6 — favourites border + chip | `ui/home/AnimeCard.kt:194,200`, `ui/shared/theme/OverlayThemeTokens.kt:136` |
| 7 — Details duplicate chips | `ui/details/DetailsScreen.kt`, `ui/details/DetailsFactCards.kt` (**untracked, new**) |
| 8 — search shows `0` episodes | `ui/home/ApiSearchResultCard.kt`, `domain/search/AddFromApiUseCase.kt:67` |
| 9 — add button reverts, duplicates | `domain/search/AddFromApiUseCase.kt`, `ui/home/HomeViewModel.kt` |
| 10 — volumes expanded, no animation | `manga/ui/MangaChaptersPage.kt` |
| 11 — README pointer | `README.md` |

### Existing behaviour (root causes already established)

- **Item 1.** `MangaReaderActivity.hideSystemBars()` hides system bars, so `Modifier.statusBarsPadding()` on the reader header resolves to **0** — the display-cutout inset is not part of `statusBars` once hidden. The title therefore sits under the notch. Fix direction: pad by `systemBars ∪ displayCutout`.
- **Item 2.** A bottom page slider (`ReaderPageSlider`) already exists and renders when `state.pages.size > 1`; a per-chapter `LinearProgressIndicator` already exists in `ChapterSheetRow`. So "progress bar not shown" is ambiguous — which one is meant is an interview question.
- **Item 6.** Swipe-to-favourites fill is `Color(0xFFFFD600)` @ 0.85 alpha (`HomeComponents.kt:741`). The card border is `OverlayThemeTokens.FavoriteCardBorder = IconAccountYellow` at `2.dp`. Target: same gold, `~1.dp`, fading, plus a chip.
- **Item 9.** `AddFromApiUseCase` runs a poster download **and** (for Shikimori) a detail request *before* saving — hence the slow round-trip and the button snapping back. There is **no duplicate check at all**, and every invocation mints a fresh UUID, so N taps ⇒ N rows.
- **Item 8.** `AddFromApiUseCase.kt:67` does `result.episodes.coerceAtLeast(1)` — an unknown episode count is silently persisted as `1`.

### Existing terminology

`mediaType` (drives the Details tab) vs `categoryType` (genre category in AddEdit); `RatingScale` (10-point, stored ×10); `MangaChapter.key`; `ChapterReadingProgress.fraction`. No `CONTEXT.md` and no `docs/adr/` exist yet.

### Existing tests

`app/src/test` and `app/src/androidTest` exist; `app/src/test/java/.../data/models/` is untracked (new). Test coverage of the touched areas is effectively nil — these are Compose UI surfaces.

### Constraints discovered

1. **Dirty working tree.** 48 modified + 12 untracked source files, uncommitted, overlapping *every* todo area (`MangaReaderScreen.kt`, `ReaderDock.kt`, `MangaChaptersPage.kt`, `DetailsScreen.kt`, `AnimeCard.kt`, `HomeComponents.kt`, `AddFromApiUseCase.kt`, plus a brand-new `DetailsFactCards.kt` that appears to be item 7 already in progress). Safe isolation per ticket is **impossible** without a user decision.
2. `.claude/skills/*` shows 27 deleted files in git status — pre-existing, unrelated, must be preserved untouched.
3. Item 6 references `reference-favorites-chip.jpg`, which **does not exist** in the repo.
4. `:core:designsystem` is empty — do not add design tokens there expecting them to be wired up.

### Questions answerable from code

- Where the favourites gold comes from (item 6) — answered: `0xFFFFD600`.
- Why the add button reverts (item 9) — answered: pre-save network work, no optimistic state.
- Why the reader title is occluded (item 1) — answered: hidden system bars zero out `statusBarsPadding`.

### Remaining material uncertainties

Carried into `INTERVIEW`: working-tree handling; which progress bar item 2 means; whether item 3 (keiyoushi) is in scope for this run or research-only; whether "unknown episode count" becomes a real domain state or is display-only; the missing reference image; and what verification is actually available (build/emulator).
