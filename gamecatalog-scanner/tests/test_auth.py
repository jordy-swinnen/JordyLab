import json
import threading
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

from jordylab_scan import auth
from jordylab_scan.api import ReauthRequired
from jordylab_scan.config import AppConfig


def _handler_factory(state):
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):  # noqa: N802 - http.server API
            length = int(self.headers.get("Content-Length", "0"))
            params = urllib.parse.parse_qs(self.rfile.read(length).decode("utf-8"))
            if self.path.endswith("/auth/device"):
                state["device_calls"] += 1
                state["device_body"] = params
                payload = {
                    "device_code": "dev",
                    "user_code": "UC",
                    "verification_uri": "http://verify",
                    "verification_uri_complete": "http://verify?c=1",
                    "expires_in": 300,
                    "interval": 1,
                }
                self._send(200, payload)
            elif self.path.endswith("/token"):
                if params.get("grant_type") == ["refresh_token"]:
                    code, payload = state["refresh"]
                else:
                    responses = state["device_responses"]
                    code, payload = responses.pop(0) if responses else (200, state["token_success"])
                self._send(code, payload)
            elif self.path.endswith("/revoke"):
                state["revoked"] = True
                self._send(200, {})
            else:
                self._send(404, {})

        def _send(self, code, payload):
            body = json.dumps(payload).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            return

    return Handler


@pytest.fixture
def oauth_server():
    state = {
        "device_calls": 0,
        "device_responses": [],
        "token_success": {"access_token": "acc", "refresh_token": "r1", "expires_in": 3600},
        "refresh": (200, {"access_token": "new", "refresh_token": "r2", "expires_in": 3600}),
        "revoked": False,
    }
    server = ThreadingHTTPServer(("127.0.0.1", 0), _handler_factory(state))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield state, f"http://127.0.0.1:{server.server_address[1]}"
    finally:
        server.shutdown()
        server.server_close()


def _config(url):
    return AppConfig(
        library_type="EMUDECK", keycloak_url=url, realm="jordylab", client_id="gamecatalog-script"
    )


def test_device_login_happy_path(oauth_server):
    _, url = oauth_server
    token = auth.device_login(_config(url), sleep=lambda _: None)

    assert token["access_token"] == "acc"
    assert auth.load_token()["refresh_token"] == "r1"


def test_device_login_requests_offline_scope(oauth_server):
    state, url = oauth_server
    auth.device_login(_config(url), sleep=lambda _: None)

    assert state["device_body"]["scope"] == ["openid offline_access"]


def test_poll_handles_pending_and_slow_down(oauth_server):
    state, url = oauth_server
    state["device_responses"] = [
        (400, {"error": "authorization_pending"}),
        (400, {"error": "slow_down"}),
        (200, {"access_token": "acc", "refresh_token": "r1", "expires_in": 3600}),
    ]

    token = auth.device_login(_config(url), sleep=lambda _: None)

    assert token["access_token"] == "acc"


def test_poll_aborts_on_access_denied(oauth_server):
    state, url = oauth_server
    state["device_responses"] = [(400, {"error": "access_denied", "error_description": "no"})]

    with pytest.raises(ReauthRequired):
        auth.device_login(_config(url), sleep=lambda _: None)


def test_refresh_access_token(oauth_server):
    _, url = oauth_server
    refreshed = auth.refresh_access_token(_config(url), "r1")

    assert refreshed["access_token"] == "new"
    assert refreshed["refresh_token"] == "r2"


def test_refresh_failure_raises_reauth(oauth_server):
    state, url = oauth_server
    state["refresh"] = (400, {"error": "invalid_grant", "error_description": "expired"})

    with pytest.raises(ReauthRequired):
        auth.refresh_access_token(_config(url), "bad")


def test_non_interactive_never_starts_device_flow_on_missing_token(oauth_server):
    state, url = oauth_server

    with pytest.raises(ReauthRequired) as error:
        auth.get_access_token(_config(url), interactive=False)

    assert error.value.exit_code == 2
    assert state["device_calls"] == 0


