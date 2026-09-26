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

from .api import NetworkError, ReauthRequired
from .config import token_file

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
