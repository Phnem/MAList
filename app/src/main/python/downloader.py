from java import jclass
import yt_dlp
import threading
import os
import logging
from urllib.parse import urlparse
from extractors.base import EpisodeInfo

logger = logging.getLogger(__name__)

_BROWSER_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
)


class DownloadAborted(Exception):
    """Raised when cooperative cancel Event is set during yt-dlp download."""


def quality_to_format(quality: str) -> str:
    """Map UI quality labels to yt-dlp format selectors."""
    q = (quality or "720p").strip().lower()
    height = 720
    if "1080" in q:
        height = 1080
    elif "480" in q:
        height = 480
    elif "360" in q:
        height = 360
    elif "720" in q:
        height = 720
    return f"bv*[height<={height}],ba/b[height<={height}]/b"


def _mux_video_audio(video_path: str, audio_path: str, output_path: str):
    """Мультиплексирует видео и аудио используя нативный Android MediaMuxer."""
    logger.info("[Muxer] Entering _mux_video_audio")
    try:
        MediaExtractor = jclass('android.media.MediaExtractor')
        MediaMuxer = jclass('android.media.MediaMuxer')
        MediaFormat = jclass('android.media.MediaFormat')
        ByteBuffer = jclass('java.nio.ByteBuffer')
        MediaCodec = jclass('android.media.MediaCodec')

        logger.info(f"[Muxer] Creating MediaMuxer for: {output_path}")
        muxer = MediaMuxer(output_path, 0)

        logger.info(f"[Muxer] Extracting video from: {video_path}")
        video_extractor = MediaExtractor()
        video_extractor.setDataSource(video_path)
        video_track_index = -1
        video_muxer_index = -1
        for i in range(video_extractor.getTrackCount()):
            fmt = video_extractor.getTrackFormat(i)
            mime = fmt.getString(MediaFormat.KEY_MIME)
            if mime.startswith('video/'):
                video_extractor.selectTrack(i)
                video_track_index = i
                video_muxer_index = muxer.addTrack(fmt)
                logger.info(f"[Muxer] Found video track: {mime}")
                break

        logger.info(f"[Muxer] Extracting audio from: {audio_path}")
        audio_extractor = MediaExtractor()
        audio_extractor.setDataSource(audio_path)
        audio_track_index = -1
        audio_muxer_index = -1
        for i in range(audio_extractor.getTrackCount()):
            fmt = audio_extractor.getTrackFormat(i)
            mime = fmt.getString(MediaFormat.KEY_MIME)
            if mime.startswith('audio/'):
                audio_extractor.selectTrack(i)
                audio_track_index = i
                audio_muxer_index = muxer.addTrack(fmt)
                logger.info(f"[Muxer] Found audio track: {mime}")
                break

        if video_track_index == -1 or audio_track_index == -1:
            video_extractor.release()
            audio_extractor.release()
            muxer.release()
            raise RuntimeError(f"Failed to find track (v={video_track_index}, a={audio_track_index})")

        logger.info("[Muxer] Starting MediaMuxer")
        muxer.start()

        buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        bufferInfo = MediaCodec.BufferInfo()

        logger.info("[Muxer] Copying video samples...")
        video_samples = 0
        while True:
            sampleSize = video_extractor.readSampleData(buffer, 0)
            if sampleSize < 0:
                break
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = video_extractor.getSampleTime()
            bufferInfo.flags = video_extractor.getSampleFlags()
            muxer.writeSampleData(video_muxer_index, buffer, bufferInfo)
            video_extractor.advance()
            video_samples += 1

        logger.info(f"[Muxer] Finished copying {video_samples} video samples. Copying audio...")
        audio_samples = 0
        while True:
            sampleSize = audio_extractor.readSampleData(buffer, 0)
            if sampleSize < 0:
                break
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = audio_extractor.getSampleTime()
            bufferInfo.flags = audio_extractor.getSampleFlags()
            muxer.writeSampleData(audio_muxer_index, buffer, bufferInfo)
            audio_extractor.advance()
            audio_samples += 1

        logger.info(f"[Muxer] Finished copying {audio_samples} audio samples. Releasing resources...")
        muxer.stop()
        muxer.release()
        video_extractor.release()
        audio_extractor.release()
        logger.info("[Muxer] SUCCESS: Muxing finished.")

    except Exception as e:
        logger.error(f"[Muxer] CRITICAL ERROR during muxing: {repr(e)}")
        import traceback
        logger.error(traceback.format_exc())
        raise


