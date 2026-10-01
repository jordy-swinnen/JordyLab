# Rendered by jordylab-be ClientService. Do not edit by hand.
KEYCLOAK_URL = "${KEYCLOAK_URL}"
REALM = "${REALM}"
CLIENT_ID = "${CLIENT_ID}"
BACKEND_URL = "${BACKEND_URL}"
SCAN_ENDPOINT = "${SCAN_ENDPOINT}"
LIBRARY_TYPE = "${LIBRARY_TYPE}"


"""JordyLab game catalog scan client (standard library only)."""

__version__ = "0.1.0"

"""Filename normalization rules.

Verbatim port of the server's ``EmuDeckLibraryParser`` extension/title rules and
``TextSanitizer.sanitizeTitle`` so that a game keeps the same identity, title,
and platform the server would have produced. Do not "improve" these rules:
identity continuity depends on them matching exactly.
"""

import re

_SCRIPT_STYLE_PATTERN = re.compile(r"<(script|style)[^>]*>.*?</\1>", re.IGNORECASE | re.DOTALL)
_MARKUP_PATTERN = re.compile(r"<[^>]*>")
# Java: [\p{Cntrl}&&[^\t]] — control characters except tab.
_CONTROL_PATTERN = re.compile(r"[\x00-\x08\x0a-\x1f\x7f]")
_WHITESPACE_PATTERN = re.compile(r"\s+")
_PAREN_GROUP_PATTERN = re.compile(r"\(.*?\)")


def extension(relpath):
    """Lower-cased extension including the dot, or ``None`` when absent."""
    slash = relpath.rfind("/")
    basename = relpath if slash < 0 else relpath[slash + 1 :]
    dot = basename.rfind(".")
    if dot <= 0:
        return None

    return basename[dot:].lower()


def parent_directory(relpath):
    """The immediate parent directory name, or ``None`` when there is none."""
    slash = relpath.rfind("/")
    if slash < 0:
        return None
    previous = relpath.rfind("/", 0, slash)
    if previous < 0:
        return relpath[:slash]

    return relpath[previous + 1 : slash]


def title_from_filename(relpath):
    """Port of ``EmuDeckLibraryParser.titleFromFilename`` (verbatim rules)."""
    slash = relpath.rfind("/")
    basename = relpath if slash < 0 else relpath[slash + 1 :]
    dot = basename.rfind(".")
    if dot > 0:
        basename = basename[:dot]
    basename = basename.replace("_", " ")
    basename = basename.replace("[", "(").replace("]", ")")
    basename = _PAREN_GROUP_PATTERN.sub("", basename)
    basename = _WHITESPACE_PATTERN.sub(" ", basename)

    return basename.strip()


def sanitize_title(raw_title):
    """Port of ``TextSanitizer.sanitizeTitle``."""
    if raw_title is None:
        return None
    cleaned = _SCRIPT_STYLE_PATTERN.sub("", raw_title)
    cleaned = _MARKUP_PATTERN.sub("", cleaned)
    cleaned = _CONTROL_PATTERN.sub("", cleaned)
    cleaned = _WHITESPACE_PATTERN.sub(" ", cleaned)

    return cleaned.strip()

"""EmuDeck emulator-subfolder to platform-label mapping.

Verbatim port of ``EMUDECK_PLATFORM_LOOKUPS`` from the server's
``EmuDeckLibraryParser`` plus the capitalise-unknown fallback.
"""

EMUDECK_PLATFORM_LOOKUPS = {
    "snes": "SNES",
    "nes": "NES",
    "n64": "Nintendo 64",
    "gb": "Game Boy",
    "gbc": "Game Boy Color",
    "gba": "Game Boy Advance",
    "nds": "Nintendo DS",
    "3ds": "Nintendo 3DS",
    "gamecube": "GameCube",
    "gc": "GameCube",
    "wii": "Wii",
    "ps1": "PlayStation",
    "psx": "PlayStation",
    "psp": "PSP",
    "ps2": "PlayStation 2",
    "dreamcast": "Dreamcast",
    "dc": "Dreamcast",
    "saturn": "Saturn",
    "genesis": "Genesis",
    "megadrive": "Genesis",
    "sega32x": "Sega 32X",
    "gamegear": "Game Gear",
    "atari2600": "Atari 2600",
    "msu1": "SNES MSU-1",
}


def platform_for(emulator):
    """Display label for an EmuDeck subfolder name.

    Unmapped subfolders fall back to a capitalised version of the name so a
    non-standard layout still yields a readable label.
    """
    if not emulator:
        return emulator
    lowered = emulator.lower()
    mapped = EMUDECK_PLATFORM_LOOKUPS.get(lowered)
    if mapped is not None:
        return mapped

    return lowered[0].upper() + lowered[1:]

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

"""Client configuration and local state locations.

Service endpoints normally arrive from the constants header rendered into the
frozen client. In package/dev mode they can be supplied by environment
variables or a ``service`` block in ``config.json``. The only user-facing
config file content is an optional per-library ``roots`` map.
"""

import json
import os
from dataclasses import dataclass
from pathlib import Path

DEFAULT_KEYCLOAK_URL = "http://localhost:8180"
DEFAULT_REALM = "jordylab"
DEFAULT_CLIENT_ID = "gamecatalog-script"
DEFAULT_BACKEND_URL = "http://localhost:8080"
DEFAULT_SCAN_ENDPOINT = "/api/gamecatalog/ingest/scan"

_ENV_KEYS = {
    "keycloak_url": "JORDYLAB_SCAN_KEYCLOAK_URL",
    "realm": "JORDYLAB_SCAN_REALM",
    "client_id": "JORDYLAB_SCAN_CLIENT_ID",
    "backend_url": "JORDYLAB_SCAN_BACKEND_URL",
    "scan_endpoint": "JORDYLAB_SCAN_ENDPOINT",
}

SUPPORTED_LIBRARY_TYPES = ("STEAM", "EMUDECK")

# Built at runtime so the frozen artifact contains the placeholder marker only
# inside its rendered constants header.
PLACEHOLDER_MARK = "$" + "{"


@dataclass
class AppConfig:
    library_type: str
    keycloak_url: str = DEFAULT_KEYCLOAK_URL
    realm: str = DEFAULT_REALM
    client_id: str = DEFAULT_CLIENT_ID
    backend_url: str = DEFAULT_BACKEND_URL
    scan_endpoint: str = DEFAULT_SCAN_ENDPOINT

    def check_endpoint(self):
        base = self.scan_endpoint.rsplit("/", 1)[0]

        return base + "/check"


