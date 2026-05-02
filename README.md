<h1 align="center">Vetro 🎬</h1>

<p align="center">
  <a href="images/multiBG.png">
    <img src="images/multiSM.png" width="900" alt="Vetro preview">
  </a>
</p>

<p align="center">
  <b>Offline-first media tracker built on The Depth of Light design language.</b><br>
  Anime · Movies · TV Shows · Anything structured
</p>

<p align="center">
  Kotlin · Compose · Ktor · Koin · SQLDelight
</p>

---

<h2 align="center">✨ What is Vetro?</h2>

Vetro is a powerful yet minimal <b>local-first media manager</b>.

Originally built as an anime tracker, it has evolved into a flexible and scalable system with:

- The Depth of Light design language (3.2.x)
- strict unidirectional data flow (UDF / MVI)
- clean architecture (domain-driven)
- reactive data layer (Flow + SQLDelight)
- optional cloud sync & external integrations
- advanced search (API + image recognition)

---

<h2 align="center">📸 Screenshots</h2>

<p align="center">
  <a href="images/dkBG.png"><img src="images/dkSM.png" width="220" alt="Screenshot 1"></a>
  <a href="images/ltBG.png"><img src="images/ltSM.png" width="220" alt="Screenshot 2"></a>
</p>

<p align="center">
  <i>Click any image to view full resolution</i>
</p>

---

<h2>🧠 Core Philosophy</h2>

- 100% usable offline
- No forced accounts
- Your data belongs to you
- Cloud = extension, not requirement
- Instant UI response (no artificial delays)
- Deterministic state & predictable behavior

---

<h2>✨ Features</h2>

<h3>📋 Content Management</h3>

- Custom lists (anime, movies, shows, etc.)
- Rating system
- Favorites
- Comments & notes
- Fully reactive updates

<h3>🔍 Advanced Search</h3>

- Real-time in-memory search
- API search (anime + movies)
- Auto-fetch metadata (cover, description, rating)

<h4>🧠 Image-based search</h4>

- Identify content from a screenshot
- Anime → trace.moe
- Movies → AI recognition

Workflow:

- Upload frame → detect title
- Add directly to library
- Auto-fill metadata
- Genres editable manually

<h3>👉 Gestures</h3>

- Swipe to delete
- Swipe to favorite
- Haptic feedback

<p align="center">
  <img src="swipe.png" width="350" style="border-radius:16px;" alt="Swipe gestures">
</p>

---

<h2>🧪 New in 3.2.x</h2>

<h3>🎨 Visual & UI Evolution</h3>

- Fully reworked light theme:
  - improved palette
  - better contrast
  - refined accent system

- Updated global lighting model:
  - heavy glow → subtle light accents
  - more consistent visuals across screens

- Tile-based genre system:
  - unified across Add / Edit / Sorting

- Redesigned dialogs in Depth of Light style

<h3>🔐 Security</h3>

- Removed embedded Gemini API key
- Users provide their own key
- Prevents abuse and improves transparency

<h3>🔄 Sync & Integrations</h3>

- New Sync system UI
- Import / Export / Sync workflows

Integrations:

- Shikimori API
- AniList API

Capabilities:

- Import external libraries → into Vetro
- Export Vetro collection → external services

⚠️ Experimental feature

<h3>🛠 Data & Tools</h3>

- Database Import added
- PDF export (shareable readable tables)
- Developer Settings section

<h3>🎭 Digital Sarcasm System</h3>

- Context-aware sarcasm engine

Features:

- 600+ dynamic phrases in Statistics
- App reacts to your taste:
  - judges it
  - or supports your optimism

<h3>🎬 UX & Interaction</h3>

- Visual Search gestures added
- Improved navigation flow
- Better animation consistency

---

<h2>🏗 Architecture (3.x)</h2>

<h3>Clean Architecture</h3>

- UI / Domain / Data separation
- UseCases for business logic:
  - SaveAnimeUseCase
  - UpdateCommentUseCase
  - GetAnimeForEditUseCase

- DTO layer (SaveAnimeParams)
- No Android dependencies in ViewModels

<h3>State Management</h3>

- Strict UDF (Unidirectional Data Flow)
- Immutable UI state
- SideEffects via Channel
- No callback anti-patterns

<h3>Data Layer</h3>

- SQLDelight + Flow
- Fully reactive database
- Single source of truth
- In-memory filtering & sorting

<h3>Dependency Injection</h3>

- Koin 4.x
- Fully modular graph
- No legacy singletons

<h3>Networking</h3>

- Ktor (CIO)
- Apollo GraphQL
- Structured error handling

---

<h2>⚡ Performance</h2>

- Removed Paging3 → no pagination lag
- Reduced recompositions
- Optimized LazyColumn behavior
- GPU improvements
- Image loading optimization (Coil 3.x)
- XML removal → no runtime parsing

Result:

- smooth scrolling
- instant search
- stable rendering

---

<h2>☁️ Cloud Sync (Optional)</h2>

Dropbox-based sync:

- File hashing (DropboxContentHasher)
- Simple and reliable restore
- No complex delta sync

Local-first architecture remains unchanged.

---

<h2>🔐 Storage & Privacy</h2>

- 100% local by default
- No analytics
- No tracking
- No hidden network activity

---

<h2>📄 License</h2>

This project is licensed under the <b>MIT License</b>.  
See the <a href="LICENSE">LICENSE</a> file for details.