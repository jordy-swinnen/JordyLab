"""Sync orchestration: detect libraries, ask the server, upload only when told.

This mirrors the frozen client's flow (spec 003) exactly: build the fingerprint
from the same inputs, call ``/ingest/check``, and only then upload to
``/ingest/scan``. The server stays authoritative. The scan rules themselves live
in ``jordylab_scan`` and are never reimplemented here.

Qt-free by design: callers pass a progress callback, so the same code runs in a
worker thread and in tests.
"""

import datetime
import platform as platform_module
import re
from dataclasses import dataclass, field

from jordylab_scan.api import (
    NetworkError,
    ReauthRequired,
    ScanError,
    ServerRejected,
    check,
    scan,
)
from jordylab_scan.auth import clear_token, get_token
from jordylab_scan.config import ensure_machine_id, resolve_service
from jordylab_scan.grouping import build_emudeck, build_steam
from jordylab_scan.manifest import compute_digest
from jordylab_scan.payload import PayloadTooLarge, build_check_body, build_payload

from ..detect.libraries import SUNSHINE, detect_libraries
from ..detect.sunshine import sunshine_apps

UP_TO_DATE = "NO_CHANGE"
APPLIED = "APPLIED"
NO_CONTENT = "NO_CONTENT"
NO_SOURCE = "NO_SOURCE"
PARTIAL = "PARTIAL"
REJECTED = "REJECTED"
REAUTH_REQUIRED = "REAUTH_REQUIRED"
NETWORK_ERROR = "NETWORK_ERROR"
FAILED = "FAILED"


