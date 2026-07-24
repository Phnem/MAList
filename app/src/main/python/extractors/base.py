from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional, List
import logging
import re

from models import InputTask

logger = logging.getLogger(__name__)


@dataclass
class EpisodeInfo:
    episode: int
    m3u8_url: str
    duration_sec: int = 1200
    referer: str = ""


def parse_episode_range(ep_range: str, max_episodes: int = 12) -> List[int]:
    """
    Superset parser for episode ranges from Kotlin UI and CLI.

    Supports:
      - "all"
      - "3"
      - "1-5"
      - "1,2,3"
      - "1-3,5,8-10"
    """
    if not ep_range or not str(ep_range).strip():
        return []

    text = str(ep_range).strip()
    if text.lower() == "all":
        return list(range(1, max_episodes + 1))

    result: List[int] = []
    for token in text.split(","):
        part = token.strip()
        if not part:
            continue
        if part.lower() == "all":
            result.extend(range(1, max_episodes + 1))
            continue
        if "-" in part:
            try:
                start_str, end_str = part.split("-", 1)
                start = int(start_str.strip())
                end = int(end_str.strip())
                if start > end:
                    start, end = end, start
                result.extend(range(start, end + 1))
            except ValueError:
                logger.error(f"Failed to parse episode range token: {part}")
            continue
        try:
            result.append(int(part))
        except ValueError:
            # Tolerate accidental whitespace / junk around digits
            digits = re.findall(r"\d+", part)
            if digits:
                result.append(int(digits[0]))
            else:
                logger.error(f"Failed to parse episode range token: {part}")

    # Preserve order, drop duplicates
    seen = set()
    ordered: List[int] = []
    for n in result:
        if n not in seen:
            seen.add(n)
            ordered.append(n)
    return ordered


class BaseExtractor(ABC):
    name: str = "BaseExtractor"

    def __init__(self):
        self._next: Optional['BaseExtractor'] = None

    def set_next(self, extractor: 'BaseExtractor') -> 'BaseExtractor':
        self._next = extractor
        return extractor

    @abstractmethod
    def extract(self, task: InputTask) -> List[EpisodeInfo]:
        """
        Abstract method to be implemented by specific extractors.
        Returns a list of EpisodeInfo if successful.
        """
        pass

    def handle(self, task: InputTask) -> List[EpisodeInfo]:
        try:
            logger.info(f"[{self.name}] Attempting to extract data...")
            result = self.extract(task)
            if result:
                logger.info(f"[{self.name}] Extraction successful. Found {len(result)} episodes.")
                return result
            else:
                logger.warning(f"[{self.name}] Extraction returned empty result.")
        except Exception as e:
            logger.error(f"[{self.name}] Extraction failed: {e}", exc_info=task.is_debug)

        if self._next:
            logger.info(f"[{self.name}] Passing request to next extractor: {self._next.name}")
            return self._next.handle(task)

        logger.error("End of chain reached. Could not extract data.")
        return []
