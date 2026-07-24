# Smoke matrix (Phase 4) — manual QA checklist before dropping use_native_media_engine flag
#
# Device/emulator with network + arm64 ffmpeg jniLib preferred.
#
# [ ] RU AniLibria episode online (headers work, plays in StreamPlayerActivity)
# [ ] RU AnimeGo / AniBoom episode online
# [ ] RU jut.su episode online (progressive MP4 + Referer)
# [ ] EN Consumet/Gogoanime episode online (same resolve → play | download)
# [ ] Download via ffmpeg fast-path (HLS → E00N.mp4)
# [ ] Download progressive MP4 (jut.su) without remux
# [ ] Download via yt-dlp fallback (UrlSource / remux failure)
# [ ] Offline play of downloaded file in localplayer
# [ ] Cancel download (MediaJobBus.requestCancel / cancel_task)
# [ ] Re-resolve on 403/410 mid-stream (expires → new URL, position kept)
# [ ] Legacy DownloadWizard still works (Python ranges "1,2,3", quality, push progress)
# [ ] APK size delta acceptable (jsoup + no multi-package runtimes)
