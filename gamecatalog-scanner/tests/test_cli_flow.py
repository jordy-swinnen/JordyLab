import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

from jordylab_scan import __main__ as cli
from jordylab_scan import auth, schedule
from jordylab_scan.config import token_file


class _State:
    def __init__(self):
        self.scan_needed = True
        self.outcome = "APPLIED"
        self.reason = None
        self.requests = []


def _handler_factory(state):
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):  # noqa: N802 - http.server API
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length) or b"{}")
            state.requests.append((self.path, body))
            if self.path.endswith("/check"):
                payload = {"scanNeeded": state.scan_needed, "sourceEnabled": True}
            else:
                payload = {
                    "outcome": state.outcome,
                    "sourceEnabled": True,
                    "reason": state.reason,
                    "counts": {
                        "submitted": 1,
                        "added": 1,
                        "updated": 0,
                        "removed": 0,
                        "rejected": 0,
                    },
                }
            encoded = json.dumps(payload).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def log_message(self, *args):
            return

    return Handler


@pytest.fixture
def ingest_server():
    state = _State()
    server = ThreadingHTTPServer(("127.0.0.1", 0), _handler_factory(state))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield state, f"http://127.0.0.1:{server.server_address[1]}"
    finally:
        server.shutdown()
        server.server_close()


@pytest.fixture
def rom_root(tmp_path):
    root = tmp_path / "roms"
    (root / "snes").mkdir(parents=True)
    (root / "snes" / "Game (USA).sfc").write_text("rom", encoding="utf-8")

    return root


def _cache_token():
    auth.save_token(
        {"access_token": "t", "refresh_token": "r", "expires_in": 3600, "obtained_at": time.time()}
    )


def test_scan_applied(ingest_server, rom_root, monkeypatch):
    state, url = ingest_server
    monkeypatch.setenv("JORDYLAB_SCAN_BACKEND_URL", url)
    _cache_token()

    code = cli.main(["scan", "--path", str(rom_root), "--library", "EMUDECK"])

    assert code == 0
    assert any(path.endswith("/scan") for path, _ in state.requests)


def test_scan_no_change_uploads_nothing(ingest_server, rom_root, monkeypatch):
    state, url = ingest_server
    state.scan_needed = False
    monkeypatch.setenv("JORDYLAB_SCAN_BACKEND_URL", url)
    _cache_token()

    code = cli.main(["scan", "--path", str(rom_root), "--library", "EMUDECK"])

    assert code == 0
    assert not any(path.endswith("/scan") for path, _ in state.requests)
    assert any(path.endswith("/check") for path, _ in state.requests)


def test_scan_force_uploads_when_unchanged(ingest_server, rom_root, monkeypatch):
    state, url = ingest_server
    state.scan_needed = False
    monkeypatch.setenv("JORDYLAB_SCAN_BACKEND_URL", url)
    _cache_token()

    code = cli.main(["scan", "--path", str(rom_root), "--library", "EMUDECK", "--force"])

    assert code == 0
    scan_requests = [body for path, body in state.requests if path.endswith("/scan")]
    assert scan_requests and scan_requests[0]["force"] is True


def test_scan_rejected_returns_four(ingest_server, rom_root, monkeypatch):
    state, url = ingest_server
    state.outcome = "REJECTED"
    state.reason = "SNAPSHOT_SHRINK_SUSPECT"
    monkeypatch.setenv("JORDYLAB_SCAN_BACKEND_URL", url)
    _cache_token()

    code = cli.main(["scan", "--path", str(rom_root), "--library", "EMUDECK"])

    assert code == 4


def test_scan_without_roots_is_noop(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))

    assert cli.main(["scan", "--library", "EMUDECK"]) == 0


def test_install_and_uninstall(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(schedule.sys, "platform", "linux")
    monkeypatch.setattr(schedule, "_run", lambda command: 0)

    assert cli.main(["install", "--library", "EMUDECK"]) == 0
    unit = tmp_path / ".config" / "systemd" / "user" / "jordylab-scan.timer"
    assert unit.is_file()

    assert cli.main(["uninstall", "--purge"]) == 0
    assert not unit.exists()


def test_login_command(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    called = {}

    def fake_device_login(cfg, **kwargs):
        called["client_id"] = cfg.client_id
        auth.save_token({"access_token": "a", "refresh_token": "r", "expires_in": 3600})
        return {"access_token": "a"}

    monkeypatch.setattr(auth, "device_login", fake_device_login)
    monkeypatch.setattr(cli, "resolve_library_type", lambda args, constants: "EMUDECK")

    assert cli.main(["login", "--library", "EMUDECK"]) == 0
    assert called["client_id"] == "gamecatalog-script"
    assert token_file().is_file()


def test_status_warns_about_old_shell_schedule(tmp_path, monkeypatch, capsys):
    monkeypatch.setenv("HOME", str(tmp_path))
    unit_dir = tmp_path / ".config" / "systemd" / "user"
    unit_dir.mkdir(parents=True)
    (unit_dir / "jordylab-scan-old.service").write_text(
        "ExecStart=/bin/bash /home/x/jordylab-scan-steam.sh", encoding="utf-8"
    )

    assert cli.main(["status"]) == 0
    assert "WARNING" in capsys.readouterr().out
