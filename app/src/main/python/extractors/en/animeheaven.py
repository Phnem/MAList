import logging
import re
from html import unescape
from typing import List, Optional, Tuple
from urllib.parse import quote

import requests
from tenacity import retry, stop_after_attempt, wait_exponential

from models import InputTask
from extractors.base import BaseExtractor, EpisodeInfo, parse_episode_range

logger = logging.getLogger(__name__)

BASE_URL = "https://animeheaven.me"
_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)

# Search cards: <a href='anime.php?ak2gr'>Frieren: Beyond Journey's End</a>
_SEARCH_CARD = re.compile(r"<a[^>]*href=['\"]anime\.php\?(\w+)['\"][^>]*>(.*?)</a>", re.S)
# Episode rows: <a ... id="<32 hex>" href='gate.php'> … <div class=' watch2 bc '>28</div>
_EPISODE_ROW = re.compile(
    r"id=[\"'](\w{16,64})[\"'][^>]*href=\s*['\"]gate\.php['\"].*?class='[^']*\bwatch2\b[^']*'>\s*(\d+)\s*<",
    re.S,
)
_SOURCE_TAG = re.compile(r"<source[^>]+src=['\"]([^'\"]+)['\"]", re.I)
_TAGS = re.compile(r"<[^>]+>")

_SEASON_WORD = re.compile(r"\bseason\s+(\d{1,2})\b", re.I)
_ORDINAL_SEASON = re.compile(r"\b(\d{1,2})(?:st|nd|rd|th)\s+season\b", re.I)
_TRAILING_ROMAN = re.compile(r"\s(I{1,3}|IV|V|VI{1,3}|IX|X)$")
_TRAILING_NUMBER = re.compile(r"\s(\d{1,2})$")
_ROMAN = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7, "VIII": 8, "IX": 9, "X": 10}


def _season_of(title: str) -> int:
    """Season number a site title advertises; 1 when it carries no marker."""
    text = title.strip()
    for pattern in (_SEASON_WORD, _ORDINAL_SEASON):
        m = pattern.search(text)
        if m:
            return int(m.group(1))
    m = _TRAILING_ROMAN.search(text)
    if m:
        return _ROMAN.get(m.group(1).upper(), 1)
    m = _TRAILING_NUMBER.search(text)
    if m:
        return int(m.group(1))
    return 1