def config_dir():
    override = os.environ.get("JORDYLAB_SCAN_CONFIG_DIR")
    if override:
        return Path(override)

    return Path.home() / ".config" / "jordylab" / "scan"


def state_dir():
    override = os.environ.get("JORDYLAB_SCAN_STATE_DIR")
    if override:
        return Path(override)

    return Path.home() / ".local" / "state" / "jordylab-scan"


def config_file():
    return config_dir() / "config.json"


def token_file():
    return config_dir() / "token.json"


def machine_id_file():
    return config_dir() / "machine-id"


def service_config_file():
    return config_dir() / "service.json"


def load_config_json(path=None):
    path = Path(path) if path else config_file()
    if not path.is_file():
        return {}
    try:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return {}
    if isinstance(data, dict):
        return data

    return {}


def configured_roots(library_type, path=None):
    """Explicit roots from ``config.json`` for a library type (may be empty)."""
    data = load_config_json(path)
    roots = data.get("roots")
    if not isinstance(roots, dict):
        return []
    values = roots.get(library_type)
    if not isinstance(values, list):
        return []

    return [str(value) for value in values if isinstance(value, str) and value]


def _clean(value):
    if not value:
        return None
    if PLACEHOLDER_MARK in str(value):
        return None

    return str(value)


def resolve_service(library_type, constants=None, config_data=None, env=None):
    """Build an :class:`AppConfig`.

    Precedence: rendered constants > ``service`` block in config.json >
    environment variables > built-in defaults.
    """
    constants = constants or {}
    config_data = config_data or {}
    env = env if env is not None else os.environ
    service = config_data.get("service") if isinstance(config_data.get("service"), dict) else {}

    def pick(constant_key, config_key, env_key, default):
        for candidate in (
            _clean(constants.get(constant_key)),
            _clean(service.get(config_key)),
            _clean(env.get(env_key)),
        ):
            if candidate is not None:
                return candidate

        return default

    return AppConfig(
        library_type=library_type,
        keycloak_url=pick(
            "KEYCLOAK_URL", "keycloak_url", _ENV_KEYS["keycloak_url"], DEFAULT_KEYCLOAK_URL
        ),
        realm=pick("REALM", "realm", _ENV_KEYS["realm"], DEFAULT_REALM),
        client_id=pick("CLIENT_ID", "client_id", _ENV_KEYS["client_id"], DEFAULT_CLIENT_ID),
        backend_url=pick(
            "BACKEND_URL", "backend_url", _ENV_KEYS["backend_url"], DEFAULT_BACKEND_URL
        ),
        scan_endpoint=pick(
            "SCAN_ENDPOINT", "scan_endpoint", _ENV_KEYS["scan_endpoint"], DEFAULT_SCAN_ENDPOINT
        ),
    )


def normalize_library_type(value):
    """Upper-case and validate a library type, raising ``ValueError`` if bad."""
    if value is None:
        raise ValueError("library type is required")
    normalized = str(value).strip().upper()
    if normalized not in SUPPORTED_LIBRARY_TYPES:
        raise ValueError("library type must be one of " + ", ".join(SUPPORTED_LIBRARY_TYPES))

    return normalized


def ensure_private_dir(path=None):
    target = Path(path) if path else config_dir()
    target.mkdir(mode=0o700, parents=True, exist_ok=True)

    return target


def read_machine_id():
    path = machine_id_file()
    if not path.is_file():
        return None
    try:
        value = path.read_text(encoding="utf-8").strip()
    except OSError:
        return None

    return value or None


def write_machine_id(machine_id):
    import tempfile

    directory = ensure_private_dir()
    handle, temp_path = tempfile.mkstemp(dir=str(directory), prefix=".machine-id-")
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as stream:
            stream.write(machine_id)
        os.chmod(temp_path, 0o600)
        os.replace(temp_path, str(directory / "machine-id"))
    except OSError:
        try:
            os.unlink(temp_path)
        except OSError:
            pass
        raise

    return machine_id_file()


def ensure_machine_id(machine_id_factory=None):
    """Return the stable machine id, generating one if absent."""
    existing = read_machine_id()
    if existing:
        return existing
    if machine_id_factory is None:
        import uuid

        machine_id_factory = lambda: str(uuid.uuid4())  # noqa: E731
    machine_id = machine_id_factory()
    write_machine_id(machine_id)

    return machine_id

"""Cross-process advisory lock.

``fcntl.flock`` on POSIX, ``msvcrt.locking`` on Windows. Used around token
refresh and around the whole scan run so two overlapping runs cannot corrupt
the cached session or race each other.
"""

import errno
import os
import time
from contextlib import contextmanager

try:  # pragma: no cover - platform specific
    import fcntl
except ImportError:  # pragma: no cover - Windows
    fcntl = None

try:  # pragma: no cover - platform specific
    import msvcrt
except ImportError:  # pragma: no cover - POSIX
    msvcrt = None


class LockTimeout(Exception):
    """Raised when the lock could not be acquired within the timeout."""


def _acquire_posix(handle):
    fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)


def _release_posix(handle):
    fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def _acquire_windows(handle):  # pragma: no cover - Windows only
    handle.seek(0)
    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)


def _release_windows(handle):  # pragma: no cover - Windows only
    handle.seek(0)
    msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)


def _try_acquire(handle):
    if fcntl is not None:
        _acquire_posix(handle)
    elif msvcrt is not None:  # pragma: no cover - Windows only
        _acquire_windows(handle)
    else:  # pragma: no cover - no locking primitive
        raise LockTimeout("no file-locking primitive available on this platform")


def _release(handle):
    if fcntl is not None:
        _release_posix(handle)
    elif msvcrt is not None:  # pragma: no cover - Windows only
        _release_windows(handle)


@contextmanager
def file_lock(path, timeout=0.0, poll_interval=0.1):
    """Hold an exclusive lock on ``path`` for the duration of the block."""
    directory = os.path.dirname(str(path))
    if directory:
        os.makedirs(directory, exist_ok=True)
    handle = open(path, "a+")
    deadline = time.monotonic() + max(0.0, timeout)
    try:
        while True:
            try:
                _try_acquire(handle)
                break
            except OSError as error:
                if error.errno not in (errno.EACCES, errno.EAGAIN):
                    raise
                if time.monotonic() >= deadline:
                    raise LockTimeout(f"could not acquire lock on {path}") from error
                time.sleep(poll_interval)
        yield handle
    finally:
        try:
            _release(handle)
        except OSError:
            pass
        handle.close()

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

"""Scan request body construction and the canonical client fingerprint."""

