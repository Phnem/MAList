"""Unit-style checks for parse_episode_range and quality_to_format (run under Chaquopy/host Python)."""
from extractors.base import parse_episode_range
from downloader import quality_to_format


def test_comma_list():
    assert parse_episode_range("1,2,3") == [1, 2, 3]


def test_ranges_and_singles():
    assert parse_episode_range("1-3,5,8-10") == [1, 2, 3, 5, 8, 9, 10]


def test_all():
    assert parse_episode_range("all", max_episodes=5) == [1, 2, 3, 4, 5]


def test_quality_map():
    assert "480" in quality_to_format("480p")
    assert "1080" in quality_to_format("1080p")
    assert "720" in quality_to_format("720p")


if __name__ == "__main__":
    test_comma_list()
    test_ranges_and_singles()
    test_all()
    test_quality_map()
    print("ok")
