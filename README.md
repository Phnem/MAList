<h1 align="center">Vetra 🎬</h1>

<p align="center">
  <a href="multiSM.png">
    <img src="multiBG.png" width="900" alt="Vetra preview">
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

<h2 align="center">✨ What is Vetra?</h2>

Vetra is a powerful yet minimal <b>local-first media manager</b>.

Originally built as a personal anime tracker, it evolved into a flexible and scalable system with:

- The Depth of Light design language (3.2.x family)
- strict unidirectional data flow (UDF / MVI)
- clean architecture (Domain-driven)
- reactive data layer (Flow + SQLDelight)
- optional cloud sync
- advanced search (API + image recognition)

---

<h2 align="center">📸 Screenshots</h2>

<p align="center">
  <a href="dkBG.png"><img src="dkSM.png" width="220" alt="Screenshot 1"></a>
  <a href="ltBG.png"><img src="ltSM.png" width="220" alt="Screenshot 2"></a>
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
- Instant UI response (no fake delays)
- Deterministic state & predictable behavior

---

<h2>✨ Features</h2>

<h3>📋 Content Management</h3>

- Custom lists (anime, movies, shows, etc.)
- Rating system
- Favorites
- Comments & notes
- Fully reactive data updates

<h3>🔍 Advanced Search</h3>

- Real-time in-memory search
- API search (movies + anime)
- Auto-fetch metadata (cover, description, rating)

<h4>🧠 Image-based search (v3.1.x family)</h4>

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

<h2>🎨 Design Language — The Depth of Light</h2>

Introduced in the 3.2.x family, <b>The Depth of Light</b> defines the visual and interaction system of Vetra:

- light-driven hierarchy instead of flat layers
- depth through translucency and spatial separation
- physically consistent motion and feedback
- refined contrast between foreground and background
- emphasis on clarity, focus, and visual calm

Result:

- deeper visual immersion
- stronger spatial hierarchy
- more natural interaction feedback

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
- Fully reactive DB
- Single source of truth
- In-memory filtering & sorting

<h3>Dependency Injection</h3>

- Koin 4.x
- Fully modular graph
- No legacy singletons

<h3>Networking</h3>

- Ktor (CIO)
- Apollo GraphQL
- Improved API pipeline (3.1.x family)
- Structured error handling

---

<h2>⚡ Performance</h2>

Key improvements since 3.0:

- Removed Paging3 → no pagination lag
- Reduced recompositions
- Optimized LazyColumn behavior
- GPU optimization
- Image loading optimization (Coil 3.x)
- XML removal → no runtime parsing cost

Result:

- smooth scrolling
- instant search
- stable rendering

---

<h2>☁️ Cloud Sync (Optional)</h2>

Dropbox-based sync:

- File hashing (DropboxContentHasher)
- No delta sync complexity
- Safe DB restore (WAL fix)

Architecture remains local-first.

---

<h2>🔐 Storage & Privacy</h2>

- 100% local by default
- No analytics
- No tracking
- No hidden network calls

---

<h2>📄 License</h2>

This project is licensed under the <b>MIT License</b>.  
See the <a href="LICENSE">LICENSE</a> file for details.