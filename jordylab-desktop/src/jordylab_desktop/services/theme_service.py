"""Theme preference: the account-wide choice shared with the web app.

The preference is stored on the account (``identity`` module) and read by both
surfaces. The desktop app reads it with its machine token through
``GET /api/identity/machines/preferences`` (the machine token's ``created_by``
identifies the account), caches the last known value locally, and renders it as
one of the six JordyLab themes.
"""

import json
import urllib.request
from pathlib import Path

from . import config_service

THEMES = ("content", "oxblood", "flare", "paper", "glasshouse", "heather")
FOLLOW_DEVICE = "follow-device"
DEFAULT_THEME = "content"


def normalize_theme(value):
    if isinstance(value, str) and value in THEMES:
        return value
    if value == FOLLOW_DEVICE:
        return FOLLOW_DEVICE

    return FOLLOW_DEVICE


def resolve_theme(preference, device_is_dark):
    """Concrete theme for rendering; ``follow-device`` picks light/dark."""
    normalized = normalize_theme(preference)
    if normalized != FOLLOW_DEVICE:
        return normalized

    return "content" if device_is_dark else "paper"


def fetch_account_theme(backend_url, token, opener=None):
    """The account's theme, or None when it cannot be read (offline)."""
    open_url = opener or urllib.request.urlopen
    request = urllib.request.Request(
        backend_url.rstrip("/") + "/api/identity/machines/preferences",
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
    )
    try:
        with open_url(request, timeout=20) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except (OSError, ValueError, urllib.error.URLError):
        return None
    theme = payload.get("theme") if isinstance(payload, dict) else None

    return normalize_theme(theme) if isinstance(theme, str) else None


def cache_file():
    return config_service.config_dir() / "theme.json"


def load_cached_theme():
    path = cache_file()
    if not path.is_file():
        return None
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None

    return normalize_theme(payload.get("theme")) if isinstance(payload, dict) else None


def save_cached_theme(theme):
    path = cache_file()
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    path.write_text(json.dumps({"theme": normalize_theme(theme)}) + "\n", encoding="utf-8")

    return path


def current_theme(config, token, device_is_dark=True, opener=None):
    """Fetch, cache and return the concrete theme id for this launch."""
    preference = fetch_account_theme(config.backend_url, token, opener=opener)
    if preference is None:
        preference = load_cached_theme() or FOLLOW_DEVICE
    else:
        save_cached_theme(preference)

    return resolve_theme(preference, device_is_dark)


def device_is_dark():
    """Best-effort OS dark-mode detection for ``follow-device``."""
    import subprocess
    import sys

    if sys.platform == "darwin":
        try:
            result = subprocess.run(
                ["defaults", "read", "-g", "AppleInterfaceStyle"],
                capture_output=True, text=True, check=False,
            )
            return "dark" in (result.stdout or "").lower()
        except OSError:
            return True
    if sys.platform.startswith("win"):
        return True

    return True