import datetime


# The estimate is ~100 bytes per path row, so 8 MiB carries ~80,000 files. The server allows 50,000 games per source;
# a source with many multi-file games (bin/cue, m3u) can exceed 8 MiB first — the clear error then says so.
MAX_PAYLOAD_BYTES = 8 * 1024 * 1024


class PayloadTooLarge(Exception):
    """Raised when the upload body would exceed the server's byte cap."""


def iso8601_from_ns(mtime_ns):
    seconds = mtime_ns / 1_000_000_000
    moment = datetime.datetime.fromtimestamp(seconds, tz=datetime.timezone.utc)

    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


def path_rows(entries):
    rows = []
    for entry in entries:
        rows.append(
            {
                "relpath": nfc(entry.relpath),
                "size": int(entry.size),
                "mtime": iso8601_from_ns(entry.mtime_ns),
            }
        )
    rows.sort(key=lambda row: row["relpath"])

    return rows


def game_rows(games):
    rows = [{"externalRef": game[0], "title": game[1], "platform": game[2]} for game in games]
    rows.sort(key=lambda row: row["externalRef"])

    return rows


def estimated_bytes(paths, manifest_contents, games):
    total = 0
    for row in paths:
        total += len(row["relpath"]) + 32
    for text in (manifest_contents or {}).values():
        total += len(text or "")
    for row in games:
        total += len(row["externalRef"]) + len(row["title"]) + len(row["platform"]) + 16

    return total


def build_payload(
    library_type,
    hostname,
    captured_at,
    entries,
    manifest_contents=None,
    games=None,
    client_digest=None,
    machine_id=None,
    force=False,
):
    """Build the request body plus its fingerprint.

    Returns ``(body, digest)``. Raises :class:`PayloadTooLarge` when the body
    would exceed the server cap.
    """
    manifest_contents = manifest_contents or {}
    games = games or []
    digest = client_digest or compute_digest(entries, manifest_contents, games)

    paths = path_rows(entries)
    rows = game_rows(games) if games else []
    if estimated_bytes(paths, manifest_contents, rows) > MAX_PAYLOAD_BYTES:
        raise PayloadTooLarge(f"scan payload exceeds {MAX_PAYLOAD_BYTES} bytes")

    body = {
        "hostname": hostname,
        "libraryType": library_type,
        "capturedAt": captured_at,
        "clientDigest": digest,
        "force": bool(force),
        "paths": paths,
        "manifestContents": manifest_contents,
        "games": rows,
    }
    if machine_id:
        body["machineId"] = machine_id

    return body, digest


def build_check_body(library_type, hostname, client_digest, machine_id=None):
    body = {
        "hostname": hostname,
        "libraryType": library_type,
        "clientDigest": client_digest,
    }
    if machine_id:
        body["machineId"] = machine_id

    return body

"""Keycloak Device Authorization Grant with an offline refresh token.

A non-interactive run never starts the device flow: if there is no usable
cached token and the refresh token cannot be redeemed, :class:`ReauthRequired`
is raised so the CLI exits with code 2 and tells the user to run ``login``.
"""

import json
import logging
import os
import socket
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request


LOGGER = logging.getLogger("jordylab_scan.auth")

DEVICE_SCOPE = "openid offline_access"
MIN_INTERVAL_SECONDS = 5
EXPIRY_SKEW_SECONDS = 30


class OAuthError(Exception):
    def __init__(self, error, description=None):
        super().__init__(description or error)
        self.error = error
        self.description = description


def device_authorization_endpoint(cfg):
    return "{0}/realms/{1}/protocol/openid-connect/auth/device".format(
        cfg.keycloak_url.rstrip("/"), cfg.realm
    )


def token_endpoint(cfg):
    return "{0}/realms/{1}/protocol/openid-connect/token".format(
        cfg.keycloak_url.rstrip("/"), cfg.realm
    )


def revocation_endpoint(cfg):
    return "{0}/realms/{1}/protocol/openid-connect/revoke".format(
        cfg.keycloak_url.rstrip("/"), cfg.realm
    )


def _parse_json(raw):
    if not raw:
        return {}
    try:
        return json.loads(raw.decode("utf-8"))
    except ValueError:
        return {}


def _safe_error_body(error):
    try:
        raw = error.read()
    except Exception:  # noqa: BLE001 - best effort
        return {}

    return _parse_json(raw)


def post_form(url, fields, timeout=30):
    data = urllib.parse.urlencode(fields).encode("utf-8")
    request = urllib.request.Request(url, data=data, method="POST")
    request.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return _parse_json(response.read())
    except urllib.error.HTTPError as error:
        payload = _safe_error_body(error)
        raise OAuthError(
            payload.get("error", f"http_{error.code}"), payload.get("error_description")
        ) from error
    except (urllib.error.URLError, socket.timeout, TimeoutError, ConnectionError, OSError) as error:
        raise NetworkError(f"network failure: {error}") from error


def request_device_code(cfg):
    return post_form(
        device_authorization_endpoint(cfg),
        {"client_id": cfg.client_id, "scope": DEVICE_SCOPE},
    )


def poll_for_token(cfg, device_code, interval, expires_in, sleep=time.sleep, now=time.time):
    deadline = now() + expires_in
    current_interval = max(interval or 0, MIN_INTERVAL_SECONDS)
    while now() < deadline:
        sleep(current_interval)
        try:
            response = post_form(
                token_endpoint(cfg),
                {
                    "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id": cfg.client_id,
                    "device_code": device_code,
                },
            )
        except OAuthError as error:
            if error.error == "authorization_pending":
                continue
            if error.error == "slow_down":
                current_interval += 5
                continue
            raise ReauthRequired(
                f"device authorization failed: {error.description or error.error}"
            ) from error
        if response.get("access_token"):
            response["obtained_at"] = now()

            return response
        if response.get("error"):
            raise ReauthRequired("device authorization failed: {0}".format(response["error"]))

    raise ReauthRequired("device code expired before it was authorized")


def device_login(cfg, sleep=time.sleep, now=time.time, stream=None):
    import sys

    stream = stream or sys.stderr
    device = request_device_code(cfg)
    device_code = device.get("device_code")
    if not device_code:
        raise ReauthRequired(f"could not start device authorization: {device}")
    complete_uri = device.get("verification_uri_complete")
    user_code = device.get("user_code", "")
    verification_uri = device.get("verification_uri", "")
    if complete_uri:
        stream.write(f"Open this URL in a browser to authorize:\n  {complete_uri}\n")
    else:
        stream.write(f"Open {verification_uri} in a browser and enter code: {user_code}\n")
    stream.write("Waiting for authorization...\n")
    stream.flush()
    token = poll_for_token(
        cfg,
        device_code,
        device.get("interval", MIN_INTERVAL_SECONDS),
        device.get("expires_in", 300),
        sleep=sleep,
        now=now,
    )
    save_token(token)

    return token


