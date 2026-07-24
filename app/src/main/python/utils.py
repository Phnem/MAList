import json
import os
import threading
import logging
import requests
from tenacity import retry, stop_after_attempt, wait_exponential

logger = logging.getLogger(__name__)

_write_lock = threading.Lock()

def get_session() -> requests.Session:
    session = requests.Session()
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
    })
    return session

@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
def fetch_json(url: str, params: dict = None) -> dict:
    session = get_session()
    logger.debug(f"GET Request to {url} with params {params}")
    response = session.get(url, params=params, timeout=15)
    logger.debug(f"Response from {url}: {response.status_code}")
    response.raise_for_status()
    return response.json()

def atomic_write_json(data: dict, filepath: str):
    """
    Thread-safe atomic JSON write.
    Writes to a temporary file first, then renames it.
    """
    tmp_filepath = f"{filepath}.tmp"
    with _write_lock:
        try:
            with open(tmp_filepath, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2)
            os.replace(tmp_filepath, filepath)
        except Exception as e:
            logger.error(f"Failed to atomic_write_json to {filepath}: {e}")
            if os.path.exists(tmp_filepath):
                try:
                    os.remove(tmp_filepath)
                except Exception:
                    pass
