A major update focused on stabilizing anime playback, rebuilding the video player, upgrading the manga reader, improving source reliability, and polishing the app UI.

🚨 Critical fixes

- Fixed a security issue where Supabase credentials could appear in Logcat
- Improved English anime playback and download compatibility
- Fixed incorrect episode counts for ongoing anime
- Improved season detection for titles with multiple seasons
- Prevented duplicate titles from being added to the library
- Fixed missing manga reading progress
- Fixed manga reader title overlap with camera cutouts

✨ Highlights

- Completely rebuilt the video player
- Added streaming, local playback, and download improvements
- Added automatic switching to the next episode
- Added automatic opening skip when timing data is available
- Added a manual 89-second skip button for opening scenes
- Added next and previous episode controls
- Redesigned the manga reader experience

▶️ Video player

- Rebuilt the player UI and interaction model
- Replaced the player icons with a cleaner new set
- Removed the old player docks for a cleaner viewing experience
- Fixed the voiceover selection wheel
- Voiceover selection is now saved between episodes
- Added pinch zoom and pan
- Added hold-to-play at 2x speed
- Added an expansion gesture for faster fullscreen control
- Consecutive double taps now accumulate seek time: +10s, +20s, +30s, and beyond
- Improved stream retry and fallback behavior
- The player now preserves playback position more reliably when switching sources
- Improved playback stability during unstable network conditions
- Redesigned the progress bar and quality menu

📖 Manga reader

- Added manga progress display on main screen cards
- Added new chapter chips for manga updates
- Added automatic layout detection when opening a title for the first time
- Added per-title reading mode memory
- Added per-title page direction support for RTL and LTR reading
- Added automatic scan margin cropping with a per-title toggle
- Improved stability when loading very long manga and webtoon pages
- Added retry support for failed manga pages
- Improved webtoon reading behavior
- Volumes are now collapsed by default after source binding
- Improved volume expand and collapse animations
- Redesigned the chapter list with cleaner cards and a better header

🧩 Sources & parsing

- Improved Kodik source loading speed and reliability
- Added a direct Kodik search path for better availability
- Improved AnimeGo video quality detection
- Added support for additional AnimeGo playback sources
- Improved Jut.su reliability with mirror support
- Improved franchise and season matching for Russian sources
- Background enrichment now pauses during video playback, local playback, and manga reading to reduce unnecessary requests

🏠 Library & search

- Improved progress bars on main screen cards
- Cards now switch between release progress and watch progress more intelligently
- Search results now show N/A instead of misleading zero episode or chapter counts
- The add button now responds instantly when adding a title from search
- Improved duplicate detection when adding titles
- Added clearer manga update indicators

🎨 UI improvements

- Updated category and sorting menu colors
- Reworked favorite card highlighting with a gold fading border and chip
- Redesigned Visual Search for easier navigation
- Improved Details layout by moving rating into the info card grid
- Removed duplicated info chips from Details
- Improved menu readability, spacing, and visual consistency
- Redesigned Settings cards and bottom sheets
- Added a new katana-style loading overlay

🧠 Recommendations

- Reworked recommendation algorithms
- Improved recommendation relevance and ranking
- Recommendations now better use library and metadata signals

⚠️ Known issues

- Episode search may still make mistakes, find incorrect episodes, or play the wrong season
- English search is still limited, because most of the work in this update focused on Russian-language sources
- Opening and ending skip may work incorrectly on some titles
- Opening and ending skip may not work on English sources yet
- Source availability may still depend on provider status and region

This Beta makes watching, reading, searching, and managing the library more stable, faster, safer, and more consistent.