def now_iso():
    return datetime.datetime.now(tz=datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def detect_hostname():
    raw = platform_module.node() or ""
    host = re.sub(r"[^A-Za-z0-9._-]", "-", raw.split(".")[0]).strip("-")

    return host[:100] or "unknown-host"


@dataclass(frozen=True)
class LibraryResult:
    library_type: str
    outcome: str
    message: str = ""
    games: int = 0
    skipped_roots: tuple = ()
    counts: dict = field(default_factory=dict)


@dataclass(frozen=True)
class SyncReport:
    started_at: str
    finished_at: str
    results: tuple

    @property
    def ok(self):
        return all(result.outcome in (UP_TO_DATE, APPLIED, NO_CONTENT, NO_SOURCE)
                   for result in self.results)

    def worst_outcome(self):
        for result in self.results:
            if result.outcome not in (UP_TO_DATE, APPLIED):
                return result.outcome

        return UP_TO_DATE


def _root_usable(root):
    from pathlib import Path
    import os

    try:
        return Path(root).is_dir() and os.access(str(root), os.R_OK)
    except OSError:
        return False


def _collect(library_type, roots, explicit):
    """Aggregate scan inputs across roots (same dedupe rules as the scanner)."""
    entries = []
    manifest_contents = {}
    games = []
    seen_entries = set()
    seen_refs = set()
    skipped = []

    for root in roots:
        if not _root_usable(root):
            if explicit:
                skipped.append(str(root))
            continue
        if library_type == "STEAM":
            root_entries, root_contents = build_steam([root])
            root_games = []
            has_content = bool(root_entries)
        elif library_type == SUNSHINE:
            continue  # handled by _collect_sunshine
        else:
            root_entries, root_games = build_emudeck([root])
            root_contents = {}
            has_content = bool(root_games)
        if not has_content:
            if explicit:
                skipped.append(str(root))
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

    return entries, manifest_contents, games, skipped


def _collect_sunshine(config_dir=None):
    apps = sunshine_apps(config_dir)
    games = [(f"sunshine:{app.name.lower()}", app.name, "Sunshine") for app in apps]
    if not games:
        return [], {}, [], []

    from pathlib import Path

    path = Path(config_dir or (Path.home() / ".config" / "sunshine")) / "apps.json"
    entries = []
    try:
        stat = path.stat()
        from jordylab_scan.manifest import ScanEntry

        entries.append(ScanEntry(relpath="sunshine/apps.json", size=stat.st_size,
                                 mtime_ns=stat.st_mtime_ns))
    except OSError:
        pass

    return entries, {}, games, []


def scan_library(library_type, roots, explicit, cfg, hostname, machine_id, token,
                 force=False, sunshine_dir=None):
    """Run one library through check → scan, returning a LibraryResult."""
    if library_type == SUNSHINE:
        entries, manifest_contents, games, skipped = _collect_sunshine(sunshine_dir)
    else:
        entries, manifest_contents, games, skipped = _collect(library_type, roots, explicit)
    if skipped:
        return LibraryResult(library_type, PARTIAL,
                             message="some roots were skipped",
                             skipped_roots=tuple(skipped))
    if not entries and not games:
        return LibraryResult(library_type, NO_CONTENT, message="nothing to scan")

    digest = compute_digest(entries, manifest_contents, games)
    check_response = check(cfg, build_check_body(library_type, hostname, digest, machine_id),
                           token)
    if not check_response.get("scanNeeded") and not force:
        return LibraryResult(library_type, UP_TO_DATE, games=len(games))

    body, digest = build_payload(
        library_type,
        hostname,
        now_iso(),
        entries,
        manifest_contents=manifest_contents,
        games=games,
        client_digest=digest,
        machine_id=machine_id,
        force=bool(force),
    )
    response = scan(cfg, body, token)
    outcome = response.get("outcome") or FAILED
    if outcome == APPLIED:
        return LibraryResult(library_type, APPLIED, games=len(games),
                             counts=response.get("counts") or {})
    if outcome == UP_TO_DATE:
        return LibraryResult(library_type, UP_TO_DATE, games=len(games))
    if outcome == "REJECTED":
        return LibraryResult(library_type, REJECTED,
                             message=str(response.get("reason") or "rejected by the server"))

    return LibraryResult(library_type, FAILED, message=f"unexpected outcome {outcome}")


class SyncService:
    """Runs a full sync over every detected library, one result per library."""

    def __init__(self, config, token_path=None, sunshine_dir=None):
        self.config = config
        self.token_path = token_path
        self.sunshine_dir = sunshine_dir
        self.libraries = []

    def run_once(self, force=False, on_library=None):
        started = now_iso()
        hostname = detect_hostname()
        machine_id = ensure_machine_id()
        results = []
        try:
            token = get_token(interactive=False, path=self.token_path)
        except ReauthRequired as error:
            self.libraries = detect_libraries()
            report = SyncReport(started, now_iso(), tuple(
                LibraryResult(library.library_type, REAUTH_REQUIRED, message=str(error))
                for library in self.libraries
            ))

            return report

        self.libraries = detect_libraries(self.config.extra_roots)
        for library in self.libraries:
            if on_library:
                on_library(library.library_type, "checking")
            result = self._scan_one(library, hostname, machine_id, token, force)
            results.append(result)
            if on_library:
                on_library(library.library_type, result.outcome)

        return SyncReport(started, now_iso(), tuple(results))

    def _scan_one(self, library, hostname, machine_id, token, force):
        if library.library_type == SUNSHINE and not library.sunshine_apps:
            return LibraryResult(library.library_type, NO_SOURCE,
                                 message="Sunshine is not configured on this machine")
        cfg = resolve_service(
            library.library_type,
            config_data={"service": {"backend_url": self.config.backend_url}},
            env={},
        )
        try:
            return scan_library(
                library.library_type, library.roots, library.explicit, cfg,
                hostname, machine_id, token, force=force,
                sunshine_dir=self.sunshine_dir,
            )
        except ReauthRequired:
            clear_token(self.token_path)

            return LibraryResult(library.library_type, REAUTH_REQUIRED,
                                 message="the machine must be reconnected")
        except NetworkError as error:
            return LibraryResult(library.library_type, NETWORK_ERROR, message=str(error))
        except ServerRejected as error:
            return LibraryResult(library.library_type, REJECTED,
                                 message=str(error.reason or error))
        except PayloadTooLarge as error:
            return LibraryResult(library.library_type, REJECTED, message=str(error))
        except ScanError as error:
            return LibraryResult(library.library_type, FAILED, message=str(error))
        except OSError as error:
            return LibraryResult(library.library_type, FAILED, message=str(error))
