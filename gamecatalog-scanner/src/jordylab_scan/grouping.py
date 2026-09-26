"""Library grouping: Steam multi-library discovery and EmuDeck multi-file sets.

A minimal Valve KeyValues (VDF) grammar is ported from the server's
``VdfParser`` so that ``libraryfolders.vdf`` can be parsed without a dependency.
Steam ``externalRef`` values remain the bare appid (parsed server-side), while
the client emits namespaced ``libraryfolders/<n>/steamapps/appmanifest_<appid>.acf``
paths that still satisfy the server's manifest regex.

EmuDeck grouping collapses ``.m3u`` playlists, ``.cue``/``.gdi`` disc images and
their referenced components, standalone ``.chd`` files, and disc-numbered sets
into a single game anchored to an existing identity. Referenced components are
never emitted as standalone games.
"""

import os
import re
from pathlib import Path
from typing import Dict, List, Tuple

from .manifest import ScanEntry, nfc, read_text, to_posix, walk_entries
from .normalize import extension, parent_directory, sanitize_title, title_from_filename
from .platforms import platform_for

STEAM_MANIFEST_REGEX = re.compile(r".*steamapps/appmanifest_(\d+)\.acf$")

LIBRARY_FOLDERS_FILENAME = "libraryfolders.vdf"

ROM_EXTENSIONS = {
    ".smc",
    ".sfc",
    ".iso",
    ".bin",
    ".cue",
    ".m3u",
    ".nes",
    ".gb",
    ".gbc",
    ".gba",
    ".nds",
    ".3ds",
    ".n64",
    ".z64",
    ".v64",
    ".gcm",
    ".ciso",
    ".chd",
    ".pbp",
    ".ecm",
    ".mds",
    ".gdi",
}

PLAYLIST_EXTENSIONS = {".m3u"}
DISC_IMAGE_EXTENSIONS = {".cue", ".gdi"}
COMPONENT_EXTENSIONS = {".bin", ".img", ".iso", ".chd"}

_DISC_PATTERN = re.compile(r"[\(\[]?\s*(?:disc|disk|cd|part)\s*(\d+)\s*[\)\]]?", re.IGNORECASE)
_CUE_FILE_PATTERN = re.compile(r"FILE\s+(?:\"([^\"]+)\"|(\S+))", re.IGNORECASE)
_QUOTED_PATTERN = re.compile(r"\"([^\"]+)\"")
_GDI_BARE_PATTERN = re.compile(
    r"""(?<![\"\w])([^\s"']+\.(?:bin|img|iso|chd|raw|wav|mp3))""", re.IGNORECASE
)


class _VdfParser:
    """Faithful port of the server's minimal VDF grammar."""

    def __init__(self, text):
        self.text = text or ""
        self.index = 0

    def parse(self):
        if not self.text or not self.text.strip():
            return {}
        root: Dict[str, object] = {}
        self.skip_whitespace_and_comments()
        while self.has_more():
            key = self.read_key()
            if key is None:
                break
            self.skip_whitespace_and_comments()
            root[key] = self.read_value()
            self.skip_whitespace_and_comments()

        return root

    def has_more(self):
        return self.index < len(self.text)

    def skip_whitespace_and_comments(self):
        while self.index < len(self.text):
            char = self.text[self.index]
            if char.isspace():
                self.index += 1
            elif (
                char == "/" and self.index + 1 < len(self.text) and self.text[self.index + 1] == "/"
            ):
                while self.index < len(self.text) and self.text[self.index] != "\n":
                    self.index += 1
            else:
                break

    def read_key(self):
        if self.index >= len(self.text):
            return None
        first = self.text[self.index]
        if first == '"':
            return self.read_quoted()
        if first in "{}":
            return None
        start = self.index
        while self.index < len(self.text):
            char = self.text[self.index]
            if char.isspace() or char in '{"':
                break
            self.index += 1

        return self.text[start : self.index]

    def read_quoted(self):
        self.index += 1
        collected = []
        while self.index < len(self.text) and self.text[self.index] != '"':
            collected.append(self.text[self.index])
            self.index += 1
        if self.index < len(self.text):
            self.index += 1

        return "".join(collected)

    def read_value(self):
        if self.index >= len(self.text):
            return ""
        char = self.text[self.index]
        if char == '"':
            return self.read_quoted()
        if char == "{":
            self.index += 1
            return self.read_object()
        start = self.index
        while self.index < len(self.text) and not self.text[self.index].isspace():
            self.index += 1

        return self.text[start : self.index]

    def read_object(self):
        result: Dict[str, object] = {}
        while self.index < len(self.text):
            self.skip_whitespace_and_comments()
            if self.index >= len(self.text):
                break
            if self.text[self.index] == "}":
                self.index += 1
                return result
            key = self.read_key()
            if key is None:
                break
            self.skip_whitespace_and_comments()
            result[key] = self.read_value()

        return result


