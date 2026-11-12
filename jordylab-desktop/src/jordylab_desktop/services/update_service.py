"""In-app updates: check GitHub Releases, verify, download, stage, swap.

The release workflow publishes ``update-manifest.json`` with one entry per
platform (url, sha256, signature). The app compares versions, verifies the
download against the manifest hash before touching anything, and keeps the
previous version until the new one starts (FR-006/FR-007).

The swap is deliberately conservative: a staged artifact replaces the running
executable only on explicit confirmation, and a failure leaves the old file in
place.
"""

import hashlib
import json
import os
import shutil
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path

DEFAULT_MANIFEST_URL = (
    "https://github.com/jordyswinnen/jordylab/releases/latest/download/update-manifest.json"
)


class UpdateError(Exception):
    """Checking, downloading, verifying or applying an update failed."""


def _version_tuple(value):
    parts = []
    for piece in str(value).lstrip("v").split("."):
        digits = "".join(character for character in piece if character.isdigit())
        parts.append(int(digits) if digits else 0)

    return tuple(parts)


def is_newer(current, candidate):
    """True when ``candidate`` is a strictly newer dotted version."""
    current_parts = _version_tuple(current)
    candidate_parts = _version_tuple(candidate)
    length = max(len(current_parts), len(candidate_parts))
    current_parts += (0,) * (length - len(current_parts))
    candidate_parts += (0,) * (length - len(candidate_parts))

    return candidate_parts > current_parts


@dataclass(frozen=True)
class UpdateInfo:
    version: str
    url: str
    sha256: str
    signature: str = ""
    notes: str = ""


def fetch_manifest(url=DEFAULT_MANIFEST_URL, opener=None):
    open_url = opener or urllib.request.urlopen
    try:
        with open_url(url, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except (OSError, ValueError, urllib.error.URLError) as error:
        raise UpdateError(f"cannot read the update manifest: {error}") from error


def update_for_platform(manifest, platform_key):
    """The UpdateInfo for this platform, or None when absent."""
    platforms = (manifest or {}).get("platforms") or {}
    entry = platforms.get(platform_key)
    version = (manifest or {}).get("version")
    if not isinstance(entry, dict) or not version:
        return None
    url = entry.get("url")
    digest = entry.get("sha256")
    if not isinstance(url, str) or not isinstance(digest, str):
        return None

    return UpdateInfo(
        version=str(version),
        url=url,
        sha256=digest.lower(),
        signature=str(entry.get("signature") or ""),
        notes=str((manifest or {}).get("notes") or ""),
    )


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)

    return digest.hexdigest()


def download_verified(info, directory, opener=None):
    """Download the artifact and return its path only when the hash matches."""
    open_url = opener or urllib.request.urlopen
    target_dir = Path(directory)
    target_dir.mkdir(parents=True, exist_ok=True)
    suffix = Path(urllib.request.urlparse(info.url).path).suffix or ".bin"
    handle, temporary = tempfile.mkstemp(dir=str(target_dir), prefix=".update-", suffix=suffix)
    os.close(handle)
    temporary_path = Path(temporary)
    try:
        with open_url(info.url, timeout=60) as response, open(temporary_path, "wb") as target:
            shutil.copyfileobj(response, target)
        if sha256_of(temporary_path) != info.sha256:
            raise UpdateError("the downloaded update does not match its checksum")
        final_path = target_dir / f"jordylab-desktop-{info.version}{suffix}"
        os.replace(temporary_path, final_path)

        return final_path
    except Exception:
        try:
            temporary_path.unlink()
        except OSError:
            pass
        raise


def apply_staged(staged_path, executable_path):
    """Replace the running executable, keeping the previous one until it works.

    The caller restarts the app after this returns; if the swap itself fails the
    old executable is untouched.
    """
    staged = Path(staged_path)
    executable = Path(executable_path)
    if not staged.is_file():
        raise UpdateError("the staged update is missing")
    backup = executable.with_suffix(executable.suffix + ".old")
    try:
        if backup.exists():
            backup.unlink()
        os.replace(executable, backup)
        os.replace(staged, executable)
        os.chmod(executable, 0o755)
    except OSError as error:
        if backup.exists() and not executable.exists():
            os.replace(backup, executable)
        raise UpdateError(f"cannot replace the running version: {error}") from error

    return backup


class UpdateService:
    """Checks for a newer release for this platform."""

    def __init__(
        self,
        current_version,
        platform_key,
        manifest_url=DEFAULT_MANIFEST_URL,
        state_dir=None,
        opener=None,
    ):
        self.current_version = current_version
        self.platform_key = platform_key
        self.manifest_url = manifest_url
        self.state_dir = (
            Path(state_dir)
            if state_dir
            else (Path.home() / ".local" / "state" / "jordylab-desktop")
        )
        self.opener = opener

    def check(self):
        """The newer UpdateInfo, or None when up to date."""
        manifest = fetch_manifest(self.manifest_url, opener=self.opener)
        info = update_for_platform(manifest, self.platform_key)
        if info is None or not is_newer(self.current_version, info.version):
            return None

        return info

    def download(self, info):
        return download_verified(info, self.state_dir / "updates", opener=self.opener)


def platform_key():
    import sys

    if sys.platform == "darwin":
        return "macos-aarch64"
    if sys.platform.startswith("win"):
        return "windows-x64"

    return "linux-x86_64"
