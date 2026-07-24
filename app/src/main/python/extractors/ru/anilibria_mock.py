import logging
from typing import List
from ..base import BaseExtractor, EpisodeInfo, parse_episode_range
from models import InputTask

logger = logging.getLogger(__name__)

# Re-export for older imports (anilibria.py / animego.py / consumet / ytdlp_ext).
__all__ = ["AnilibriaMock", "parse_episode_range"]


class AnilibriaMock(BaseExtractor):
    name: str = "AnilibriaMock"

    def extract(self, task: InputTask) -> List[EpisodeInfo]:
        requested_episodes = parse_episode_range(task.episode_range)

        episodes_to_download = [
            ep for ep in requested_episodes
            if ep not in task.already_downloaded
        ]

        if not episodes_to_download:
            logger.info(f"[{self.name}] All requested episodes are already downloaded.")
            return []

        results = []
        for ep in episodes_to_download:
            if len(results) >= 3:
                break

            results.append(
                EpisodeInfo(
                    episode=ep,
                    m3u8_url="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    duration_sec=600,
                    referer="https://mock.anilibria.tv/"
                )
            )

        return results
