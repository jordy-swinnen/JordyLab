"""Scan collection: aggregate entries, manifest contents and games across roots.

This is the orchestration the frozen script used to run (spec 003); the desktop
app calls the same rules through ``jordylab_scan`` for its scan payload.
"""

import os
from pathlib import Path
from typing import Dict, List, Tuple

from .grouping import build_emudeck, build_steam
from .manifest import ScanEntry


def _root_usable(root):
    try:
        return Path(root).is_dir() and os.access(str(root), os.R_OK)
    except OSError:
        return False


def _build_for_root(library_type, root):
    if library_type == "STEAM":
        entries, manifest_contents = build_steam([root])

        return entries, manifest_contents, [], bool(entries)
    entries, games = build_emudeck([root])

    return entries, {}, games, bool(games)


def collect_library(library_type, roots, explicit):
    """Aggregate scan inputs across roots.

    Returns ``(entries, manifest_contents, games, skipped_configured)``.
    """
    entries: List[ScanEntry] = []
    seen_entries = set()
    manifest_contents: Dict[str, str] = {}
    games: List[Tuple[str, str, str]] = []
    seen_refs = set()
    skipped_configured: List[str] = []

    for root in roots:
        if not _root_usable(root):
            if explicit:
                skipped_configured.append(str(root))
            continue
        root_entries, root_contents, root_games, has_content = _build_for_root(library_type, root)
        if not has_content:
            if explicit:
                skipped_configured.append(str(root))
            continue
        for entry in root_entries:
            if entry.relpath in seen_entries:
                continue
            seen_entries.add(entry.relpath)
            entries.append(entry)
        manifest_contents.update(root_contents)
        for game in root_games:
            if game[0] in seen_refs:
                continue
            seen_refs.add(game[0])
            games.append(game)

    entries.sort(key=lambda entry: entry.relpath)
    games.sort(key=lambda game: game[0])

    return entries, manifest_contents, games, skipped_configured
