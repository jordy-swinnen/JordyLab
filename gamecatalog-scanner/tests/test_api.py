import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

from jordylab_scan import api
from jordylab_scan.config import AppConfig


class _Handler(BaseHTTPRequestHandler):
    def do_POST(self):  # noqa: N802 - http.server API
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        status, payload = self.server.next_response
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        return


@pytest.fixture
def api_server():
    server = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
    server.next_response = (200, {"ok": True})
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield server
    finally:
        server.shutdown()
        server.server_close()


def test_http_error_mapping():
    assert isinstance(api._http_error(401, {}), api.ReauthRequired)
    assert api._http_error(401, {}).exit_code == 2
    assert isinstance(api._http_error(403, {}), api.ConfigError)
    assert api._http_error(403, {}).exit_code == 6
    rejected = api._http_error(413, {})
    assert isinstance(rejected, api.ServerRejected)
    assert rejected.exit_code == 4
    assert rejected.reason == "PAYLOAD_TOO_LARGE"
    assert isinstance(api._http_error(400, {"reason": "NOPE"}), api.ServerRejected)
    assert isinstance(api._http_error(503, {}), api.NetworkError)


def test_post_with_retry_retries_then_succeeds(monkeypatch):
    calls = {"count": 0}

    def fake_post(url, body, token=None, opener=None):
        calls["count"] += 1
        if calls["count"] < 3:
            raise api.NetworkError("boom")
        return 200, {"ok": True}

    monkeypatch.setattr(api, "post_json", fake_post)
    delays = []

    status, payload = api.post_with_retry("http://x", {}, sleep=delays.append, now=lambda: 0.0)

    assert status == 200
    assert payload == {"ok": True}
    assert calls["count"] == 3
    assert len(delays) == 2


def test_post_with_retry_gives_up(monkeypatch):
    def fake_post(url, body, token=None, opener=None):
        raise api.NetworkError("down")

    monkeypatch.setattr(api, "post_json", fake_post)
    with pytest.raises(api.NetworkError):
        api.post_with_retry("http://x", {}, sleep=lambda _: None, now=lambda: 0.0)


def test_post_with_retry_does_not_retry_rejections(monkeypatch):
    calls = {"count": 0}

    def fake_post(url, body, token=None, opener=None):
        calls["count"] += 1
        raise api.ServerRejected("no")

    monkeypatch.setattr(api, "post_json", fake_post)
    with pytest.raises(api.ServerRejected):
        api.post_with_retry("http://x", {}, sleep=lambda _: None, now=lambda: 0.0)
    assert calls["count"] == 1


def test_post_json_reads_response(api_server):
    status, payload = api.post_json(
        f"http://127.0.0.1:{api_server.server_address[1]}/x", {"a": 1}, token="t"
    )

    assert status == 200
    assert payload == {"ok": True}


def test_post_json_maps_http_error(api_server):
    api_server.next_response = (401, {"error": "unauthorized"})

    with pytest.raises(api.ReauthRequired):
        api.post_json(f"http://127.0.0.1:{api_server.server_address[1]}/x", {}, token="t")


def test_post_json_maps_network_error():
    with pytest.raises(api.NetworkError):
        api.post_json("http://127.0.0.1:1/x", {}, token="t")


def test_safe_json_handles_garbage(api_server):
    class _FakeError:
        def read(self):
            return b"not json"

    assert api._safe_json(_FakeError()) == {}


def test_scan_error_default_exit_code():
    assert api.ScanError("boom").exit_code == 6


def test_check_and_scan_build_urls(monkeypatch):
    urls = []

    def fake_post(url, body, token=None, opener=None):
        urls.append((url, token))
        return 200, {}

    monkeypatch.setattr(api, "post_json", fake_post)
    cfg = AppConfig(library_type="STEAM", backend_url="http://host:8080/")
    api.check(cfg, {}, "t")
    api.scan(cfg, {}, "t")

    assert urls[0][0] == "http://host:8080/api/gamecatalog/ingest/check"
    assert urls[1][0] == "http://host:8080/api/gamecatalog/ingest/scan"
    assert urls[0][1] == "t"
