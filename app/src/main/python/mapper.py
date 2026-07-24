import logging
from typing import List
from tenacity import retry, stop_after_attempt, wait_exponential
from utils import fetch_json

logger = logging.getLogger(__name__)

@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
def get_external_links(shikimori_id: str) -> List[dict]:
    """
    Fetches external links for a given anime from Shikimori.
    Returns a list of dictionaries with keys like 'kind' and 'url'.
    """
    url = f"https://shikimori.one/api/animes/{shikimori_id}/external_links"
    try:
        data = fetch_json(url)
        if isinstance(data, list):
            return data
        return []
    except Exception as e:
        logger.error(f"Failed to fetch external links for shikimori_id {shikimori_id}: {e}")
        return []
