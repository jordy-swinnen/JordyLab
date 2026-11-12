"""Sunshine detection: the games configured in Sunshine on this host.

Sunshine keeps its app list in ``apps.json`` next to its configuration, which
lives in ``~/.config/sunshine/`` on both Linux and macOS (verified against the
LizardByte Sunshine documentation, 2026-11-10). Detection only reads that file;
nothing is executed and no Sunshine process is touched.
"""

import json
from dataclasses import dataclass
from pathlib import Path

APPS_FILE = "apps.json"


def sunshine_config_dir(home=None):
    base = Path(home) if home else Path.home()

    return base / ".config" / "sunshine"


@dataclass(frozen=True)
class SunshineApp:
    name: str
    command: str = ""


def sunshine_apps(config_dir=None):
    """Configured Sunshine applications, empty when absent or unreadable."""
    directory = Path(config_dir) if config_dir else sunshine_config_dir()
    path = directory / APPS_FILE
    if not path.is_file():
        return []
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []
    if not isinstance(raw, list):
        return []

    apps = []
    for entry in raw:
        if not isinstance(entry, dict):
            continue
        name = entry.get("name")
        if not isinstance(name, str) or not name.strip():
            continue
        command = entry.get("cmd")
        apps.append(
            SunshineApp(
                name=name.strip(),
                command=command if isinstance(command, str) else "",
            )
        )

    return apps