def test_non_interactive_refresh_failure_exits_two(oauth_server):
    state, url = oauth_server
    state["refresh"] = (400, {"error": "invalid_grant"})
    auth.save_token(
        {"access_token": "old", "refresh_token": "bad", "expires_in": 1, "obtained_at": 0}
    )

    with pytest.raises(ReauthRequired) as error:
        auth.get_access_token(_config(url), interactive=False)

    assert error.value.exit_code == 2
    assert state["device_calls"] == 0


def test_interactive_falls_back_to_device_flow(oauth_server):
    state, url = oauth_server
    state["refresh"] = (400, {"error": "invalid_grant"})
    auth.save_token(
        {"access_token": "old", "refresh_token": "bad", "expires_in": 1, "obtained_at": 0}
    )

    token = auth.get_access_token(_config(url), interactive=True, sleep=lambda _: None)

    assert token == "acc"
    assert state["device_calls"] == 1


def test_get_access_token_returns_cached_when_valid(oauth_server):
    state, url = oauth_server
    import time

    auth.save_token(
        {
            "access_token": "cached",
            "refresh_token": "r1",
            "expires_in": 3600,
            "obtained_at": time.time(),
        }
    )

    assert auth.get_access_token(_config(url), interactive=False) == "cached"
    assert state["device_calls"] == 0


def test_revoke_is_best_effort(oauth_server):
    state, url = oauth_server
    assert auth.revoke(_config(url), "r1") is True
    assert state["revoked"] is True


def test_revoke_without_token_returns_false():
    assert auth.revoke(_config("http://127.0.0.1:1"), None) is False


def test_revoke_failure_is_swallowed(monkeypatch):
    from jordylab_scan import auth as auth_module

    def boom(url, fields, timeout=30):
        raise auth_module.OAuthError("invalid_request")

    monkeypatch.setattr(auth_module, "post_form", boom)

    assert auth.revoke(_config("http://x"), "token") is False


def test_post_form_network_error():
    from jordylab_scan.api import NetworkError

    with pytest.raises(NetworkError):
        auth.post_form("http://127.0.0.1:1/token", {"grant_type": "refresh_token"})


def test_missing_device_code_raises(oauth_server):
    state, url = oauth_server
    original = auth.request_device_code

    auth.request_device_code = lambda cfg: {}  # type: ignore[assignment]
    try:
        with pytest.raises(ReauthRequired):
            auth.device_login(_config(url), sleep=lambda _: None)
    finally:
        auth.request_device_code = original


def test_load_token_missing_and_corrupt():
    assert auth.load_token() is None
    auth.token_file().parent.mkdir(parents=True, exist_ok=True)
    auth.token_file().write_text("not json", encoding="utf-8")

    assert auth.load_token() is None


def test_save_token_is_private():
    import os
    import stat

    auth.save_token({"access_token": "a"})

    mode = stat.S_IMODE(os.stat(str(auth.token_file())).st_mode)
    assert mode == 0o600


def test_is_expired_variants():
    assert auth._is_expired({}, 1000.0) is True
    assert auth._is_expired({"expires_in": 3600, "obtained_at": 1000.0}, 1010.0) is False
    assert auth._is_expired({"expires_in": 3600, "obtained_at": 1000.0}, 9000.0) is True


def test_interactive_network_failure_propagates(monkeypatch):
    from jordylab_scan.api import NetworkError

    auth.save_token(
        {"access_token": "old", "refresh_token": "r", "expires_in": 1, "obtained_at": 0}
    )

    def boom(cfg, refresh_token, now=auth.time.time):
        raise NetworkError("offline")

    monkeypatch.setattr(auth, "refresh_access_token", boom)

    with pytest.raises(NetworkError):
        auth.get_access_token(_config("http://x"), interactive=True)
