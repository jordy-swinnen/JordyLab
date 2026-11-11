"""Connect this PC: browser sign-in with a loopback callback.

The app opens the JordyLab connect page (``/connect/desktop``) in the system
browser. The admin signs in with the existing session login; the page creates a
machine token through the existing ``POST /api/identity/machines`` endpoint and
redirects to ``http://127.0.0.1:<port>/callback?state=...&token=...``. The app
validates the ``state`` it generated, stores the token in the scanner's cache
(``~/.config/jordylab/scan/token.json``, mode 0600) and closes the listener.

Admin-only: the connect page is behind the admin session guard, so a guest can
never create a machine token.
"""

import secrets
import threading
import urllib.parse
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from jordylab_scan.auth import save_token

CALLBACK_PATH = "/callback"
DEFAULT_PORT = 19878
DEFAULT_TIMEOUT_SECONDS = 300

SUCCESS_HTML = """<!doctype html>
<html><head><meta charset="utf-8"><title>JordyLab connected</title></head>
<body style="font-family: system-ui; padding: 3rem; max-width: 32rem;">
<h1>Connected</h1>
<p>This PC is linked to JordyLab. You can close this tab and return to the app.</p>
</body></html>"""

ERROR_HTML = """<!doctype html>
<html><head><meta charset="utf-8"><title>JordyLab connect failed</title></head>
<body style="font-family: system-ui; padding: 3rem; max-width: 32rem;">
<h1>Connect failed</h1>
<p>The request did not complete. Return to the app and try again.</p>
</body></html>"""


class ConnectError(Exception):
    """The sign-in did not complete (timeout, state mismatch, no token)."""


class _CallbackHandler(BaseHTTPRequestHandler):
    server_version = "JordyLabDesktop/1.0"

    def do_GET(self):  # noqa: N802 - http.server API
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        if parsed.path != CALLBACK_PATH:
            self._respond(404, ERROR_HTML)

            return
        state = (query.get("state") or [""])[0]
        token = (query.get("token") or [""])[0]
        if state != self.server.expected_state or not token:
            self._respond(400, ERROR_HTML)
            self.server.failure = "state mismatch or missing token"

            return
        self.server.received_token = token
        self.server.done.set()
        self._respond(200, SUCCESS_HTML)

    def _respond(self, status, body):
        encoded = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, *arguments):  # noqa: D401 - silence the callback server
        return


class ConnectFlow:
    """Runs the browser sign-in once; ``wait`` blocks until the token arrives."""

    def __init__(self, backend_url, port=DEFAULT_PORT, timeout=DEFAULT_TIMEOUT_SECONDS,
                 token_path=None, open_browser=webbrowser.open, hostname=None):
        self.backend_url = backend_url.rstrip("/")
        self.port = port
        self.timeout = timeout
        self.token_path = token_path
        self.open_browser = open_browser
        self.hostname = hostname
        self.state = secrets.token_urlsafe(32)
        self._server = None
        self._thread = None

    @property
    def connect_url(self):
        query = urllib.parse.urlencode({
            "state": self.state,
            "port": str(self.port),
        })
        if self.hostname:
            query += "&" + urllib.parse.urlencode({"machine": self.hostname})

        return f"{self.backend_url}/connect/desktop?{query}"

    def start(self):
        """Start the loopback listener and open the browser."""
        server = ThreadingHTTPServer(("127.0.0.1", self.port), _CallbackHandler)
        server.expected_state = self.state
        server.received_token = None
        server.failure = None
        server.done = threading.Event()
        self._server = server
        if self.port == 0:
            self.port = server.server_address[1]
        self._thread = threading.Thread(target=server.serve_forever, daemon=True)
        self._thread.start()
        self.open_browser(self.connect_url)

    def wait(self, timeout=None):
        """Block until the callback arrives; returns the machine token."""
        if self._server is None:
            raise ConnectError("connect flow not started")
        deadline = (timeout if timeout is not None else self.timeout)
        if not self._server.done.wait(deadline):
            self.stop()
            raise ConnectError("sign-in did not complete in time")
        token = self._server.received_token
        self.stop()
        if not token:
            raise ConnectError(self._server.failure or "no machine token received")
        save_token(token, self.token_path)

        return token

    def stop(self):
        if self._server is not None:
            try:
                self._server.shutdown()
            finally:
                self._server.server_close()
                self._server = None
