"""
Lightweight yt-dlp info extractor for UrlSource (Kotlin).
Returns a single JSON object string from yt-dlp -J / extract_info(download=False).
"""
import json
import logging

logger = logging.getLogger(__name__)


def extract_info_json(url: str) -> str:
    try:
        import yt_dlp
    except ImportError as e:
        raise RuntimeError("yt-dlp not available") from e

    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
        if info is None:
            return "{}"
        # Make JSON-serializable
        return json.dumps(info, ensure_ascii=False, default=str)
