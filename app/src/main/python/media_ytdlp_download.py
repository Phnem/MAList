"""
Direct URL download via yt-dlp for MediaDownloadWorker fallback (when ffmpeg remux is unavailable).
"""
import threading
import yt_dlp
from downloader import quality_to_format, DownloadAborted

_cancel = threading.Event()


def cancel():
    _cancel.set()


def download_url(url: str, output_path: str, quality: str = "720p", referer: str = "", callback=None):
    _cancel.clear()
    fmt = quality_to_format(quality)

    def progress_hook(d):
        if _cancel.is_set():
            raise DownloadAborted("Cancelled")
        if d.get("status") == "downloading" and callback is not None:
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            done = d.get("downloaded_bytes") or 0
            if total > 0:
                pct = int(done * 100 / total)
                try:
                    callback.onProgress(pct)
                except Exception:
                    pass
            else:
                frag_i = d.get("fragment_index")
                frag_n = d.get("fragment_count")
                if frag_i and frag_n:
                    try:
                        callback.onProgress(int(frag_i * 100 / frag_n))
                    except Exception:
                        pass

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
        ),
    }
    if referer:
        headers["Referer"] = referer

    opts = {
        "outtmpl": output_path,
        "format": fmt,
        "quiet": True,
        "no_warnings": True,
        "progress_hooks": [progress_hook],
        "http_headers": headers,
        "hls_prefer_native": True,
        "concurrent_fragment_downloads": 3,
        "merge_output_format": "mp4",
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        ydl.download([url])
    if callback is not None:
        try:
            callback.onProgress(100)
        except Exception:
            pass
    return output_path
