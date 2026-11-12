"""Config service tests."""

import json
import os

from jordylab_desktop.services import config_service


def test_defaults_when_missing(tmp_path, monkeypatch):
    monkeypatch.setenv("JORDYLAB_DESKTOP_CONFIG_DIR", str(tmp_path))
    config = config_service.load()

    assert config.backend_url == "https://jordylab.be"
    assert config.schedule_hours == 6
    assert config.roots_for("STEAM") == []


def test_round_trip(tmp_path, monkeypatch):
    monkeypatch.setenv("JORDYLAB_DESKTOP_CONFIG_DIR", str(tmp_path))
    config = config_service.DesktopConfig(
        backend_url="http://localhost:8080",
        schedule_hours=12,
        extra_roots={"STEAM": ["/mnt/steam"]},
    )
    path = config_service.save(config)
    assert json.loads(path.read_text())["scheduleHours"] == 12

    loaded = config_service.load()
    assert loaded.backend_url == "http://localhost:8080"
    assert loaded.schedule_hours == 12
    assert loaded.roots_for("STEAM") == ["/mnt/steam"]


def test_invalid_file_falls_back(tmp_path, monkeypatch):
    monkeypatch.setenv("JORDYLAB_DESKTOP_CONFIG_DIR", str(tmp_path))
    (tmp_path / "config.json").write_text("{not json", encoding="utf-8")

    config = config_service.load()
    assert config.schedule_hours == 6


def test_save_is_owner_only(tmp_path, monkeypatch):
    monkeypatch.setenv("JORDYLAB_DESKTOP_CONFIG_DIR", str(tmp_path))
    path = config_service.save(config_service.DesktopConfig())

    assert oct(os.stat(path).st_mode & 0o777) == oct(0o600)