def parse_vdf(text):
    return _VdfParser(text).parse()


def nested_string(root, default, *keys):
    current = root
    for key in keys:
        if not isinstance(current, dict):
            return default
        current = current.get(key)
    if current is None:
        return default

    return str(current)


def _library_folders(text, steam_root):
    if not text:
        return [("0", Path(steam_root))]
    parsed = parse_vdf(text)
    if isinstance(parsed.get("libraryfolders"), dict):
        parsed = parsed["libraryfolders"]
    folders = []
    if isinstance(parsed, dict):
        for key in parsed:
            if not str(key).isdigit():
                continue
            value = parsed[key]
            if isinstance(value, str):
                folders.append((str(key), Path(value)))
            elif isinstance(value, dict) and isinstance(value.get("path"), str):
                folders.append((str(key), Path(value["path"])))
    if not folders:
        return [("0", Path(steam_root))]

    return folders


def build_steam(roots):
    """Return ``(entries, manifest_contents)`` for all Steam libraries."""
    entries = []
    manifest_contents: Dict[str, str] = {}
    seen_appids = set()

    for root in roots:
        steamapps = Path(root) / "steamapps"
        if not steamapps.is_dir():
            continue
        vdf_text = read_text(steamapps / LIBRARY_FOLDERS_FILENAME)
        for index, folder in _library_folders(vdf_text, root):
            apps_dir = Path(folder) / "steamapps"
            if not apps_dir.is_dir():
                continue
            try:
                names = sorted(
                    child.name for child in apps_dir.glob("appmanifest_*.acf") if child.is_file()
                )
            except OSError:
                continue
            for name in names:
                match = re.match(r"appmanifest_(\d+)\.acf$", name)
                if not match:
                    continue
                appid = match.group(1)
                if appid in seen_appids:
                    continue
                relpath = f"libraryfolders/{index}/steamapps/{name}"
                if not STEAM_MANIFEST_REGEX.match(relpath):
                    continue
                manifest_path = apps_dir / name
                try:
                    stat_result = os.stat(str(manifest_path))
                except OSError:
                    continue
                seen_appids.add(appid)
                entries.append(
                    ScanEntry(nfc(relpath), int(stat_result.st_size), int(stat_result.st_mtime_ns))
                )
                text = read_text(manifest_path)
                if text is not None:
                    manifest_contents[nfc(relpath)] = text

    entries.sort(key=lambda entry: entry.relpath)

    return entries, manifest_contents


def _resolve_reference(base_dir, reference):
    if not reference:
        return None
    cleaned = reference.strip().replace("\\", "/")
    if not cleaned or cleaned.startswith("/"):
        return None
    if len(cleaned) > 1 and cleaned[1] == ":":
        return None
    combined = (
        os.path.normpath(os.path.join(base_dir, cleaned)) if base_dir else os.path.normpath(cleaned)
    )

    return nfc(combined.replace(os.sep, "/"))


