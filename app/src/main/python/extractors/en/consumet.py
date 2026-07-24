import logging
from typing import List
import requests
from ..base import BaseExtractor, EpisodeInfo
from models import InputTask
from extractors.base import parse_episode_range
from tenacity import retry, stop_after_attempt, wait_exponential

logger = logging.getLogger(__name__)

class ConsumetExtractor(BaseExtractor):
    name: str = "Consumet"
    
    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
    def extract(self, task: InputTask) -> List[EpisodeInfo]:
        if not task.search_query:
            logger.warning(f"[{self.name}] No search_query provided.")
            return []
            
        headers = {
            "Accept-Language": "en-US,en;q=0.9",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
        }
            
        # Step 1: Search
        search_url = f"https://api.consumet.org/anime/gogoanime/{task.search_query}"
        try:
            resp = requests.get(search_url, headers=headers, timeout=10)
            if resp.status_code != 200:
                raise Exception(f"Search API returned {resp.status_code}")
            search_data = resp.json()
        except Exception as e:
            logger.error(f"[{self.name}] Search failed for {task.search_query}: {e}")
            return []
            
        results = search_data.get("results", [])
        if not results:
            logger.warning(f"[{self.name}] No anime found for {task.search_query}")
            return []
            
        anime_id = results[0].get("id")
        if not anime_id:
            logger.warning(f"[{self.name}] First result has no ID.")
            return []
            
        logger.info(f"[{self.name}] Found anime ID: {anime_id}")
        
        episodes = results[0].get("episodes", [])
        if not episodes:
            info_url = f"https://api.consumet.org/anime/gogoanime/info/{anime_id}"
            try:
                resp = requests.get(info_url, headers=headers, timeout=10)
                if resp.status_code == 200:
                    episodes = resp.json().get("episodes", [])
            except Exception as e:
                logger.error(f"[{self.name}] Info fetch failed for {anime_id}: {e}")
                
        if not episodes:
            logger.warning(f"[{self.name}] No episodes found for {anime_id}")
            return []
            
        requested_episodes = parse_episode_range(task.episode_range)
        episodes_to_download = [
            ep for ep in requested_episodes 
            if ep not in task.already_downloaded
        ]
        
        final_results = []
        for ep_data in episodes:
            ep_num = ep_data.get("number")
            if ep_num is None:
                continue
                
            try:
                ep_num = int(ep_num)
            except ValueError:
                continue
                
            if ep_num in episodes_to_download:
                ep_id = ep_data.get("id")
                if not ep_id:
                    continue
                    
                # Step 3: Get watch sources
                watch_url = f"https://api.consumet.org/anime/gogoanime/watch/{ep_id}"
                try:
                    resp = requests.get(watch_url, headers=headers, timeout=10)
                    if resp.status_code != 200:
                        raise Exception(f"Watch API returned {resp.status_code}")
                    watch_data = resp.json()
                    sources = watch_data.get("sources", [])
                    
                    best_source = None
                    for source in sources:
                        # Prefer 1080p m3u8, or default, or any
                        url = source.get("url", "")
                        quality = source.get("quality", "")
                        if source.get("isM3U8") or url.endswith(".mpd") or url.endswith(".m3u8"):
                            if quality == "1080p":
                                best_source = source
                                break
                            elif quality == "default" and not best_source:
                                best_source = source
                            elif not best_source:
                                best_source = source
                                
                    if best_source:
                        stream_url = best_source["url"]
                        # Knowledge from animego: replace .mpd with .m3u8 to avoid native muxing issues
                        if stream_url.endswith('.mpd'):
                            stream_url = stream_url.replace('.mpd', '.m3u8')
                            
                        # Knowledge from animego: explicitly provide referer to bypass CDN 403
                        referer = watch_data.get("headers", {}).get("Referer", "")
                        if not referer:
                            referer = "https://gogoplay.io/"
                            
                        final_results.append(
                            EpisodeInfo(
                                episode=ep_num,
                                m3u8_url=stream_url,
                                duration_sec=1440,
                                referer=referer
                            )
                        )
                    else:
                        logger.warning(f"[{self.name}] No valid stream source found for {ep_id}")
                except Exception as e:
                    logger.error(f"[{self.name}] Failed to fetch sources for {ep_id}: {e}")
                    
        return final_results
