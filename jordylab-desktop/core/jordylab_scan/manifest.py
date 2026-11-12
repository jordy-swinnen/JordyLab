"""Symlink-safe directory walk and the canonical metadata fingerprint.

The fingerprint is derived from file metadata only (path, size, mtime_ns) —
file contents are never read to decide whether a scan is needed. Paths are
POSIX-style and NFC-normalised so that one file is one entry regardless of the
Unicode normalisation the filesystem happens to use.
"""

import hashlib
import json
import os
import unicodedata
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List


@dataclass
class ScanEntry:
    relpath: str
    size: int
    mtime_ns: int


def nfc(value):
    if value is None:
        return None

    return unicodedata.normalize("NFC", value)


def to_posix(path):
    return str(path).replace(os.sep, "/")


def canonicalize_roots(roots):
    """Drop roots that resolve (realpath) to a location already seen."""
    seen = set()
    result = []
    for root in roots:
        try:
            resolved = os.path.realpath(str(root))
        except OSError:
            continue
        if resolved in seen:
            continue
        seen.add(resolved)
        result.append(Path(root))

    return result


def walk_entries(root, include=None):
    """Walk ``root`` following symlinks exactly once, returning entries.

    ``include`` is an optional predicate taking a relpath; when it returns
    false the file is skipped.
    """
    root_path = Path(root)
    visited = set()
    entries = []

    for dirpath, dirnames, filenames in os.walk(str(root_path), followlinks=True):
        resolved_dir = os.path.realpath(dirpath)
        if resolved_dir in visited:
            dirnames[:] = []
            continue
        visited.add(resolved_dir)

        kept = []
        for dirname in sorted(dirnames):
            child = os.path.join(dirpath, dirname)
            if os.path.realpath(child) in visited:
                continue
            kept.append(dirname)
        dirnames[:] = kept

        for filename in sorted(filenames):
            full = os.path.join(dirpath, filename)
            try:
                stat_result = os.stat(full)
            except OSError:
                continue
            if not os.path.isfile(full):
                continue
            relative = to_posix(os.path.relpath(full, str(root_path)))
            relative = nfc(relative)
            if include is not None and not include(relative):
                continue
            entries.append(
                ScanEntry(relative, int(stat_result.st_size), int(stat_result.st_mtime_ns))
            )

    entries.sort(key=lambda entry: entry.relpath)

    return entries


def read_text(path, encoding="utf-8"):
    try:
        with open(str(path), encoding=encoding, errors="replace") as handle:
            return handle.read()
    except OSError:
        return None


def canonical_json(entries, manifest_contents=None, games=None):
    """The exact canonical form that is hashed (see data-model.md)."""
    paths = sorted(
        [[entry.relpath, entry.size, entry.mtime_ns] for entry in entries],
        key=lambda row: row[0],
    )
    game_rows = sorted(
        [[game[0], game[1], game[2]] for game in (games or [])],
        key=lambda row: row[0],
    )
    document = {
        "paths": paths,
        "manifest_contents": dict(manifest_contents or {}),
        "games": game_rows,
    }

    return json.dumps(document, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def compute_digest(entries, manifest_contents=None, games=None):
    canonical = canonical_json(entries, manifest_contents, games)
    hexdigest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    return "sha256:" + hexdigest


def collect_manifest_contents(root, entries, predicate):
    """Read the raw text of entries matching ``predicate`` into a dict."""
    contents: Dict[str, str] = {}
    root_path = Path(root)
    for entry in entries:
        if not predicate(entry.relpath):
            continue
        text = read_text(root_path / entry.relpath)
        if text is not None:
            contents[entry.relpath] = text

    return contents


def merge_entries(target: List[ScanEntry], additions: Sequence[ScanEntry]):
    seen = {entry.relpath for entry in target}
    for entry in additions:
        if entry.relpath in seen:
            continue
        seen.add(entry.relpath)
        target.append(entry)
    target.sort(key=lambda entry: entry.relpath)

    return target