def _referenced_from_playlist(text, base_dir):
    references = []
    for line in (text or "").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        resolved = _resolve_reference(base_dir, stripped)
        if resolved:
            references.append(resolved)

    return references


def _referenced_from_disc_image(text):
    references = []
    for match in _CUE_FILE_PATTERN.finditer(text or ""):
        references.append(match.group(1) or match.group(2))
    for quoted in _QUOTED_PATTERN.findall(text or ""):
        references.append(quoted)
    for bare in _GDI_BARE_PATTERN.findall(text or ""):
        references.append(bare)

    return references


def _disc_info(basename_without_extension):
    match = _DISC_PATTERN.search(basename_without_extension)
    if not match:
        return None
    number = int(match.group(1))
    base = basename_without_extension[: match.start()] + basename_without_extension[match.end() :]
    base = re.sub(r"[\s_\-.]+", " ", base).strip().lower()

    return number, base


def _game_tuple(relpath):
    parent = parent_directory(relpath)
    if not parent:
        return None
    title = sanitize_title(title_from_filename(relpath))
    platform = platform_for(parent)

    return (relpath, title, platform)


def _walk_with_absolute(root):
    entries = walk_entries(root)
    absolute = {}
    root_path = Path(root)
    for entry in entries:
        absolute[entry.relpath] = root_path / entry.relpath

    return entries, absolute


def build_emudeck(roots):
    """Return ``(entries, games)`` for all EmuDeck ROM roots.

    ``entries`` are all regular files across the roots (deduped by relpath);
    ``games`` are ``(external_ref, title, platform)`` tuples.
    """
    entries = []
    absolute: Dict[str, Path] = {}
    seen_relpaths = set()
    for root in roots:
        root_entries, root_absolute = _walk_with_absolute(root)
        for entry in root_entries:
            if entry.relpath in seen_relpaths:
                continue
            seen_relpaths.add(entry.relpath)
            entries.append(entry)
            absolute[entry.relpath] = root_absolute[entry.relpath]
    entries.sort(key=lambda entry: entry.relpath)
    by_relpath = {entry.relpath: entry for entry in entries}

    consumed = set()

    for entry in entries:
        if extension(entry.relpath) not in PLAYLIST_EXTENSIONS:
            continue
        base_dir = to_posix(os.path.dirname(entry.relpath))
        text = read_text(absolute[entry.relpath])
        for reference in _referenced_from_playlist(text, base_dir):
            if reference in by_relpath:
                consumed.add(reference)

    for entry in entries:
        if extension(entry.relpath) not in DISC_IMAGE_EXTENSIONS:
            continue
        if entry.relpath in consumed:
            continue
        base_dir = to_posix(os.path.dirname(entry.relpath))
        text = read_text(absolute[entry.relpath])
        for reference in _referenced_from_disc_image(text):
            resolved = _resolve_reference(base_dir, reference)
            if resolved and resolved in by_relpath:
                consumed.add(resolved)

    disc_groups: Dict[Tuple[str, str, str], List[Tuple[int, str]]] = {}
    for entry in entries:
        file_extension = extension(entry.relpath)
        if file_extension not in ROM_EXTENSIONS or entry.relpath in consumed:
            continue
        basename = os.path.basename(entry.relpath)
        stem = basename[: -len(file_extension)] if file_extension else basename
        info = _disc_info(stem)
        if info is None:
            continue
        number, base = info
        key = (parent_directory(entry.relpath) or "", base, file_extension)
        disc_groups.setdefault(key, []).append((number, entry.relpath))

    for members in disc_groups.values():
        if len(members) < 2:
            continue
        members.sort(key=lambda item: (item[0], item[1]))
        for _, relpath in members[1:]:
            consumed.add(relpath)

    games = []
    for entry in entries:
        if extension(entry.relpath) not in ROM_EXTENSIONS:
            continue
        if entry.relpath in consumed:
            continue
        game = _game_tuple(entry.relpath)
        if game is not None:
            games.append(game)

    games.sort(key=lambda game: game[0])

    return entries, games
