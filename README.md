# Vetro Collection 🎬📚

[![Vetro Collection](https://github.com/Phnem/Vetro/raw/main/images/hero.png)](https://github.com/Phnem/Vetro/blob/main/images/hero.png)

**Offline-first media collection manager built on The Depth of Light design language.**
Anime · Manga · Manhwa · Movies · TV Shows · Anything structured

Kotlin · Compose · Ktor · Koin · Supabase · SQLDelight

**Latest release: v3.3.2-Beta** · Latest stable: v3.3.1-Stable
See [Releases](https://github.com/Phnem/Vetro/releases) for the full changelog history.

---

## ✨ What is Vetro?

Vetro (in-app: **Vetro Collection**, following a full project rebrand) is a powerful yet minimal **local-first media manager**.

Originally built as an anime tracker, it's evolving into a universal media collection manager — anime, manga, manhwa, movies, and TV shows, all presented as one unified library.

- The Depth of Light design language
- Experimental multi-media support: manga & manhwa (v3.2.8+)
- Native cloud authentication (Supabase) & optional sync
- Strict unidirectional data flow (UDF / MVI)
- Clean, domain-driven architecture
- Reactive, multi-database data layer (Flow + SQLDelight)
- Advanced search (API + image recognition)

---

## 📸 Screenshots

[![Screenshot 1](https://github.com/Phnem/Vetro/raw/main/images/1.png)](https://github.com/Phnem/Vetro/blob/main/images/1.png) [![Screenshot 2](https://github.com/Phnem/Vetro/raw/main/images/2.png)](https://github.com/Phnem/Vetro/blob/main/images/2.png) [![Screenshot 3](https://github.com/Phnem/Vetro/raw/main/images/3.png)](https://github.com/Phnem/Vetro/blob/main/images/3.png) [![Screenshot 4](https://github.com/Phnem/Vetro/raw/main/images/4.png)](https://github.com/Phnem/Vetro/blob/main/images/4.png) [![Screenshot 5](https://github.com/Phnem/Vetro/raw/main/images/5.png)](https://github.com/Phnem/Vetro/blob/main/images/5.png)

*Click any image to view full resolution*

---

## 🧠 Core Philosophy

- 100% usable offline
- No forced accounts
- Your data belongs to you
- Cloud = extension, not requirement
- Instant UI response (no artificial delays)
- Deterministic state & predictable behavior

---

## ✨ Features

### 📋 Content Management

- Custom lists (anime, manga, manhwa, movies, shows, etc.)
- Precise 0.0–10.0 rating system
- Favorites
- Comments & notes
- Fully reactive updates

### 📚 Manga & Manhwa (Experimental)

Vetro is no longer limited to anime and TV.

- Track manga, manhwa, and other printed media alongside your existing collection
- Stored in an independent SQLDelight database, fully isolated from existing anime libraries — no migration risk, no corruption of existing data
- Presented as one seamless collection via reactive Kotlin Flow merging; you never need to know which database a title actually lives in
- Legacy entries without a media type are automatically detected and tagged as Anime for backward compatibility

⚠️ Still experimental — synchronization and metadata handling for printed media are under active development.

### 🔍 Advanced Search

- Redesigned Search interface in The Depth of Light style
- Quick media-type filter capsules (Anime / Manga / TV Shows) above the search field
- 250–300ms debounce to avoid unnecessary queries while typing
- Real-time in-memory search
- API search (anime + manga + movies) with automatic metadata fetch (cover, description, rating)
- Media-type badges on every card
- Quick media-type filter dropdown in the top navigation

#### 🧠 Image-based search

- Identify content from a screenshot
- Anime → trace.moe
- Movies → AI recognition

Workflow: upload a frame → detect title → add directly to library → metadata auto-fills → genres remain manually editable.

### 👉 Gestures

- Swipe to delete
- Swipe to favorite
- Haptic feedback

[![Swipe gestures](https://github.com/Phnem/Vetro/raw/main/swipe.png)](https://github.com/Phnem/Vetro/blob/main/swipe.png)

### 💡 Recommendations

A dedicated Recommendations section surfaces new anime, manga, and shows to discover based on your existing collection.

### 🎭 Digital Sarcasm System

- Context-aware sarcasm engine with 600+ dynamic phrases in Statistics
- Reacts to your taste — judges it, or supports your optimism

---

## 🔐 Authentication & Accounts

- Native authentication via the Supabase SDK
- Sign in with **Google** or **GitHub** (OAuth)
- Optional **Email + Password** login with a permanent password
- Dedicated account settings screen to view auth status and manage your linked cloud account

No embedded API keys ship with the app — bring your own key for AI-powered features like image recognition, keeping usage transparent and abuse-resistant.

---

## ⭐ Rating System

- Precise **0.0–10.0** scoring, replacing the old 1–5 scale, for finer control and better compatibility with external services
- Redesigned rating controls in the Add / Edit screens for faster, more intuitive scoring

---

## 🔄 Sync & Integrations

Primary providers:
- Shikimori
- AniList

Fallback provider:
- Jikan API (automatic fallback improves reliability when a primary provider is unavailable)

- Redesigned connection screens for AniList, MyAnimeList, and Shikimori
- Import / Export / Sync workflows to move libraries in and out of Vetro
- Dynamic sync status badge that updates in real time
- Unified genre database shared consistently between anime and manga

⚠️ **Known issue:** genre synchronization is currently incomplete. Developer Settings → **Repair Database** restores missing genres, covers, and metadata as a temporary workaround; a full synchronization fix is planned for a future release.

---

## 🎨 UI & UX

- Complete animation overhaul across the app: smoother transitions, more natural motion, refined timing and interaction feedback
- All overlays — bottom sheets, dialogs, context menus, pop-ups — rebuilt in The Depth of Light style
- Details Bottom Sheet trimmed from ~80% to ~60% of screen height, with the header moved directly above the artwork
- Unified bottom sheet corner radius (32dp → 36dp) across the app
- Completely redesigned Settings screen with clearer grouping, hierarchy, and navigation
- Completely redesigned bottom navigation dock
- New primary typeface for improved readability
- New color palette with refined accents, consistent across light and dark themes
- Tile-based genre system, unified across Add / Edit / Sorting

---

## 🛠 Developer Tools

- **Repair Database**: recovers missing genres, cover images, metadata, and sync artifacts after an interrupted sync or incomplete import
- Developer option to toggle adaptive blur rendering
- Database Import
- PDF export (shareable, readable tables)

---

## 🔄 Update System

- Separate update modes for F-Droid and GitHub distributions
- GitHub update checks are disabled by default on F-Droid installs and redirect to the official project website instead
- When enabled, Vetro fetches release notes directly from GitHub and runs the built-in updater

---

## 🏗 Architecture

### Clean Architecture

- UI / Domain / Data separation
- UseCases for business logic (`SaveAnimeUseCase`, `UpdateCommentUseCase`, `GetAnimeForEditUseCase`, …)
- DTO layer (`SaveAnimeParams`)
- No Android dependencies in ViewModels

### Multi-Database Data Layer

- SQLDelight + Flow, fully reactive, single source of truth
- Independent database for printed media (manga/manhwa), isolated from the anime database to eliminate migration risk
- Multiple databases merged into one reactive UI list via Kotlin Flow `combine()`
- Sorting and filtering moved entirely into SQL; `distinctUntilChanged()` after queries avoids redundant state updates

### State Management

- Strict UDF (Unidirectional Data Flow)
- Immutable UI state
- SideEffects via Channel
- No callback anti-patterns

### Dependency Injection

- Koin 4.x, fully modular graph, no legacy singletons

### Networking

- Ktor (CIO)
- Apollo GraphQL
- Structured error handling

---

## ⚡ Performance

- **Adaptive blur rendering**: expensive blur and lens effects pause during fast scrolling and smoothly return once it stops, virtually eliminating micro-stutters
- SQLDelight query and startup optimizations, fewer unnecessary recompositions
- Optimized Coil image loading: smarter thumbnail limits, lower memory use, better handling of legacy high-resolution posters
- Removed Paging3 → no pagination lag
- GPU improvements, XML removal (no runtime parsing)

Result: smoother scrolling, faster collection rendering, instant search, stable rendering.

---

## ☁️ Cloud Sync (Optional)

Dropbox-based sync:

- File hashing (DropboxContentHasher)
- Simple and reliable restore
- No complex delta sync

Local-first architecture remains unchanged — cloud sync is always optional, never required.

---

## 🌍 Localization

- Bilingual (English / Russian) throughout the app
- Ongoing cleanup of hardcoded strings and translation consistency fixes with every release

---

## 🔐 Storage & Privacy

- 100% local by default
- No analytics
- No tracking
- No hidden network activity

---

## 📄 License

This project is licensed under the **MIT License**.
See the [LICENSE](https://github.com/Phnem/Vetro/blob/main/LICENSE) file for details.
