"""Theme service tests: normalization, follow-device, caching, fetch."""

import io
import json

from jordylab_desktop.services import theme_service


def test_normalize():
    assert theme_service.normalize_theme("heather") == "heather"
    assert theme_service.normalize_theme("follow-device") == "follow-device"
    assert theme_service.normalize_theme("nope") == "follow-device"
    assert theme_service.normalize_theme(None) == "follow-device"


def test_resolve_follow_device():
    assert theme_service.resolve_theme("follow-device", True) == "content"
    assert theme_service.resolve_theme("follow-device", False) == "paper"
    assert theme_service.resolve_theme("oxblood", True) == "oxblood"


def test_fetch_theme():
    def opener(_request, timeout=None):
        return io.BytesIO(json.dumps({"theme": "flare"}).encode("utf-8"))

    assert theme_service.fetch_account_theme("https://x", "jlmt_t", opener=opener) == "flare"


def test_fetch_theme_offline_returns_none():
    def opener(_request, timeout=None):
        raise OSError("offline")

    assert theme_service.fetch_account_theme("https://x", "jlmt_t", opener=opener) is None


def test_cache_round_trip(tmp_path, monkeypatch):
    monkeypatch.setenv("JORDYLAB_DESKTOP_CONFIG_DIR", str(tmp_path))
    theme_service.save_cached_theme("glasshouse")
    assert theme_service.load_cached_theme() == "glasshouse"
