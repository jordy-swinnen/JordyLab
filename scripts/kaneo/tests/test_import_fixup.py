"""Tests for scripts/kaneo/import-fixup.py against a fake Kaneo server (no network, no real key).

Run from the repository root:
    KANEO_FIXUP_MIN_INTERVAL=0 python -I -m pytest scripts/kaneo/tests
"""

import importlib.util
import json
import os
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import pytest

SCRIPT = Path(__file__).resolve().parent.parent / "import-fixup.py"
spec = importlib.util.spec_from_file_location("import_fixup", SCRIPT)
fixup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixup)

API_KEY = "test-key-not-real"


class FakeKaneo:
    """Holds a small JOR project and records every request."""

    def __init__(self, tasks, columns=("todo", "in-progress", "in-review", "done", "canceled", "planned")):
        self.tasks = dict(tasks)  # identifier -> {"id", "number", "status"}
        self.columns = {slug: f"col-{slug}" for slug in columns}
        self.requests = []  # (method, path, body)
        self.refuse = None  # set to a status code to refuse every call
        self.rate_limit_once = False
        self.bad_auth_seen = []

    def handle(self, method, path, body):
        self.requests.append((method, path, body))
        if self.refuse:
            return self.refuse, {"error": "refused"}, {}
        parsed = urlparse(path)
        route = parsed.path
        if method == "GET" and route == "/api/workspace":
            return 200, [{"id": "ws1"}], {}
        if method == "GET" and route == "/api/project":
            assert parse_qs(parsed.query).get("workspaceId") == ["ws1"]
            return 200, [{"id": "p1", "slug": "JOR"}], {}
        if method == "GET" and route == "/api/column/p1":
            return 200, [{"id": cid, "slug": slug} for slug, cid in self.columns.items()], {}
        if method == "POST" and route == "/api/column/p1":
            slug = body["name"].lower()
            self.columns[slug] = f"col-{slug}"
            return 200, {"id": f"col-{slug}", "slug": slug}, {}
        if method == "GET" and route.startswith("/api/task/by-ticket-id/"):
            identifier = route.rsplit("/", 1)[1]
            if identifier not in self.tasks:
                return 404, {"code": "NOT_FOUND"}, {}
            task = self.tasks[identifier]
            return 200, {"id": task["id"], "number": task["number"], "status": task["status"]}, {}
        if method == "PUT" and route.startswith("/api/task/status/"):
            task_id = route.rsplit("/", 1)[1]
            for task in self.tasks.values():
                if task["id"] == task_id:
                    task["status"] = body["status"]
                    return 200, {"id": task_id, "status": body["status"]}, {}
            return 404, {"code": "NOT_FOUND"}, {}
        return 404, {"code": "NOT_FOUND", "path": route}, {}

    def writes(self):
        return [r for r in self.requests if r[0] != "GET"]


def start(fake):
    class Handler(BaseHTTPRequestHandler):
        def _serve(self, method):
            if self.headers.get("Authorization") != f"Bearer {API_KEY}":
                self.send_response(401)
                self.end_headers()
                return
            length = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(length)) if length else None
            if fake.rate_limit_once:
                fake.rate_limit_once = False
                self.send_response(429)
                self.send_header("Retry-After", "0")
                self.end_headers()
                return
            code, payload, headers = fake.handle(method, self.path, body)
            data = json.dumps(payload).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            self._serve("GET")

        def do_POST(self):
            self._serve("POST")

        def do_PUT(self):
            self._serve("PUT")

        def log_message(self, *args):
            pass

    httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    return httpd, f"http://127.0.0.1:{httpd.server_address[1]}"


def export_file(tmp_path, issues, wrapper="list"):
    if wrapper == "list":
        payload = issues
    elif wrapper == "nodes":
        payload = {"data": {"issues": {"nodes": issues}}}
    else:
        payload = {"issues": issues}
    path = tmp_path / "linear-export.json"
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


def issue(identifier, state, labels=("Platform", "Feature")):
    return {"identifier": identifier, "state": {"name": state}, "labels": [{"name": n} for n in labels]}


def run_script(monkeypatch, capsys, base_url, export, apply=False):
    monkeypatch.setenv("KANEO_API_KEY", API_KEY)
    monkeypatch.setenv("KANEO_BASE_URL", base_url)
    monkeypatch.setenv("KANEO_WORKSPACE_ID", "")
    monkeypatch.setenv("KANEO_FIXUP_MIN_INTERVAL", os.environ.get("KANEO_FIXUP_MIN_INTERVAL", "0"))
    argv = ["import-fixup.py", "--export", str(export)] + (["--apply"] if apply else [])
    monkeypatch.setattr(sys, "argv", argv)
    code = fixup.main()
    out = capsys.readouterr()
    return code, out.out, out.err


def test_mapping_covers_every_linear_state_and_duplicates_are_canceled():
    for state in ["Triage", "Backlog", "Drafted", "Specified", "Todo", "In Progress", "In Review", "Done", "Canceled"]:
        assert fixup.target_status(state) is not None, state
    assert fixup.target_status("Duplicate") == "canceled"
    assert fixup.target_status("Backlog") == "planned"
    assert fixup.target_status("Something new") is None


def test_export_shapes_are_all_read(tmp_path):
    issues = [issue("JOR-1", "Todo")]
    for wrapper in ("list", "nodes", "issues"):
        records = fixup.load_export(export_file(tmp_path, issues, wrapper))
        assert [r["identifier"] for r in records] == ["JOR-1"], wrapper


