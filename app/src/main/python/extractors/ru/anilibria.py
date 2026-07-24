import logging
import urllib.parse
import requests
from typing import List
from ..base import BaseExtractor, EpisodeInfo
from models import InputTask
from extractors.base import parse_episode_range

logger = logging.getLogger(__name__)

class AnilibriaExtractor(BaseExtractor):
    name: str = "Anilibria"
    
    def extract(self, task: InputTask) -> List[EpisodeInfo]:
        if not task.search_query:
            logger.warning(f"[{self.name}] Пустой поисковой запрос.")
            return []
            
        logger.debug(f"[{self.name}] Ищем тайтл: {task.search_query}")
        
        try:
            # 1. Используем официальное API v3
            url = f"https://api.anilibria.tv/v3/title/search?search={urllib.parse.quote(task.search_query)}"
            resp = requests.get(url, timeout=10).json()
            
            if not resp or "list" not in resp or len(resp["list"]) == 0:
                logger.warning(f"[{self.name}] Тайтл не найден.")
                return []
                
            title_data = resp["list"][0]
            logger.info(f"[{self.name}] Найден тайтл: {title_data.get('names', {}).get('ru')}")
            
            # 2. Достаем список эпизодов
            episodes_dict = title_data.get("player", {}).get("list", {})
            
            requested = parse_episode_range(task.episode_range)
            ep_to_get = [ep for ep in requested if ep not in task.already_downloaded]
            
            results = []
            for ep_num in ep_to_get:
                ep_str = str(ep_num)
                if ep_str in episodes_dict:
                    hls = episodes_dict[ep_str].get("hls", {})
                    
                    # Берем лучшее доступное качество, фоллбэк на более низкое
                    path = hls.get("hd") or hls.get("fhd") or hls.get("sd")
                    
                    if path:
                        m3u8_url = f"https://cache.libria.fun{path}"
                        results.append(
                            EpisodeInfo(
                                episode=ep_num,
                                m3u8_url=m3u8_url,
                                referer="https://www.anilibria.tv/"
                            )
                        )
                        logger.info(f"[{self.name}] Эпизод {ep_num}: поток найден!")
                        
            return results
            
        except Exception as e:
            logger.error(f"[{self.name}] Ошибка парсинга: {e}")
            return []
