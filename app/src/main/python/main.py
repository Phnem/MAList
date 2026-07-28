import argparse
import json
import os
import sys
import threading
import signal
from pathlib import Path
from pydantic import ValidationError
import logging
import concurrent.futures

from models import InputTask, OutputTask, EpisodeResult
from logging_config import setup_logging
from extractors.ru.anilibria_mock import AnilibriaMock
from downloader import HlsDownloader, DownloadAborted
from utils import atomic_write_json

logger = logging.getLogger(__name__)

# Global state for signal / cooperative cancel
downloader_instance = None
executor_instance = None
shutdown_event = threading.Event()
_progress_callback = None


def handle_signal(sig, frame):
    logger.warning(f"Получен сигнал завершения ({sig}). Останавливаем процессы...")
    shutdown_event.set()
    if downloader_instance:
        downloader_instance.shutdown()
    if executor_instance:
        executor_instance.shutdown(wait=False, cancel_futures=True)
    sys.exit(1)


def cancel_task():
    """Called from Kotlin to cooperatively abort the running download."""
    logger.warning("cancel_task() invoked from Kotlin")
    shutdown_event.set()
    if downloader_instance:
        downloader_instance.shutdown()
    if executor_instance:
        try:
            executor_instance.shutdown(wait=False, cancel_futures=True)
        except TypeError:
            executor_instance.shutdown(wait=False)


def _emit_progress(output_task: OutputTask, output_path: str):
    """Write durability snapshot and push to Kotlin callback if present."""
    payload = output_task.dict()
    atomic_write_json(payload, output_path)
    cb = _progress_callback
    if cb is not None:
        try:
            cb.onProgress(json.dumps(payload, ensure_ascii=False))
        except Exception as e:
            logger.debug(f"Progress callback failed: {e}")


