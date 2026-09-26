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

from .__init__ import __version__
from .api import ScanError, check, scan
from .auth import get_access_token, load_token, revoke
from .config import (
    PLACEHOLDER_MARK,
    configured_roots,
    ensure_machine_id,
    load_config_json,
    normalize_library_type,
    read_machine_id,
    resolve_service,
    state_dir,
    token_file,
)
from .grouping import build_emudeck, build_steam
from .lock import file_lock
from .manifest import ScanEntry, compute_digest
from .paths import default_roots
from .payload import PayloadTooLarge, build_check_body, build_payload
from .schedule import install as install_schedule
from .schedule import read_old_shell_schedules, status_line
from .schedule import uninstall as uninstall_schedule
from .status import format_status, read_last_run, write_last_run

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
    from .auth import device_login

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
    from .config import config_dir, machine_id_file

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
