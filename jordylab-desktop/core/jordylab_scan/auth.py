"""Per-machine ingestion token (spec 019 US5).

The admin creates a token in the users area ("Add machine") and hands it over
out of band. The client reads it from ``JORDYLAB_SCAN_TOKEN`` or prompts for it
once, caches it at ``~/.config/jordylab/scan/token.json`` (mode 0600), and sends
it as ``Authorization: Bearer …`` on every ingest call. A 401 clears the cache,
so the next run asks for a fresh token.
"""

import getpass
import json
import os
import tempfile

from .api import ReauthRequired
from .config import token_file

TOKEN_PREFIX = "jlmt_"
SCAN_TOKEN_ENV = "JORDYLAB_SCAN_TOKEN"


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
    """Writes ``{"token": …}`` with owner-only permissions, atomically."""
    path = path or token_file()
    directory = os.path.dirname(str(path))
    if directory:
        os.makedirs(directory, exist_ok=True)
        os.chmod(directory, 0o700)
    handle, temp_path = tempfile.mkstemp(dir=directory or ".", prefix=".token-")
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as stream:
            json.dump({"token": token}, stream)
        os.chmod(temp_path, 0o600)
        os.replace(temp_path, str(path))
    except OSError:
        try:
            os.unlink(temp_path)
        except OSError:
            pass
        raise

    return path


def clear_token(path=None):
    path = path or token_file()
    try:
        os.unlink(str(path))
    except OSError:
        pass


def prompt_login(path=None, stream=None, prompt=getpass.getpass):
    import sys

    stream = stream or sys.stderr
    stream.write(
        "Paste the machine token from Settings \u2192 Machines "
        "(it starts with jlmt_ and is shown only once):\n"
    )
    stream.flush()
    token = prompt("Token: ").strip()
    if not token.startswith(TOKEN_PREFIX):
        raise ReauthRequired("That does not look like a JordyLab machine token (jlmt_\u2026).")
    save_token(token, path)
    stream.write("Token cached. The next scan uses it automatically.\n")

    return token


def get_token(interactive=False, path=None):
    cached = load_token(path)
    if cached and cached.get("token"):
        return cached["token"]

    from_env = os.environ.get(SCAN_TOKEN_ENV, "").strip()
    if from_env:
        save_token(from_env, path)

        return from_env

    if not interactive:
        raise ReauthRequired(
            "No machine token cached. Create one in Settings \u2192 Machines, then run "
            "`python jordylab-scan.py login` or set JORDYLAB_SCAN_TOKEN."
        )

    return prompt_login(path)


# The rest of the client calls this name; it now resolves the machine token.
def get_access_token(cfg=None, interactive=False, path=None, **_ignored):
    return get_token(interactive=interactive, path=path)