def test_unknown_export_shape_is_refused(tmp_path):
    path = tmp_path / "bad.json"
    path.write_text(json.dumps({"something": "else"}), encoding="utf-8")
    with pytest.raises(fixup.ExportError):
        fixup.load_export(path)


def test_dry_run_writes_nothing(tmp_path, monkeypatch, capsys):
    fake = FakeKaneo({"JOR-1": {"id": "t1", "number": 1, "status": "planned"}})
    httpd, base = start(fake)
    try:
        export = export_file(tmp_path, [issue("JOR-1", "Triage")])
        code, out, _ = run_script(monkeypatch, capsys, base, export)
    finally:
        httpd.shutdown()
    assert code == 0
    assert fake.writes() == []
    assert "would create column triage" in out
    assert "JOR-1 | Triage | planned | triage" in out
    assert "dry run, nothing written" in out


def test_apply_creates_columns_moves_tickets_and_a_second_run_changes_nothing(tmp_path, monkeypatch, capsys):
    fake = FakeKaneo({
        "JOR-1": {"id": "t1", "number": 1, "status": "planned"},
        "JOR-2": {"id": "t2", "number": 2, "status": "planned"},
        "JOR-3": {"id": "t3", "number": 3, "status": "todo"},
    })
    httpd, base = start(fake)
    try:
        export = export_file(tmp_path, [issue("JOR-1", "Triage"), issue("JOR-2", "Backlog"), issue("JOR-3", "Todo")])
        code, _, _ = run_script(monkeypatch, capsys, base, export, apply=True)
        assert code == 0
        assert "triage" in fake.columns  # the one column these tickets need was created
        assert fake.tasks["JOR-1"]["status"] == "triage"
        assert fake.tasks["JOR-2"]["status"] == "planned"  # Backlog stays in the Backlog view
        assert fake.tasks["JOR-3"]["status"] == "todo"
        # One column create (triage) and one move (JOR-1). JOR-2 and JOR-3 were already in place.
        assert [(m, p) for m, p, _ in fake.writes()] == [
            ("POST", "/api/column/p1"),
            ("PUT", "/api/task/status/t1"),
        ]

        code, out, _ = run_script(monkeypatch, capsys, base, export, apply=True)
        assert code == 0
        assert len(fake.writes()) == 2  # idempotent: the second run wrote nothing
        assert "already in place: 3" in out
    finally:
        httpd.shutdown()


def test_missing_ticket_is_reported_not_written(tmp_path, monkeypatch, capsys):
    fake = FakeKaneo({})
    httpd, base = start(fake)
    try:
        code, out, _ = run_script(monkeypatch, capsys, base, export_file(tmp_path, [issue("JOR-9", "Todo")]), apply=True)
    finally:
        httpd.shutdown()
    assert code == 0
    assert "not in Kaneo: 1" in out
    assert [w for w in fake.writes() if w[0] == "PUT"] == []


def test_unknown_state_and_label_problems_are_listed(tmp_path, monkeypatch, capsys):
    fake = FakeKaneo({"JOR-1": {"id": "t1", "number": 1, "status": "planned"}})
    httpd, base = start(fake)
    try:
        issues = [
            issue("JOR-1", "Weird state"),  # unmapped state: listed, not moved
            issue("JOR-4", "Todo", labels=("Platform",)),  # no Type label: a label problem
        ]
        code, out, _ = run_script(monkeypatch, capsys, base, export_file(tmp_path, issues))
    finally:
        httpd.shutdown()
    assert code == 0
    assert "Linear states this script does not map (1): JOR-1 (Weird state)" in out
    assert "tickets without exactly one App and one Type label (1):" in out


def test_key_refused_stops_the_run_with_exit_code_2(tmp_path, monkeypatch, capsys):
    fake = FakeKaneo({"JOR-1": {"id": "t1", "number": 1, "status": "planned"}})
    fake.refuse = 403
    httpd, base = start(fake)
    try:
        code, _, err = run_script(monkeypatch, capsys, base, export_file(tmp_path, [issue("JOR-1", "Todo")]), apply=True)
    finally:
        httpd.shutdown()
    assert code == 2
    assert "refused with 403" in err
    assert len(fake.requests) == 1  # the first call was refused; nothing else was tried


def test_rate_limit_is_waited_out_and_retried(tmp_path, monkeypatch, capsys):
    fake = FakeKaneo({"JOR-1": {"id": "t1", "number": 1, "status": "planned"}})
    fake.rate_limit_once = True
    httpd, base = start(fake)
    try:
        code, out, err = run_script(monkeypatch, capsys, base, export_file(tmp_path, [issue("JOR-1", "Todo")]), apply=True)
    finally:
        httpd.shutdown()
    assert code == 0
    assert "rate limited" in err
    assert fake.tasks["JOR-1"]["status"] == "todo"


def test_api_key_is_never_printed(tmp_path, monkeypatch, capsys):
    fake = FakeKaneo({"JOR-1": {"id": "t1", "number": 1, "status": "planned"}})
    httpd, base = start(fake)
    try:
        code, out, err = run_script(monkeypatch, capsys, base, export_file(tmp_path, [issue("JOR-1", "Todo")]), apply=True)
    finally:
        httpd.shutdown()
    assert code == 0
    assert API_KEY not in out and API_KEY not in err
