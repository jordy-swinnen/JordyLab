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
