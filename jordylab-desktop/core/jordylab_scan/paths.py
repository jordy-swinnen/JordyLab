"""Per-OS default library root discovery.

These are *probed* defaults: a default location that does not exist is skipped
silently and never causes a partial result (only explicit roots do).
"""

import sys
from pathlib import Path


def home_directory():
    return Path.home()


def steam_candidates():
    """Steam install roots, including the Flatpak location."""
    home = home_directory()
    candidates = [
        home / ".local" / "share" / "Steam",
        home / ".steam" / "steam",
        home / ".steam",
        home / "Steam",
        home / ".var" / "app" / "com.valvesoftware.Steam" / ".local" / "share" / "Steam",
    ]
    if sys.platform == "darwin":
        candidates.insert(0, home / "Library" / "Application Support" / "Steam")

    return candidates


def emudeck_candidates():
    home = home_directory()

    return [home / "Emulation" / "roms"]


def default_roots(library_type):
    """Existing default roots for a library type (missing ones excluded)."""
    if library_type == "STEAM":
        candidates = steam_candidates()
    elif library_type == "EMUDECK":
        candidates = emudeck_candidates()
    else:
        candidates = []

    return [candidate for candidate in candidates if candidate.is_dir()]
