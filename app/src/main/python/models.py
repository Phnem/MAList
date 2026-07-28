from pydantic import BaseModel, Field
from typing import Optional

class InputTask(BaseModel):
    task_id: str
    anime_id: str
    mal_id: Optional[int] = None
    shikimori_id: Optional[str] = None
    web_url: Optional[str] = None
    search_query: str = ""
    region: str
    episode_range: str
    """Номер сезона франшизы (1 = первый); шлётся Kotlin-визардом, см. DownloadIpcModels.kt."""
    season_number: int = 1
    quality: str = "720p"
    output_dir: str
    ffmpeg_path: Optional[str] = None
    already_downloaded: list[int] = Field(default_factory=list)
    is_debug: bool = False
    parallel_downloads: int = 2

class EpisodeResult(BaseModel):
    number: int
    status: str
    progress_pct: int = Field(default=0, ge=0, le=100)
    file_path: Optional[str] = None
    m3u8_url: Optional[str] = None
    error: Optional[str] = None

class OutputTask(BaseModel):
    task_id: str
    status: str
    progress_pct: int = Field(ge=0, le=100)
    episodes: list[EpisodeResult]
    summary: dict
    error: Optional[str] = None
