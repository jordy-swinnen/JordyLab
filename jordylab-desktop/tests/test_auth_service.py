"""Connect flow tests: loopback callback, state validation, token caching."""

import json
import threading
import urllib.request

import pytest

from jordylab_desktop.services.auth_service import ConnectError, ConnectFlow


def _flow(tmp_path, port=0):
    opened = []

    flow = ConnectFlow(
        "http://backend.test",
        port=port,
        token_path=tmp_path / "token.json",
        open_browser=lambda url: opened.append(url),
        timeout=5,
    )

    return flow, opened


def test_callback_stores_token(tmp_path):
    flow, opened = _flow(tmp_path)
    flow.start()
    try:
        assert flow.port > 0
        assert opened and f"state={flow.state}" in opened[0]
        urllib.request.urlopen(
            f"http://127.0.0.1:{flow.port}/callback?state={flow.state}&token=jlmt_abc"
        ).read()
        token = flow.wait(timeout=2)
    finally:
        flow.stop()

    assert token == "jlmt_abc"
    assert json.loads((tmp_path / "token.json").read_text()) == {"token": "jlmt_abc"}


def test_state_mismatch_is_refused(tmp_path):
    import urllib.error

    flow, _opened = _flow(tmp_path)
    flow.start()
    try:
        with pytest.raises(urllib.error.HTTPError):
            urllib.request.urlopen(
                f"http://127.0.0.1:{flow.port}/callback?state=wrong&token=jlmt_abc"
            ).read()
        with pytest.raises(ConnectError):
            flow.wait(timeout=2)
    finally:
        flow.stop()

    assert not (tmp_path / "token.json").exists()


def test_timeout(tmp_path):
    flow, _opened = _flow(tmp_path)
    flow.start()
    try:
        with pytest.raises(ConnectError):
            flow.wait(timeout=0.1)
    finally:
        flow.stop()


def test_connect_url_contains_machine(tmp_path):
    flow = ConnectFlow("https://jordylab.be/", token_path=tmp_path / "token.json",
                       hostname="jordybox")
    assert flow.connect_url.startswith("https://jordylab.be/connect/desktop?")
    assert "machine=jordybox" in flow.connect_url
