"""App-level configuration for the desktop sync app.

Separate from the scanner's ``config.json`` (which lives in
``~/.config/jordylab/scan`` and holds library roots). This file holds the app's
own settings: backend URL, schedule interval, extra roots carried over from the
script era, and window state. Override the directory with
``JORDYLAB_DESKTOP_CONFIG_DIR`` (tests use this).
"""

import json
import os
from dataclasses import dataclass, field
from pathlib import Path

DEFAULT_BACKEND_URL = "https://jordylab.be"
DEFAULT_SCHEDULE_HOURS = 6
DEFAULT_LIBRARY_TYPES = ("STEAM", "EMUDECK")


def config_dir():
    override = os.environ.get("JORDYLAB_DESKTOP_CONFIG_DIR")
    if override:
        return Path(override)

    return Path.home() / ".config" / "jordylab" / "desktop"


def config_file():
    return config_dir() / "config.json"


@dataclass
class DesktopConfig:
    backend_url: str = DEFAULT_BACKEND_URL
    schedule_hours: int = DEFAULT_SCHEDULE_HOURS
    extra_roots: dict = field(default_factory=dict)
    window_width: int = 440
    window_height: int = 640

    def roots_for(self, library_type):
        values = self.extra_roots.get(library_type, [])

        return [str(value) for value in values if isinstance(value, str) and value]


def _from_raw(raw):
    config = DesktopConfig()
    if not isinstance(raw, dict):
        return config
    backend_url = raw.get("backendUrl")
    if isinstance(backend_url, str) and backend_url:
        config.backend_url = backend_url
    schedule_hours = raw.get("scheduleHours")
    if isinstance(schedule_hours, int) and schedule_hours > 0:
        config.schedule_hours = schedule_hours
    extra_roots = raw.get("extraRoots")
    if isinstance(extra_roots, dict):
        config.extra_roots = {
            str(key): [str(value) for value in values if isinstance(value, str)]
            for key, values in extra_roots.items()
            if isinstance(values, list)
        }
    window = raw.get("window")
    if isinstance(window, dict):
        if isinstance(window.get("width"), int) and window["width"] >= 360:
            config.window_width = window["width"]
        if isinstance(window.get("height"), int) and window["height"] >= 480:
            config.window_height = window["height"]

    return config


def load(path=None):
    path = Path(path) if path else config_file()
    if not path.is_file():
        return DesktopConfig()
    try:
        with open(path, encoding="utf-8") as handle:
            raw = json.load(handle)
    except (OSError, ValueError):
        return DesktopConfig()

    return _from_raw(raw)


def save(config, path=None):
    path = Path(path) if path else config_file()
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    raw = {
        "backendUrl": config.backend_url,
        "scheduleHours": config.schedule_hours,
        "extraRoots": config.extra_roots,
        "window": {"width": config.window_width, "height": config.window_height},
    }
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(raw, indent=2) + "\n", encoding="utf-8")
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)

    return path