def _base_title(title: str) -> str:
    """Title without its season marker — what the fuzzy comparison should see."""
    text = title
    for pattern in (_SEASON_WORD, _ORDINAL_SEASON, _TRAILING_ROMAN, _TRAILING_NUMBER):
        text = pattern.sub(" ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text or title.strip()


def _normalize(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^0-9a-zA-Zа-яА-Я]+", " ", text.lower())).strip()


def _similarity(a: str, b: str) -> float:
    """Token-set similarity, mirroring the Kotlin TitleMatcher closely enough for ranking."""
    a_norm, b_norm = _normalize(a), _normalize(b)
    if not a_norm or not b_norm:
        return 0.0
    if a_norm == b_norm:
        return 1.0
    a_tokens, b_tokens = set(a_norm.split()), set(b_norm.split())
    smaller, larger = (a_tokens, b_tokens) if len(a_tokens) <= len(b_tokens) else (b_tokens, a_tokens)
    if (len(smaller) >= 2 or sum(len(t) for t in smaller) >= 5) and smaller <= larger:
        return 0.92
    union = a_tokens | b_tokens
    return len(a_tokens & b_tokens) / len(union) if union else 0.0


class AnimeHeavenExtractor(BaseExtractor):
    """
    EN extractor for animeheaven.me — replaces the Consumet/Gogoanime one (api.consumet.org was
    shut down and answers every request with an HTML redirect to GitHub, so it never returned
    a stream). Kept in sync with `media/source/AnimeHeavenSource.kt`.

    search.php → anime.php?<id> → gate.php with `Cookie: key=<episode key>` → direct MP4.
    """

    name: str = "AnimeHeaven"
    MATCH_THRESHOLD = 0.91

    def __init__(self):
        super().__init__()
        self._session = requests.Session()
        self._session.headers.update({
            "User-Agent": _UA,
            "Accept-Language": "en-US,en;q=0.9",
        })

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
    def extract(self, task: InputTask) -> List[EpisodeInfo]:
        if not task.search_query:
            logger.warning(f"[{self.name}] No search_query provided.")
            return []

        # The wizard sends the franchise season it is downloading; the query itself may also carry a
        # marker ("… Season 2") when the season title was used.
        season = getattr(task, "season_number", 1) or 1
        entry = self._find_entry(task.search_query, max(season, _season_of(task.search_query)))
        if not entry:
            return []
        anime_id, anime_title = entry

        available = self._episode_keys(anime_id)
        if not available:
            logger.warning(f"[{self.name}] No episodes listed for '{anime_title}'.")
            return []

        # Ongoing seasons list only what has aired — never ask for more than the site actually has.
        requested = parse_episode_range(task.episode_range, max_episodes=max(available))
        wanted = [ep for ep in requested if ep not in task.already_downloaded]

        results: List[EpisodeInfo] = []
        for ep in wanted:
            key = available.get(ep)
            if not key:
                logger.warning(f"[{self.name}] '{anime_title}' has no episode {ep}, skipping.")
                continue
            url = self._gate_source(anime_id, key)
            if url:
                results.append(
                    EpisodeInfo(
                        episode=ep,
                        m3u8_url=url,
                        duration_sec=1440,
                        referer=f"{BASE_URL}/",
                    )
                )
            else:
                logger.warning(f"[{self.name}] No source for '{anime_title}' episode {ep}.")

        logger.info(f"[{self.name}] Resolved {len(results)}/{len(wanted)} episodes of '{anime_title}'.")
        return results

    # ------------------------------------------------------------------
    # search.php → title entry of the requested season
    # ------------------------------------------------------------------

    def _find_entry(self, query: str, target_season: int = 1) -> Optional[Tuple[str, str]]:
        candidates = self._search(_base_title(query)) or self._search(query)
        if not candidates:
            logger.warning(f"[{self.name}] Search miss for '{query}'.")
            return None

        scored = []
        for anime_id, title in candidates:
            score = _similarity(_base_title(query), _base_title(title))
            if score >= self.MATCH_THRESHOLD:
                scored.append((score, _similarity(query, title), _season_of(title), anime_id, title))
        if not scored:
            logger.warning(f"[{self.name}] No candidate above threshold for '{query}'.")
            return None

        exact = [c for c in scored if c[2] == target_season]
        # An unmarked entry is season 1, so only season 1 may fall back to the best overall match.
        pool = exact or (scored if target_season == 1 else [])
        if not pool:
            logger.warning(
                f"[{self.name}] No entry for season {target_season} of '{query}' "
                f"(have {[(c[4], c[2]) for c in scored]})."
            )
            return None

        best = max(pool, key=lambda c: (c[0], c[1]))
        logger.info(f"[{self.name}] Matched '{best[4]}' score={best[0]:.2f} season={best[2]}.")
        return best[3], best[4]

    def _search(self, query: str) -> List[Tuple[str, str]]:
        if not query.strip():
            return []
        try:
            resp = self._session.get(
                f"{BASE_URL}/search.php?s={quote(query)}",
                headers={"Referer": f"{BASE_URL}/"},
                timeout=15,
            )
            resp.raise_for_status()
        except Exception as e:
            logger.error(f"[{self.name}] Search failed for '{query}': {e}")
            return []

        found = {}
        for anime_id, inner in _SEARCH_CARD.findall(resp.text):
            title = unescape(_TAGS.sub("", inner)).strip()
            if title and anime_id not in found:
                found[anime_id] = title
        return list(found.items())

    # ------------------------------------------------------------------
    # anime.php → {episode number: gate key}
    # ------------------------------------------------------------------

    def _episode_keys(self, anime_id: str) -> dict:
        try:
            resp = self._session.get(
                f"{BASE_URL}/anime.php?{anime_id}",
                headers={"Referer": f"{BASE_URL}/search.php"},
                timeout=15,
            )
            resp.raise_for_status()
        except Exception as e:
            logger.error(f"[{self.name}] Title page failed for {anime_id}: {e}")
            return {}
        return {int(number): key for key, number in _EPISODE_ROW.findall(resp.text)}

    # ------------------------------------------------------------------
    # gate.php → direct MP4
    # ------------------------------------------------------------------

    def _gate_source(self, anime_id: str, episode_key: str) -> Optional[str]:
        try:
            # gate.php serves whichever episode the `key` cookie names — the site sets it in JS right
            # before navigating. Passed per request so parallel resolves can't race on the session.
            resp = self._session.get(
                f"{BASE_URL}/gate.php",
                headers={"Referer": f"{BASE_URL}/anime.php?{anime_id}"},
                cookies={"key": episode_key},
                timeout=15,
            )
            resp.raise_for_status()
        except Exception as e:
            logger.error(f"[{self.name}] gate.php failed for {episode_key}: {e}")
            return None

        for url in _SOURCE_TAG.findall(resp.text):
            if url.startswith("http"):
                return url
        return None
