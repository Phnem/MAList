import logging
from typing import List
from ..base import BaseExtractor, EpisodeInfo
from models import InputTask
from extractors.base import parse_episode_range

try:
    import yt_dlp
except ImportError:
    yt_dlp = None

logger = logging.getLogger(__name__)

class YtDlpExtractor(BaseExtractor):
    name: str = "YtDlp (Universal)"
    
    def extract(self, task: InputTask) -> List[EpisodeInfo]:
        if not yt_dlp:
            logger.error(f"[{self.name}] yt-dlp is not installed.")
            return []
            
        if not task.web_url:
            logger.warning(f"[{self.name}] No web_url provided in task.")
            return []
            
        logger.info(f"[{self.name}] Attempting to extract directly from web_url: {task.web_url}")
        
        ydl_opts = {
            'format': 'best',
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False
        }
        
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(task.web_url, download=False)
                
            if not info:
                logger.warning(f"[{self.name}] yt-dlp returned no info for {task.web_url}")
                return []
                
            video_url = info.get('url')
            if not video_url:
                logger.warning(f"[{self.name}] No direct video URL found in info.")
                return []
                
            # Usually fallback is for a single requested episode. 
            # We will use the first requested episode number.
            requested_episodes = parse_episode_range(task.episode_range)
            episodes_to_download = [
                ep for ep in requested_episodes 
                if ep not in task.already_downloaded
            ]
            
            if not episodes_to_download:
                logger.info(f"[{self.name}] Requested episodes already downloaded.")
                return []
                
            target_ep = episodes_to_download[0]
            
            referer = info.get('http_headers', {}).get('Referer', '')
            
            logger.info(f"[{self.name}] Successfully extracted direct URL for episode {target_ep}.")
            return [
                EpisodeInfo(
                    episode=target_ep,
                    m3u8_url=video_url,
                    duration_sec=info.get('duration', 1440),
                    referer=referer
                )
            ]
            
        except Exception as e:
            logger.error(f"[{self.name}] yt-dlp extraction failed: {e}", exc_info=True)
            return []