def refresh_access_token(cfg, refresh_token, now=time.time):
    try:
        response = post_form(
            token_endpoint(cfg),
            {
                "grant_type": "refresh_token",
                "client_id": cfg.client_id,
                "refresh_token": refresh_token,
            },
        )
    except OAuthError as error:
        raise ReauthRequired(f"refresh failed: {error.description or error.error}") from error
    if not response.get("access_token"):
        raise ReauthRequired("refresh failed: no access token returned")
    response["obtained_at"] = now()

    return response


def revoke(cfg, refresh_token):
    """Best-effort RFC 7009 revocation of the offline session."""
    if not refresh_token:
        return False
    try:
        post_form(
            revocation_endpoint(cfg),
            {
                "client_id": cfg.client_id,
                "token": refresh_token,
                "token_type_hint": "refresh_token",
            },
        )

        return True
    except (OAuthError, NetworkError):
        return False


def load_token(path=None):
    path = path or token_file()
    if not os.path.isfile(str(path)):
        return None
    try:
        with open(str(path), encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return None

    return data if isinstance(data, dict) else None


def save_token(token, path=None):
    path = path or token_file()
    directory = os.path.dirname(str(path))
    if directory:
        os.makedirs(directory, exist_ok=True)
        os.chmod(directory, 0o700)
    handle, temp_path = tempfile.mkstemp(dir=directory or ".", prefix=".token-")
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as stream:
            json.dump(token, stream)
        os.chmod(temp_path, 0o600)
        os.replace(temp_path, str(path))
    except OSError:
        try:
            os.unlink(temp_path)
        except OSError:
            pass
        raise

    return path


def _is_expired(token, now):
    expires_in = token.get("expires_in")
    obtained_at = token.get("obtained_at")
    if expires_in is None or obtained_at is None:
        return True
    try:
        return (float(obtained_at) + float(expires_in) - EXPIRY_SKEW_SECONDS) <= now
    except (TypeError, ValueError):
        return True


def get_access_token(cfg, interactive=False, path=None, sleep=time.sleep, now=time.time):
    current = now()
    token = load_token(path)
    if token and token.get("access_token") and not _is_expired(token, current):
        return token["access_token"]

    if token and token.get("refresh_token"):
        try:
            refreshed = refresh_access_token(cfg, token["refresh_token"], now=now)
            save_token(refreshed, path)

            return refreshed["access_token"]
        except ReauthRequired as error:
            if not interactive:
                raise ReauthRequired(
                    "cached session is no longer valid; run 'login' to re-authenticate"
                ) from error
        except NetworkError as error:
            if not interactive:
                raise ReauthRequired(
                    "could not refresh the session; check connectivity or run 'login'"
                ) from error
            raise
    elif not interactive:
        raise ReauthRequired("not logged in; run 'login' to authenticate")

    token = device_login(cfg, sleep=sleep, now=now)
    save_token(token, path)

    return token["access_token"]

"""Thin urllib client for the ingest API.

TLS verification is always on (the stdlib default), proxy environment variables
are honoured by ``urllib`` automatically, and network failures are retried with
exponential backoff (at most five attempts, at most five minutes total).
"""

import json
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

CONNECT_TIMEOUT = 10
READ_TIMEOUT = 60
MAX_ATTEMPTS = 5
TOTAL_BUDGET_SECONDS = 300.0
BASE_DELAY_SECONDS = 2.0
MAX_DELAY_SECONDS = 60.0


class ScanError(Exception):
    """Base class carrying the process exit code for this failure."""

    exit_code = 6

    def __init__(self, message, reason=None):
        super().__init__(message)
        self.reason = reason


class ReauthRequired(ScanError):
    exit_code = 2


class NetworkError(ScanError):
    exit_code = 3


class ServerRejected(ScanError):
    exit_code = 4


class ConfigError(ScanError):
    exit_code = 6


def _safe_json(error):
    try:
        raw = error.read().decode("utf-8")
        return json.loads(raw) if raw else {}
    except Exception:  # noqa: BLE001 - error bodies are best effort
        return {}


def _http_error(status, payload):
    reason = None
    if isinstance(payload, dict):
        reason = payload.get("reason") or payload.get("detail")
    if status in (401,):
        return ReauthRequired(f"server rejected the token (HTTP {status})")
    if status == 403:
        return ConfigError("forbidden (HTTP 403); the account lacks the gamecatalog-scanner role")
    if status == 413:
        return ServerRejected("payload too large (HTTP 413)", reason="PAYLOAD_TOO_LARGE")
    if 500 <= status < 600:
        return NetworkError(f"server error (HTTP {status})")
    if 400 <= status < 500:
        return ServerRejected(f"server rejected the request (HTTP {status})", reason=reason)
    return ConfigError(f"unexpected HTTP status {status}")


def post_json(url, body, token=None, timeout=READ_TIMEOUT, opener=None):
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=data, method="POST")
    request.add_header("Content-Type", "application/json")
    request.add_header("Accept", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    open_url = opener or urllib.request.urlopen
    try:
        with open_url(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return response.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as error:
        raise _http_error(error.code, _safe_json(error)) from error
    except (urllib.error.URLError, socket.timeout, TimeoutError, ConnectionError, OSError) as error:
        raise NetworkError(f"network failure: {error}") from error


def post_with_retry(
    url,
    body,
    token=None,
    max_attempts=MAX_ATTEMPTS,
    total_budget=TOTAL_BUDGET_SECONDS,
    base_delay=BASE_DELAY_SECONDS,
    max_delay=MAX_DELAY_SECONDS,
    sleep=time.sleep,
    now=time.monotonic,
    opener=None,
):
    start = now()
    attempt = 0
    while True:
        attempt += 1
        try:
            return post_json(url, body, token=token, opener=opener)
        except NetworkError:
            elapsed = now() - start
            if attempt >= max_attempts or elapsed >= total_budget:
                raise
            delay = min(max_delay, base_delay * (2 ** (attempt - 1)))
            delay = min(delay, total_budget - elapsed)
            if delay <= 0:
                raise
            sleep(delay)


def check(cfg, body, token, **kwargs):
    url = cfg.backend_url.rstrip("/") + cfg.check_endpoint()

    return post_with_retry(url, body, token=token, **kwargs)


def scan(cfg, body, token, **kwargs):
    url = cfg.backend_url.rstrip("/") + cfg.scan_endpoint

    return post_with_retry(url, body, token=token, **kwargs)

"""Last-run state and human-readable status output."""

import json
import os
import tempfile


EXIT_MESSAGES = {
    0: "ok",
    2: "re-authentication needed; run 'login'",
    3: "network failure; will retry on the next run",
    4: "server rejected the scan",
    5: "partial result: one or more configured roots were skipped",
    6: "configuration or fatal error",
}


def last_run_path():
    return state_dir() / "last-run.json"


def write_last_run(state):
    path = last_run_path()
    directory = path.parent
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    handle, temp_path = tempfile.mkstemp(dir=str(directory), prefix=".last-run-")
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as stream:
            json.dump(state, stream, sort_keys=True)
        os.replace(temp_path, str(path))
    except OSError:
        try:
            os.unlink(temp_path)
        except OSError:
            pass
        raise

    return path


def read_last_run():
    path = last_run_path()
    if not path.is_file():
        return None
    try:
        with open(str(path), encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return None

    return data if isinstance(data, dict) else None


def exit_message(code):
    return EXIT_MESSAGES.get(code, f"unknown result ({code})")


def format_status(last_run, schedule_info=None, token_present=False, machine_id=None):
    lines = []
    if schedule_info is not None:
        lines.append(f"Automatic startup: {schedule_info}")
    lines.append("Session: {0}".format("cached" if token_present else "not logged in"))
    if machine_id:
        lines.append(f"Machine id: {machine_id}")
    if not last_run:
        lines.append("Last run: never")
    else:
        when = last_run.get("finishedAt") or last_run.get("startedAt") or "unknown"
        lines.append(f"Last run: {when}")
        lines.append("  library: {0}".format(last_run.get("libraryType", "unknown")))
        lines.append("  outcome: {0}".format(last_run.get("outcome", "unknown")))
        code = last_run.get("exitCode")
        if code is not None:
            lines.append(f"  result: {exit_message(int(code))}")
        skipped = last_run.get("skippedRoots")
        if skipped:
            lines.append("  skipped roots: {0}".format(", ".join(str(root) for root in skipped)))

    return "\n".join(lines)

"""Automatic-startup installers for Linux (systemd user units) and macOS.

No administrator/root rights are used: Linux writes user units and probes
``loginctl enable-linger`` (falling back to a login-triggered unit when polkit
denies self-linger); macOS writes a LaunchAgent that runs at login.
"""

import getpass
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import List

SERVICE_NAME = "jordylab-scan"
LAUNCH_LABEL = "dev.jordylab.scan"


def install_dir():
    return Path.home() / ".local" / "share" / "jordylab-scan"


def _systemd_user_dir():
    return Path.home() / ".config" / "systemd" / "user"


def _launchagents_dir():
    return Path.home() / "Library" / "LaunchAgents"


def systemd_service_text(exec_start):
    return (
        "[Unit]\n"
        "Description=JordyLab game catalog scan\n"
        "After=network.target\n"
        "\n"
        "[Service]\n"
        "Type=oneshot\n"
        f"ExecStart={exec_start}\n"
    )


def systemd_timer_text():
    return (
        "[Unit]\n"
        "Description=Run the JordyLab game catalog scan periodically\n"
        "\n"
        "[Timer]\n"
        "OnBootSec=5min\n"
        "OnUnitActiveSec=6h\n"
        "RandomizedDelaySec=10min\n"
        "Persistent=true\n"
        "\n"
        "[Install]\n"
        "WantedBy=timers.target\n"
    )


def systemd_login_service_text(exec_start):
    return (
        "[Unit]\n"
        "Description=JordyLab game catalog scan (login-triggered)\n"
        "\n"
        "[Service]\n"
        "Type=oneshot\n"
        f"ExecStart={exec_start}\n"
        "\n"
        "[Install]\n"
        "WantedBy=default.target\n"
    )


def launchagent_plist_text(label, program_args, stdout_path, stderr_path):
    arguments = "".join(
        f"    <string>{_xml_escape(argument)}</string>\n" for argument in program_args
    )

    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" '
        '"http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n'
        '<plist version="1.0">\n'
        "<dict>\n"
        "  <key>Label</key>\n"
        f"  <string>{_xml_escape(label)}</string>\n"
        "  <key>ProgramArguments</key>\n"
        "  <array>\n"
        f"{arguments}  </array>\n"
        "  <key>RunAtLoad</key>\n"
        "  <true/>\n"
        "  <key>StandardOutPath</key>\n"
        f"  <string>{_xml_escape(str(stdout_path))}</string>\n"
        "  <key>StandardErrorPath</key>\n"
        f"  <string>{_xml_escape(str(stderr_path))}</string>\n"
        "</dict>\n"
        "</plist>\n"
    )


def _xml_escape(value):
    return str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _run(command):
    try:
        return subprocess.run(command, capture_output=True, text=True, check=False).returncode
    except (OSError, FileNotFoundError):  # pragma: no cover - tool not installed
        return 127


def probe_linger(user=None):
    """Try to enable lingering non-interactively; return whether it succeeded."""
    user = user or os.environ.get("USER") or getpass.getuser()

    return _run(["loginctl", "enable-linger", user]) == 0


def read_old_shell_schedules():
    """Find pre-existing shell-script schedules referencing jordylab-scan-*.sh."""
    found: List[str] = []

    for directory in (_systemd_user_dir(), _launchagents_dir()):
        if not directory.is_dir():
            continue
        for path in sorted(directory.glob("*jordylab*")):
            try:
                text = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            if "jordylab-scan" in text and ".sh" in text:
                found.append(str(path))

    crontab = _crontab_lines()
    for line in crontab:
        if "jordylab-scan" in line and ".sh" in line:
            found.append("crontab: " + line.strip())

    return found


def _crontab_lines():
    try:
        result = subprocess.run(["crontab", "-l"], capture_output=True, text=True, check=False)
    except (OSError, FileNotFoundError):  # pragma: no cover
        return []
    if result.returncode != 0:
        return []

    return result.stdout.splitlines()


def remove_old_shell_schedules():
    """Remove old shell-script schedules; returns what was removed."""
    removed: List[str] = []

    for directory in (_systemd_user_dir(), _launchagents_dir()):
        if not directory.is_dir():
            continue
        for path in sorted(directory.glob("*jordylab*")):
            try:
                text = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            if "jordylab-scan" in text and ".sh" in text:
                try:
                    path.unlink()
                    removed.append(str(path))
                except OSError:
                    pass

    lines = _crontab_lines()
    kept = [line for line in lines if not ("jordylab-scan" in line and ".sh" in line)]
    if len(kept) != len(lines):
        _write_crontab(kept)
        removed.append("crontab entry")

    return removed


def _write_crontab(lines):
    try:
        subprocess.run(
            ["crontab", "-"],
            input="\n".join(lines) + ("\n" if lines else ""),
            text=True,
            check=False,
        )
    except (OSError, FileNotFoundError):  # pragma: no cover
        pass


def install(script_path, library_type, python_executable=None, remove_old=True):
    """Install the client to run automatically; returns a result dict."""
    python_executable = python_executable or sys.executable
    target_dir = install_dir()
    target_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    installed_script = target_dir / f"jordylab-scan-{library_type.lower()}.py"
    source = Path(script_path) if script_path else None
    if source is not None and source.is_file():
        shutil.copyfile(str(source), str(installed_script))
        os.chmod(str(installed_script), 0o700)
    else:
        # Packaged/dev mode: write a tiny launcher that runs the installed package.
        installed_script.write_text(
            "#!/usr/bin/env python3\nfrom jordylab_scan.__main__ import main\n"
            "raise SystemExit(main())\n",
            encoding="utf-8",
        )
        os.chmod(str(installed_script), 0o700)

    exec_start = f"{python_executable} {installed_script} scan --library {library_type}"
    removed = remove_old_shell_schedules() if remove_old else []

    if sys.platform == "darwin":
        mode = _install_macos(exec_start, target_dir)
    else:
        mode = _install_linux(exec_start)

    return {
        "installedScript": str(installed_script),
        "mode": mode,
        "removedOldSchedules": removed,
    }


def _install_linux(exec_start):
    unit_dir = _systemd_user_dir()
    unit_dir.mkdir(parents=True, exist_ok=True)
    service_path = unit_dir / f"{SERVICE_NAME}.service"
    timer_path = unit_dir / f"{SERVICE_NAME}.timer"
    service_path.write_text(systemd_service_text(exec_start), encoding="utf-8")
    timer_path.write_text(systemd_timer_text(), encoding="utf-8")
    _run(["systemctl", "--user", "daemon-reload"])
    _run(["systemctl", "--user", "enable", "--now", f"{SERVICE_NAME}.timer"])

    if probe_linger():
        return "timer+linger"

    # Linger was denied: also install a login-triggered unit so the scan still
    # runs without root.
    login_service = unit_dir / f"{SERVICE_NAME}-login.service"
    login_service.write_text(systemd_login_service_text(exec_start), encoding="utf-8")
    _run(["systemctl", "--user", "daemon-reload"])
    _run(["systemctl", "--user", "enable", f"{SERVICE_NAME}-login.service"])

    return "timer+login (linger unavailable; run 'sudo loginctl enable-linger {0}')".format(
        os.environ.get("USER", "$USER")
    )


def _install_macos(exec_start, target_dir):
    agents = _launchagents_dir()
    agents.mkdir(parents=True, exist_ok=True)
    arguments = exec_start.split(" ")
    log_path = target_dir / f"{SERVICE_NAME}.log"
    plist_path = agents / f"{LAUNCH_LABEL}.plist"
    plist_path.write_text(
        launchagent_plist_text(LAUNCH_LABEL, arguments, log_path, log_path),
        encoding="utf-8",
    )
    _run(["launchctl", "unload", str(plist_path)])
    _run(["launchctl", "load", "-w", str(plist_path)])

    return "launchagent"


def uninstall():
    """Remove the schedule; returns what was removed."""
    removed: List[str] = []
    if sys.platform == "darwin":
        plist_path = _launchagents_dir() / f"{LAUNCH_LABEL}.plist"
        if plist_path.exists():
            _run(["launchctl", "unload", str(plist_path)])
            try:
                plist_path.unlink()
                removed.append(str(plist_path))
            except OSError:
                pass
    else:
        unit_dir = _systemd_user_dir()
        _run(["systemctl", "--user", "disable", "--now", f"{SERVICE_NAME}.timer"])
        _run(["systemctl", "--user", "disable", f"{SERVICE_NAME}-login.service"])
        for suffix in (".service", ".timer"):
            path = unit_dir / (SERVICE_NAME + suffix)
            if path.exists():
                try:
                    path.unlink()
                    removed.append(str(path))
                except OSError:
                    pass
        login_service = unit_dir / f"{SERVICE_NAME}-login.service"
        if login_service.exists():
            try:
                login_service.unlink()
                removed.append(str(login_service))
            except OSError:
                pass
        _run(["systemctl", "--user", "daemon-reload"])

    return removed


def status_line():
    """One-line description of the installed startup mode."""
    if sys.platform == "darwin":
        present = (_launchagents_dir() / f"{LAUNCH_LABEL}.plist").exists()

        return "launchagent installed" if present else "not installed"
    present = (_systemd_user_dir() / f"{SERVICE_NAME}.timer").exists()

    return "systemd user timer installed" if present else "not installed"

"""Command-line entry point.

Commands: ``scan`` (default), ``login``, ``reauth``, ``install``, ``uninstall``,
``status``, ``version``. Flags: ``--path``, ``--force``, ``--library``,
``--selftest``.

When frozen into a single file, service endpoints are read from the rendered
constants header at the top of the file. ``--selftest`` ignores that block
entirely and runs a fully offline smoke test against a local stub server.
"""

import argparse
import datetime
import json
import logging
import os
import platform
import re
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Dict, List, Tuple


LOGGER = logging.getLogger("jordylab_scan")

HEADER_KEYS = (
    "KEYCLOAK_URL",
    "REALM",
    "CLIENT_ID",
    "BACKEND_URL",
    "SCAN_ENDPOINT",
    "LIBRARY_TYPE",
)

KNOWN_COMMANDS = {"scan", "login", "reauth", "install", "uninstall", "status", "version"}

EXIT_OK = 0
EXIT_REAUTH = 2
EXIT_NETWORK = 3
EXIT_REJECTED = 4
EXIT_PARTIAL = 5
EXIT_CONFIG = 6


def header_constants():
    result = {}
    namespace = globals()
    for key in HEADER_KEYS:
        value = namespace.get(key)
        if isinstance(value, str) and value and PLACEHOLDER_MARK not in value:
            result[key] = value

    return result


def detect_hostname():
    raw = platform.node() or ""
    host = raw.split(".")[0]
    host = re.sub(r"[^A-Za-z0-9._-]", "-", host).strip("-")
    if len(host) > 100:
        host = host[:100]

    return host or "unknown-host"


def _now_iso():
    return datetime.datetime.now(tz=datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def resolve_library_type(args, constants):
    candidates = [
        getattr(args, "library", None),
        constants.get("LIBRARY_TYPE"),
        load_config_json().get("libraryType"),
    ]
    for candidate in candidates:
        if candidate:
            return normalize_library_type(candidate)
    raise ValueError("--library is required (STEAM or EMUDECK)")


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


def _record(state):
    state.setdefault("finishedAt", _now_iso())
    try:
        write_last_run(state)
    except OSError:  # pragma: no cover - disk failure must not mask the result
        pass


def run_scan(args, constants):
    library_type = resolve_library_type(args, constants)
    config_data = load_config_json()
    cfg = resolve_service(library_type, constants=constants, config_data=config_data)
    hostname = detect_hostname()
    machine_id = ensure_machine_id()

    if args.path:
        roots = [str(path) for path in args.path]
        explicit = True
    else:
        roots = configured_roots(library_type)
        explicit = bool(roots)
        if not roots:
            roots = [str(path) for path in default_roots(library_type)]
            explicit = False

    state = {
        "libraryType": library_type,
        "hostname": hostname,
        "startedAt": _now_iso(),
        "roots": [str(root) for root in roots],
    }

    if not roots:
        state.update({"outcome": "NO_SOURCE", "exitCode": EXIT_OK})
        _record(state)
        LOGGER.info("No %s library roots found; nothing to do", library_type)

        return EXIT_OK

    lock_path = str(state_dir() / "scan.lock")
    with file_lock(lock_path, timeout=0.0):
        try:
            entries, manifest_contents, games, skipped = collect_library(
                library_type, roots, explicit
            )
        except OSError as error:
            state.update({"outcome": "ERROR", "exitCode": EXIT_CONFIG, "error": str(error)})
            _record(state)

            return EXIT_CONFIG

        if skipped:
            state.update(
                {
                    "outcome": "PARTIAL",
                    "exitCode": EXIT_PARTIAL,
                    "skippedRoots": skipped,
                }
            )
            _record(state)
            LOGGER.error(
                "Skipping %s because configured root(s) are missing, unreadable, or empty: %s",
                library_type,
                ", ".join(skipped),
            )

            return EXIT_PARTIAL

        if not entries:
            state.update({"outcome": "NO_CONTENT", "exitCode": EXIT_OK})
            _record(state)
            LOGGER.info("No files matched under the discovered roots; nothing to upload")

            return EXIT_OK

        digest = compute_digest(entries, manifest_contents, games)
        token = get_access_token(cfg, interactive=False)

        check_body = build_check_body(library_type, hostname, digest, machine_id)
        _, check_response = check(cfg, check_body, token)
        scan_needed = bool(check_response.get("scanNeeded"))
        if args.force:
            scan_needed = True

        if not scan_needed:
            state.update(
                {
                    "outcome": "NO_CHANGE",
                    "exitCode": EXIT_OK,
                    "clientDigest": digest,
                    "sourceEnabled": bool(check_response.get("sourceEnabled", True)),
                }
            )
            _record(state)
            LOGGER.info("%s library unchanged since the last scan; nothing uploaded", library_type)

            return EXIT_OK

        captured_at = _now_iso()
        body, digest = build_payload(
            library_type,
            hostname,
            captured_at,
            entries,
            manifest_contents=manifest_contents,
            games=games,
            client_digest=digest,
            machine_id=machine_id,
            force=bool(args.force),
        )
        _, response = scan(cfg, body, token)
        outcome = response.get("outcome")
        state.update(
            {
                "outcome": outcome or "UNKNOWN",
                "clientDigest": digest,
                "sourceEnabled": bool(response.get("sourceEnabled", True)),
                "counts": response.get("counts"),
                "finishedAt": _now_iso(),
            }
        )

        if outcome in ("APPLIED", "NO_CHANGE"):
            state["exitCode"] = EXIT_OK
            _record(state)
            LOGGER.info("%s scan %s: %s", library_type, outcome, json.dumps(response.get("counts") or {}))

            return EXIT_OK

        if outcome == "REJECTED":
            state["exitCode"] = EXIT_REJECTED
            state["reason"] = response.get("reason")
            _record(state)
            LOGGER.error("Server rejected the scan: %s", response.get("reason"))

            return EXIT_REJECTED

        state["exitCode"] = EXIT_CONFIG
        _record(state)

        return EXIT_CONFIG


def run_login(args, constants):
    library_type = resolve_library_type(args, constants)
    cfg = resolve_service(library_type, constants=constants, config_data=load_config_json())
    ensure_machine_id()

    device_login(cfg)
    LOGGER.info("Logged in; token cached at %s", token_file())

    return EXIT_OK


def run_install(args, constants):
    library_type = resolve_library_type(args, constants)
    script_path = _install_source()
    result = install_schedule(script_path, library_type)
    LOGGER.info("Installed client (%s): %s", result["mode"], result["installedScript"])
    if result.get("removedOldSchedules"):
        LOGGER.info("Removed old shell schedules: %s", ", ".join(result["removedOldSchedules"]))

    return EXIT_OK


def _install_source():
    candidate = sys.argv[0] if sys.argv else ""
    if candidate and os.path.isfile(candidate) and not candidate.endswith("__main__.py"):
        return candidate

    return None


def run_uninstall(args):
    token = load_token() or {}
    revoked = revoke(_uninstall_config(), token.get("refresh_token"))
    removed = uninstall_schedule()
    try:
        if os.path.isfile(str(token_file())):
            os.remove(str(token_file()))
    except OSError:  # pragma: no cover
        pass
    LOGGER.info(
        "Uninstalled (session revoked: %s); removed: %s", revoked, ", ".join(removed) or "none"
    )
    if args.purge:
        _purge()
        LOGGER.info("Purged machine id, config, and state")

    return EXIT_OK


def _uninstall_config():
    constants = header_constants()
    try:
        library_type = normalize_library_type(
            constants.get("LIBRARY_TYPE") or load_config_json().get("libraryType") or "EMUDECK"
        )
    except ValueError:
        library_type = "EMUDECK"

    return resolve_service(library_type, constants=constants, config_data=load_config_json())


def _purge():

    for path in (machine_id_file(), Path(config_dir()) / "config.json", token_file()):
        try:
            if os.path.isfile(str(path)):
                os.remove(str(path))
        except OSError:  # pragma: no cover
            pass
    for directory in (state_dir(),):
        try:
            for child in Path(directory).glob("*"):
                if child.is_file():
                    child.unlink()
        except OSError:  # pragma: no cover
            pass


def run_status(args, constants):
    old = read_old_shell_schedules()
    machine_id = read_machine_id()
    output = format_status(
        read_last_run(),
        schedule_info=status_line(),
        token_present=os.path.isfile(str(token_file())),
        machine_id=machine_id,
    )
    if old:
        output += "\nWARNING: old shell-script schedule(s) still present: " + ", ".join(old)
    print(output)

    return EXIT_OK


def _log_error(error):
    LOGGER.error("%s", error)


def build_parser():
    parser = argparse.ArgumentParser(
        prog="jordylab-scan", description="JordyLab library scan client"
    )
    parser.add_argument("--selftest", action="store_true", help="run an offline self-test and exit")
    parser.add_argument("--version", action="store_true", help="print the client version")
    subparsers = parser.add_subparsers(dest="command")

    scan_parser = subparsers.add_parser("scan", help="scan a library (default)")
    scan_parser.add_argument("--path", action="append", help="explicit library root (repeatable)")
    scan_parser.add_argument("--force", action="store_true", help="upload even if unchanged")
    scan_parser.add_argument("--library", help="STEAM or EMUDECK")

    login_parser = subparsers.add_parser("login", help="authenticate in a browser")
    login_parser.add_argument("--library", help="STEAM or EMUDECK")

    reauth_parser = subparsers.add_parser("reauth", help="force a fresh browser login")
    reauth_parser.add_argument("--library", help="STEAM or EMUDECK")

    install_parser = subparsers.add_parser("install", help="install automatic startup")
    install_parser.add_argument("--library", help="STEAM or EMUDECK")

    uninstall_parser = subparsers.add_parser("uninstall", help="remove automatic startup")
    uninstall_parser.add_argument("--purge", action="store_true", help="also remove local state")

    subparsers.add_parser("status", help="show the last run and startup state")
    subparsers.add_parser("version", help="print the client version")

    return parser


def main(argv=None):
    logging.basicConfig(level=logging.INFO, format="[jordylab] %(message)s")
    raw = list(sys.argv[1:] if argv is None else argv)

    if "--selftest" in raw:
        return run_selftest()

    if "--version" in raw or (raw and raw[0] == "version"):
        print(__version__)

        return EXIT_OK

    parser = build_parser()
    if raw and raw[0] not in KNOWN_COMMANDS and raw[0].startswith("-"):
        raw = ["scan"] + raw
    if not raw:
        raw = ["scan"]
    args = parser.parse_args(raw)

    if args.version or args.command == "version":
        print(__version__)

        return EXIT_OK

    constants = header_constants()
    command = args.command or "scan"
    try:
        if command == "scan":
            return run_scan(args, constants)
        if command == "login":
            return run_login(args, constants)
        if command == "reauth":
            return run_login(args, constants)
        if command == "install":
            return run_install(args, constants)
        if command == "uninstall":
            return run_uninstall(args)
        if command == "status":
            return run_status(args, constants)
        return EXIT_CONFIG
    except ScanError as error:
        _log_error(error)

        return error.exit_code
    except PayloadTooLarge as error:
        _log_error(error)

        return EXIT_REJECTED
    except ValueError as error:
        _log_error(error)

        return EXIT_CONFIG


def run_selftest():
    """Fully offline smoke test: fixture library, local stub server, check + scan."""
    root = None
    server = None
    try:
        root = Path(tempfile.mkdtemp(prefix="jordylab-selftest-"))
        _write_fixture(root)
        entries, games = build_emudeck([root])
        if not games:
            print("selftest: fixture produced no games", file=sys.stderr)

            return 1
        digest_first = compute_digest(entries, {}, games)
        entries_again, games_again = build_emudeck([root])
        if compute_digest(entries_again, {}, games_again) != digest_first:
            print("selftest: digest is not stable across walks", file=sys.stderr)

            return 1

        server = _start_stub_server()
        port = server.server_address[1]
        cfg = resolve_service("EMUDECK", config_data={}, env={})
        cfg.backend_url = f"http://127.0.0.1:{port}"
        hostname = detect_hostname()
        token = "selftest"

        check_body = build_check_body("EMUDECK", hostname, digest_first, "selftest-machine")
        _, check_response = check(cfg, check_body, token)
        if not check_response.get("scanNeeded"):
            print("selftest: stub server did not request a scan", file=sys.stderr)

            return 1

        body, _ = build_payload(
            "EMUDECK",
            hostname,
            _now_iso(),
            entries,
            manifest_contents={},
            games=games,
            client_digest=digest_first,
            machine_id="selftest-machine",
        )
        _, scan_response = scan(cfg, body, token)
        if scan_response.get("outcome") != "APPLIED":
            print(f"selftest: unexpected scan outcome {scan_response}", file=sys.stderr)

            return 1

        print(f"selftest ok: {len(games)} game(s), digest {digest_first}")

        return 0
    except Exception as error:  # noqa: BLE001 - selftest reports any failure as exit 1
        print(f"selftest failed: {error}", file=sys.stderr)

        return 1
    finally:
        if server is not None:
            server.shutdown()
            server.server_close()
        if root is not None:
            import shutil

            shutil.rmtree(str(root), ignore_errors=True)


def _write_fixture(root):
    snes = root / "snes"
    snes.mkdir(parents=True, exist_ok=True)
    (snes / "Super Mario World (USA) (Rev 1).sfc").write_bytes(b"rom")
    psx = root / "psx"
    psx.mkdir(parents=True, exist_ok=True)
    (psx / "Final Fantasy VII.bin").write_bytes(b"data")
    (psx / "Final Fantasy VII.cue").write_text(
        'FILE "Final Fantasy VII.bin" BINARY\n  TRACK 01 MODE2/2352\n', encoding="utf-8"
    )


class _StubHandler(BaseHTTPRequestHandler):
    def do_POST(self):  # noqa: N802 - http.server API
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        if self.path.endswith("/check"):
            payload = {"scanNeeded": True, "sourceEnabled": True}
        else:
            payload = {
                "outcome": "APPLIED",
                "sourceEnabled": True,
                "counts": {"submitted": 1, "added": 1, "updated": 0, "removed": 0, "rejected": 0},
            }
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, *args):  # noqa: D401 - silence test server
        return


def _start_stub_server():
    server = ThreadingHTTPServer(("127.0.0.1", 0), _StubHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    return server


if __name__ == "__main__":
    raise SystemExit(main())