class HlsDownloader:
    def __init__(self, cancel_event: threading.Event = None):
        self.active_processes = []
        self._lock = threading.Lock()
        self.cancel_event = cancel_event or threading.Event()

    def download_episode(self, episode: EpisodeInfo, output_dir: str, on_progress, quality: str = "720p"):
        if self.cancel_event.is_set():
            raise DownloadAborted("Cancelled before start")

        filename = f"E{episode.episode:03d}.mp4"
        output_path = os.path.join(output_dir, filename)

        tmp_outtmpl = os.path.join(output_dir, f"E{episode.episode:03d}_tmp_%(format_id)s.%(ext)s")
        fmt = quality_to_format(quality)

        def progress_hook(d):
            if self.cancel_event.is_set():
                raise DownloadAborted("Cancelled by user")
            if d.get('status') == 'downloading':
                frag_index = d.get('fragment_index')
                frag_count = d.get('fragment_count')
                if frag_index and frag_count and frag_count > 0:
                    pct = int((frag_index / frag_count) * 100)
                    on_progress(episode.episode, pct)

        referer = getattr(episode, 'referer', '') or 'https://aniboom.one/'
        parsed = urlparse(referer)
        origin = f"{parsed.scheme}://{parsed.netloc}" if parsed.netloc else 'https://aniboom.one'

        ydl_opts = {
            'outtmpl': tmp_outtmpl,
            'http_headers': {
                'Referer': referer,
                'Origin': origin,
                'User-Agent': _BROWSER_UA,
            },
            'quiet': True,
            'no_warnings': True,
            'progress_hooks': [progress_hook],
            'hls_prefer_native': True,
            'concurrent_fragment_downloads': 3,
            'format': fmt,
        }

        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                with self._lock:
                    self.active_processes.append(ydl)

                info = ydl.extract_info(episode.m3u8_url, download=True)

                with self._lock:
                    if ydl in self.active_processes:
                        self.active_processes.remove(ydl)

                if self.cancel_event.is_set():
                    raise DownloadAborted("Cancelled after download")

                requested_downloads = info.get('requested_downloads', [])
                if not requested_downloads:
                    filepath = info.get('filepath')
                    if filepath and os.path.exists(filepath):
                        os.rename(filepath, output_path)
                    else:
                        raise RuntimeError("yt-dlp returned success but no files found.")
                elif len(requested_downloads) == 1:
                    filepath = requested_downloads[0].get('filepath')
                    os.rename(filepath, output_path)
                elif len(requested_downloads) >= 2:
                    v_path = requested_downloads[0].get('filepath')
                    a_path = requested_downloads[1].get('filepath')

                    logger.info(f"[Downloader] Muxing natively via Android MediaMuxer: {v_path} + {a_path}")
                    _mux_video_audio(v_path, a_path, output_path)

                    if os.path.exists(v_path):
                        os.remove(v_path)
                    if os.path.exists(a_path):
                        os.remove(a_path)

            on_progress(episode.episode, 100)
            return output_path

        except DownloadAborted:
            if os.path.exists(output_path):
                os.remove(output_path)
            raise
        except Exception as e:
            if os.path.exists(output_path):
                os.remove(output_path)
            if self.cancel_event.is_set():
                raise DownloadAborted(str(e))
            raise RuntimeError(f"Download failed: {str(e)}")

    def shutdown(self):
        """Cooperative cancel: set event so progress_hook aborts yt-dlp."""
        self.cancel_event.set()
        with self._lock:
            self.active_processes.clear()