def process_task(input_path: str, progress_callback=None):
    global downloader_instance
    global executor_instance
    global _progress_callback

    _progress_callback = progress_callback
    shutdown_event.clear()

    try:
        with open(input_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except Exception as e:
        print(f"Failed to read input file {input_path}: {e}")
        sys.exit(1)

    try:
        task = InputTask(**data)
    except ValidationError as e:
        print(f"Validation error for input data:\n{e}")
        sys.exit(1)

    setup_logging(is_debug=task.is_debug)
    logger.info(f"Started processing task {task.task_id} for anime {task.anime_id}")

    os.makedirs(task.output_dir, exist_ok=True)

    from extractors.ru.anilibria import AnilibriaExtractor
    from extractors.ru.animego import AnimeGoExtractor
    from extractors.en.animeheaven import AnimeHeavenExtractor
    from extractors.universal.ytdlp_ext import YtDlpExtractor

    if task.is_debug:
        extractor_chain = AnilibriaMock()
    elif task.region.upper() == "RU":
        extractor_chain = AnilibriaExtractor().set_next(AnimeGoExtractor())
    elif task.region.upper() == "EN":
        extractor_chain = AnimeHeavenExtractor()
    else:
        logger.error(f"Unsupported region: {task.region}")
        output_path = str(Path(input_path).parent / "output.json")
        atomic_write_json({
            "task_id": task.task_id,
            "status": "failed",
            "progress_pct": 0,
            "episodes": [],
            "summary": {"total": 0, "done": 0, "failed": 0, "skipped": 0},
            "error": f"Unsupported region: {task.region}"
        }, output_path)
        return

    if not task.is_debug and getattr(task, 'web_url', None):
        current = extractor_chain
        while current._next:
            current = current._next
        current.set_next(YtDlpExtractor())

    extracted_episodes = extractor_chain.handle(task)

    episodes_result = []
    skipped_count = len(task.already_downloaded)

    for ep in task.already_downloaded:
        episodes_result.append(
            EpisodeResult(
                number=ep,
                status="skipped",
                progress_pct=100
            )
        )

    for ep_info in extracted_episodes:
        episodes_result.append(
            EpisodeResult(
                number=ep_info.episode,
                status="downloading",
                progress_pct=0,
                m3u8_url=ep_info.m3u8_url
            )
        )

    output_path = str(Path(input_path).parent / "output.json")

    output_task = OutputTask(
        task_id=task.task_id,
        status="in_progress",
        progress_pct=100 if not extracted_episodes else 0,
        episodes=episodes_result,
        summary={
            "total": len(episodes_result),
            "done": 0,
            "failed": 0,
            "skipped": skipped_count
        },
        error=None
    )

    _emit_progress(output_task, output_path)

    if not extracted_episodes:
        output_task.status = "failed"
        output_task.error = "Парсеры не смогли найти эпизоды по этому названию."
        _emit_progress(output_task, output_path)
        logger.error("No episodes found. Marking task as failed.")
        return

    downloader_instance = HlsDownloader(cancel_event=shutdown_event)

    def on_progress(episode: int, pct: int):
        for ep_res in output_task.episodes:
            if ep_res.number == episode:
                ep_res.progress_pct = pct
                ep_res.status = "downloading"
                break

        total_pct = sum(e.progress_pct for e in output_task.episodes)

        if output_task.episodes:
            output_task.progress_pct = total_pct // len(output_task.episodes)

        _emit_progress(output_task, output_path)

    def download_job(ep_info):
        if shutdown_event.is_set():
            return

        success = False
        ep_output_path = None
        aborted = False
        try:
            ep_output_path = downloader_instance.download_episode(
                ep_info,
                task.output_dir,
                on_progress,
                quality=getattr(task, 'quality', '720p') or '720p',
            )
            success = True
        except DownloadAborted:
            aborted = True
            logger.warning(f"Download aborted for episode {ep_info.episode}")
        except Exception as e:
            logger.error(f"Download failed for episode {ep_info.episode}: {e}")

        for ep_res in output_task.episodes:
            if ep_res.number == ep_info.episode:
                if success:
                    ep_res.status = "success"
                    ep_res.file_path = ep_output_path
                else:
                    ep_res.status = "failed"
                    if aborted or shutdown_event.is_set():
                        ep_res.error = "Aborted by user"
                break

        done = sum(1 for e in output_task.episodes if e.status == "success")
        failed = sum(1 for e in output_task.episodes if e.status == "failed")

        output_task.summary["done"] = done
        output_task.summary["failed"] = failed

        _emit_progress(output_task, output_path)

    logger.info(f"Starting downloads with {task.parallel_downloads} parallel workers")
    executor_instance = concurrent.futures.ThreadPoolExecutor(max_workers=task.parallel_downloads)

    futures = [executor_instance.submit(download_job, ep_info) for ep_info in extracted_episodes]

    try:
        concurrent.futures.wait(futures)
    except KeyboardInterrupt:
        logger.warning("KeyboardInterrupt during wait")
        handle_signal(signal.SIGINT, None)
    finally:
        executor_instance.shutdown(wait=False)

    logger.info("All downloads finished.")

    all_success = all(e.status in ("success", "skipped") for e in output_task.episodes)
    any_success = any(e.status == "success" for e in output_task.episodes)

    if all_success:
        output_task.status = "success"
    elif any_success:
        output_task.status = "partial_success"
    else:
        output_task.status = "failed"

    if shutdown_event.is_set() and not any_success:
        output_task.status = "failed"
        output_task.error = "Task aborted by user"

    _emit_progress(output_task, output_path)
    logger.info(f"Finished processing task. Final status: {output_task.status}")


def run_parser(input_json_path, progress_callback=None):
    """
    Android entry. progress_callback is an optional Java/Kotlin object with
    onProgress(String json) — push updates without polling output.json.
    """
    thread = threading.Thread(
        target=process_task,
        args=(input_json_path, progress_callback),
        daemon=True,
    )
    thread.start()


def main():
    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    parser = argparse.ArgumentParser(description="Parser CLI Utility")
    parser.add_argument("input_file", help="Path to input.json")
    args = parser.parse_args()

    if not os.path.exists(args.input_file):
        print(f"Error: File {args.input_file} does not exist.")
        sys.exit(1)

    process_task(args.input_file)


if __name__ == "__main__":
    main()
