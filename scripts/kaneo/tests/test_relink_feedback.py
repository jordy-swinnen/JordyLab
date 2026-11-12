"""Tests for scripts/kaneo/relink-feedback.py (spec 022, T050).

A fake Kaneo serves the by-ticket-id lookup. The script is run as a subprocess with `python -I`, the way the owner
runs it, so the tests see exactly what is printed.
"""

import http.server
import json
import os
import pathlib
import subprocess
import sys
import threading
import urllib.parse


SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "relink-feedback.py"
ROW_ID = "11111111-2222-3333-4444-555555555555"
OTHER_ROW_ID = "66666666-7777-8888-9999-000000000000"
LINEAR_URL = "https://linear.app/jordylab/issue/JOR-123/grid-loses-my-scroll-position"
API_KEY = "kaneo-test-secret-key-0123"
TASK_BODY = {
    "id": "task-abc",
    "number": 123,
    "projectId": "proj-1",
    "workspaceId": "ws-1",
    "title": "Grid loses my scroll position",
    "status": "planned",
}


class FakeKaneo:
    """Serves a list of scripted responses per path; the last one repeats."""

    def __init__(self, routes):
        self.routes = {path: list(responses) for path, responses in routes.items()}
        self.requests = []
        fake = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                path = urllib.parse.urlsplit(self.path).path
                fake.requests.append((path, self.headers.get("Authorization")))
                status, headers, body = fake.respond(path)
                payload = json.dumps(body).encode()
                self.send_response(status)
                for name, value in headers.items():
                    self.send_header(name, value)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, format_string, *arguments):
                return

        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.base_url = f"http://127.0.0.1:{self.server.server_address[1]}"
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def respond(self, path):
        responses = self.routes.get(path)
        if not responses:
            return 404, {}, {"message": "not found", "code": "NOT_FOUND"}
        if len(responses) > 1:
            return responses.pop(0)
        return responses[0]

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *exc_info):
        self.server.shutdown()
        self.server.server_close()


def lookup_path(key):
    return f"/api/task/by-ticket-id/{key}"


def ok(body):
    return 200, {}, body


def run_script(stdin_text, base_url, api_key=API_KEY):
    environment = {
        "PATH": os.environ.get("PATH", ""),
        "KANEO_BASE_URL": base_url,
        "KANEO_API_KEY": api_key,
    }
    return subprocess.run(
        [sys.executable, "-I", str(SCRIPT), "--interval", "0"],
        input=stdin_text,
        capture_output=True,
        text=True,
        env=environment,
        timeout=60,
    )


def rows(*pairs):
    return "".join(f"{row_id}\t{url}\n" for row_id, url in pairs)


def test_prints_one_update_per_linear_link_with_the_kaneo_ticket_id_and_url():
    with FakeKaneo({lookup_path("JOR-123"): [ok(TASK_BODY)]}) as fake:
        result = run_script(rows((ROW_ID, LINEAR_URL)), fake.base_url)

    expected_url = f"{fake.base_url}/dashboard/workspace/ws-1/project/proj-1/board"
    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == (
        "UPDATE feedback.feedback_ticket SET backlog_ticket_id = 'task-abc', "
        f"backlog_ticket_url = '{expected_url}' WHERE id = '{ROW_ID}';"
    )


def test_takes_the_key_from_a_linear_url_with_a_title_suffix_and_without_one():
    short_url = "https://linear.app/jordylab/issue/JOR-123"
    with FakeKaneo({lookup_path("JOR-123"): [ok(TASK_BODY)]}) as fake:
        result = run_script(rows((ROW_ID, short_url)), fake.base_url)

    assert result.returncode == 0, result.stderr
    assert "backlog_ticket_id = 'task-abc'" in result.stdout


def test_lists_unresolved_keys_and_prints_no_update_for_them():
    with FakeKaneo({lookup_path("JOR-999"): [(404, {}, {"message": "Task not found", "code": "NOT_FOUND"})]}) as fake:
        result = run_script(rows((ROW_ID, "https://linear.app/jordylab/issue/JOR-999/gone")), fake.base_url)

    assert result.returncode == 4
    assert "JOR-999" in result.stderr
    assert "UPDATE" not in result.stdout


def test_skips_rows_that_are_not_linear_links_without_a_lookup():
    kaneo_url = "http://127.0.0.1:1/dashboard/workspace/ws-1/project/proj-1/board"
    with FakeKaneo({}) as fake:
        result = run_script(rows((ROW_ID, kaneo_url)), fake.base_url)

    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == ""
    assert fake.requests == []


def test_stops_at_a_401_without_further_requests():
    with FakeKaneo({lookup_path("JOR-123"): [(401, {}, {"message": "bad key", "code": "UNAUTHORIZED"})],
                    lookup_path("JOR-124"): [ok(dict(TASK_BODY, number=124))]}) as fake:
        result = run_script(
            rows((ROW_ID, LINEAR_URL), (OTHER_ROW_ID, "https://linear.app/jordylab/issue/JOR-124/next")),
            fake.base_url,
        )

    assert result.returncode == 2
    assert [path for path, _ in fake.requests] == [lookup_path("JOR-123")]
    assert "UPDATE" not in result.stdout


def test_refuses_a_task_whose_number_differs_from_the_linear_link():
    with FakeKaneo({lookup_path("JOR-123"): [ok(dict(TASK_BODY, number=124))]}) as fake:
        result = run_script(rows((ROW_ID, LINEAR_URL)), fake.base_url)

    assert result.returncode == 4
    assert "JOR-123" in result.stderr
    assert "UPDATE" not in result.stdout


def test_prints_only_ids_urls_and_counts_never_the_key_or_titles():
    with FakeKaneo({lookup_path("JOR-123"): [ok(TASK_BODY)]}) as fake:
        result = run_script(rows((ROW_ID, LINEAR_URL)), fake.base_url)

    output = result.stdout + result.stderr
    assert API_KEY not in output
    assert "Authorization" not in output
    assert TASK_BODY["title"] not in output


def test_retries_a_rate_limited_lookup_once_after_retry_after():
    with FakeKaneo({lookup_path("JOR-123"): [
        (429, {"Retry-After": "0"}, {"message": "slow down", "code": "RATE_LIMITED"}),
        ok(TASK_BODY),
    ]}) as fake:
        result = run_script(rows((ROW_ID, LINEAR_URL)), fake.base_url)

    assert result.returncode == 0, result.stderr
    assert len(fake.requests) == 2
    assert "backlog_ticket_id = 'task-abc'" in result.stdout


def test_sends_the_key_only_as_a_bearer_token():
    with FakeKaneo({lookup_path("JOR-123"): [ok(TASK_BODY)]}) as fake:
        run_script(rows((ROW_ID, LINEAR_URL)), fake.base_url)

    assert fake.requests == [(lookup_path("JOR-123"), f"Bearer {API_KEY}")]
