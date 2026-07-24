import logging
from typing import List
from ..base import BaseExtractor, EpisodeInfo
from models import InputTask
from extractors.base import parse_episode_range
from tenacity import retry, stop_after_attempt, wait_exponential

try:
    from anime_parsers_ru import AnimegoParser
except ImportError:
    AnimegoParser = None

logger = logging.getLogger(__name__)

class AnimeGoExtractor(BaseExtractor):
    name: str = "AnimeGo"
    
    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
    def extract(self, task: InputTask) -> List[EpisodeInfo]:
        if not AnimegoParser:
            logger.error(f"[{self.name}] anime-parsers-ru is not installed.")
            return []

        if not task.search_query:
            logger.warning(f"[{self.name}] No search_query provided.")
            return []

        parser = AnimegoParser()

        logger.debug(f"[{self.name}] Searching AnimeGo for {task.search_query}")
        search_results = parser.search(task.search_query)
        if not search_results:
            logger.warning(f"[{self.name}] No anime found for {task.search_query}")
            return []

        anime = search_results[0]
        # ВАЖНО: get_voices() ждёт ЧИСЛОВОЙ animego id, а НЕ полную ссылку.
        # Раньше сюда передавалась 'link' → URL склеивался в
        # /anime/https://animego.org/.../schedule/load → 404. Теперь берём поле 'id'.
        anime_id = self._extract_anime_id(anime)
        if not anime_id:
            raise ValueError(f"[{self.name}] Не удалось определить animego id из результата поиска: {anime}")

        anime_title = anime.get('title') if isinstance(anime, dict) else getattr(anime, 'title', 'Unknown')
        logger.debug(f"[{self.name}] Found anime: {anime_title} (id={anime_id})")

        requested = parse_episode_range(task.episode_range)
        episodes_to_get = [ep for ep in requested if ep not in task.already_downloaded]

        results: List[EpisodeInfo] = []
        for ep_num in episodes_to_get:
            # Список озвучек эпизода: {'voices': [{'label','translation_id','player','embed','cvh_id'}], ...}
            try:
                data = parser.get_voices(anime_id, ep_num)
            except Exception as e:
                logger.warning(f"[{self.name}] Эпизод {ep_num}: не удалось получить озвучки: {e}")
                continue

            voices = data.get('voices', []) if isinstance(data, dict) else []
            # Берём первую озвучку на плеере AniBoom — aniboom_get_stream() отдаёт прямой m3u8/mpd.
            # CVH-плеер требует отдельного (более сложного) потока — пока пропускаем.
            aniboom_voice = next(
                (v for v in voices if v.get('player') == 'AniBoom' and v.get('embed')),
                None,
            )
            if not aniboom_voice:
                available = [v.get('player') for v in voices]
                logger.warning(f"[{self.name}] Эпизод {ep_num}: AniBoom-плеер не найден (доступно: {available})")
                continue

            import requests
            import json
            import html
            from bs4 import BeautifulSoup
            
            try:
                embed_url = aniboom_voice['embed']
                # Парсим плеер AniBoom самостоятельно, чтобы вытащить HLS (m3u8), 
                # так как оригинальный anime-parsers-ru отдает приоритет DASH (mpd).
                headers = {
                    "Referer": "https://animego.me/",
                    "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                }
                resp = requests.get(embed_url, headers=headers, timeout=10)
                if resp.status_code != 200:
                    raise Exception(f"AniBoom HTTP {resp.status_code}")
                
                soup = BeautifulSoup(resp.text, "html.parser")
                video_tag = soup.find("video", {"id": "video"})
                if not video_tag:
                    raise Exception("No <video id='video'> found")
                
                raw_params = video_tag.get("data-parameters")
                if not raw_params:
                    raise Exception("No data-parameters found")
                    
                data = json.loads(html.unescape(raw_params))
                
                stream_url = None
                if "hls" in data:
                    stream_url = json.loads(data["hls"])["src"]
                elif "dash" in data:
                    # Фоллбек на dash, но мы знаем что без ffmpeg он упадет.
                    stream_url = json.loads(data["dash"])["src"]
                    
                if not stream_url:
                    raise Exception("No HLS or DASH src found in data")
                
                # Если все равно получили mpd, попробуем заменить на m3u8 (часто лежат рядом)
                if stream_url.endswith('.mpd'):
                    stream_url = stream_url.replace('.mpd', '.m3u8')
                    
            except Exception as e:
                logger.warning(f"[{self.name}] Эпизод {ep_num}: не удалось получить поток HLS: {e}")
                continue

            results.append(
                EpisodeInfo(
                    episode=ep_num,
                    m3u8_url=stream_url,
                    # Поток лежит на CDN AniBoom — Referer/Origin должны указывать на него,
                    # иначе CDN отдаёт 403 и yt-dlp не находит форматов.
                    referer="https://aniboom.one/",
                )
            )
            logger.info(f"[{self.name}] Эпизод {ep_num}: поток получен ({aniboom_voice.get('label')})")

        return results

    @staticmethod
    def _extract_anime_id(anime) -> str:
        """
        Числовой animego id из результата search(). Приоритет: поле 'id';
        фолбэк — последнее число из 'slug'/'link' (напр. naruto-...-1234 → 1234).
        """
        get = (lambda k: anime.get(k)) if isinstance(anime, dict) else (lambda k: getattr(anime, k, None))
        raw_id = get('id')
        if raw_id:
            return str(raw_id)
        import re
        for field in ('slug', 'link', 'url'):
            val = get(field)
            if val:
                match = re.search(r'(\d+)\/?$', str(val))
                if match:
                    return match.group(1)
        return ""
