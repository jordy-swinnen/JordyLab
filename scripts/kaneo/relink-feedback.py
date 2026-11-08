#!/usr/bin/env python3
"""Prints the reviewed UPDATE statements that point stored idea-box links from Linear to Kaneo (spec 022, FR-027).

Input, on stdin: one row per line, `<feedback_ticket id><TAB><backlog_ticket_url>`, from a read-only select, e.g.

    psql -At -F $'\t' -c "select id, backlog_ticket_url from feedback.feedback_ticket where backlog_ticket_id is not null"

A header line (`id<TAB>...`) is skipped. Only ids and URLs are read; nothing else from the row is used.

For each Linear link the JOR-n key is taken from the URL, resolved with GET /api/task/by-ticket-id/<key>, and the
task's number must equal n. Output, on stdout: one UPDATE statement per resolved row, for the owner to review and
run. This script never runs them and never connects to a database. The summary and the unresolved rows go to stderr.

Environment: KANEO_BASE_URL and KANEO_API_KEY (required; the key is never printed), KANEO_PROJECT_KEY (default JOR),
KANEO_TICKET_URL_TEMPLATE (default: the backend's board link), KANEO_WORKSPACE_ID (used when the reply has no
workspace id).

Exit codes: 0 every Linear link resolved (or none), 1 configuration or input error, 2 the key was refused and the run
stopped, 4 some links could not be resolved (listed on stderr; the rest are printed).
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_TEMPLATE = "{baseUrl}/dashboard/workspace/{workspaceId}/project/{projectId}/board"
DEFAULT_PROJECT_KEY = "JOR"
LINEAR_HOSTS = ("linear.app", "www.linear.app")
KEY_PATTERN = re.compile(r"/issue/([A-Z][A-Z0-9]*-[0-9]+)(?:[/?#]|$)")
UUID_PATTERN = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
TASK_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]+$")
REQUEST_TIMEOUT_SECONDS = 15
MAX_RATE_LIMIT_RETRIES = 1
MAX_RETRY_AFTER_SECONDS = 60
HEADER_ID = "id"

EXIT_OK = 0
EXIT_CONFIGURATION = 1
EXIT_KEY_REFUSED = 2
EXIT_UNRESOLVED = 4


class KeyRefused(Exception):
    pass


class LookupFailed(Exception):
    pass


def parse_arguments(argv):
    parser = argparse.ArgumentParser(description="Print UPDATE statements that relink idea-box links to Kaneo.")
    parser.add_argument("--interval", type=float, default=0.7,
                        help="seconds to wait between lookups (about 85 a minute, under the 100 a minute limit)")
    return parser.parse_args(argv)


def parse_rows(text):
    rows = []
    for number, line in enumerate(text.splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != 2:
            raise ValueError(f"line {number} is not an id and a URL separated by a tab")
        if parts[0].strip() == HEADER_ID:
            continue
        rows.append((number, parts[0].strip(), parts[1].strip()))

    return rows


def error_code(error):
    try:
        body = json.loads(error.read().decode("utf-8"))
    except (ValueError, UnicodeDecodeError, OSError):
        return "NO_CODE"
    code = body.get("code") if isinstance(body, dict) else None

    return code if isinstance(code, str) and code else "NO_CODE"


def retry_after_seconds(error):
    try:
        seconds = int(error.headers.get("Retry-After", "1"))
    except (TypeError, ValueError):
        seconds = 1

    return min(max(seconds, 0), MAX_RETRY_AFTER_SECONDS)


def fetch_task(base_url, api_key, key):
    url = f"{base_url}/api/task/by-ticket-id/{urllib.parse.quote(key)}"
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {api_key}", "Accept": "application/json"})
    for attempt in range(MAX_RATE_LIMIT_RETRIES + 1):
        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            if error.code in (401, 403):
                raise KeyRefused(error.code) from None
            if error.code == 429 and attempt < MAX_RATE_LIMIT_RETRIES:
                time.sleep(retry_after_seconds(error))
                continue
            raise LookupFailed(f"HTTP {error.code} {error_code(error)}") from None
        except (urllib.error.URLError, OSError, ValueError):
            raise LookupFailed("the request failed") from None

    raise LookupFailed("rate limited")


def sql_literal(value):
    return "'" + value.replace("'", "''") + "'"


def ticket_url_for(template, base_url, workspace_id, project_id, task_id, key):
    return (template.replace("{baseUrl}", base_url)
            .replace("{workspaceId}", workspace_id)
            .replace("{projectId}", project_id)
            .replace("{taskId}", task_id)
            .replace("{key}", key))


def relink(rows, base_url, api_key, template, project_key, fallback_workspace_id, interval):
    updates = 0
    skipped = 0
    unresolved = []
    looked_up_once = False
    for number, row_id, linear_url in rows:
        parsed = urllib.parse.urlsplit(linear_url)
        if parsed.hostname not in LINEAR_HOSTS:
            skipped += 1
            continue
        if not UUID_PATTERN.match(row_id):
            unresolved.append((f"row {number}", "the row id is not a UUID"))
            continue
        match = KEY_PATTERN.search(parsed.path)
        if not match or not match.group(1).startswith(project_key + "-"):
            unresolved.append((f"row {number}", f"the Linear link has no {project_key} key"))
            continue
        key = match.group(1)
        if looked_up_once and interval > 0:
            time.sleep(interval)
        looked_up_once = True
        try:
            task = fetch_task(base_url, api_key, key)
        except KeyRefused as refusal:
            print(f"Kaneo refused the key (HTTP {refusal.args[0]}); the run stopped and nothing further was "
                  "looked up.", file=sys.stderr)
            return None, EXIT_KEY_REFUSED
        except LookupFailed as failure:
            unresolved.append((key, str(failure)))
            continue
        if str(task.get("number")) != key.split("-", 1)[1]:
            unresolved.append((key, "the task number does not match the Linear link"))
            continue
        task_id = str(task.get("id") or "")
        project_id = str(task.get("projectId") or "")
        workspace_id = str(task.get("workspaceId") or fallback_workspace_id)
        if not (TASK_ID_PATTERN.match(task_id) and project_id and workspace_id):
            unresolved.append((key, "the reply has no usable task, project or workspace id"))
            continue
        ticket_url = ticket_url_for(template, base_url, workspace_id, project_id, task_id, key)
        print(f"UPDATE feedback.feedback_ticket SET backlog_ticket_id = {sql_literal(task_id)}, "
              f"backlog_ticket_url = {sql_literal(ticket_url)} WHERE id = {sql_literal(row_id)};")
        updates += 1

    return (updates, skipped, unresolved), EXIT_OK


def main(argv=None):
    arguments = parse_arguments(sys.argv[1:] if argv is None else argv)
    base_url = os.environ.get("KANEO_BASE_URL", "").strip().rstrip("/")
    api_key = os.environ.get("KANEO_API_KEY", "")
    if not base_url or not api_key:
        print("KANEO_BASE_URL and KANEO_API_KEY must both be set in the environment.", file=sys.stderr)
        return EXIT_CONFIGURATION
    template = os.environ.get("KANEO_TICKET_URL_TEMPLATE") or DEFAULT_TEMPLATE
    project_key = os.environ.get("KANEO_PROJECT_KEY") or DEFAULT_PROJECT_KEY
    fallback_workspace_id = os.environ.get("KANEO_WORKSPACE_ID", "")
    try:
        rows = parse_rows(sys.stdin.read())
    except ValueError as problem:
        print(f"input error: {problem}", file=sys.stderr)
        return EXIT_CONFIGURATION

    summary, exit_code = relink(rows, base_url, api_key, template, project_key, fallback_workspace_id,
                                arguments.interval)
    if summary is None:
        return exit_code
    updates, skipped, unresolved = summary
    print(f"{updates} UPDATE statement(s) printed; {skipped} row(s) skipped (not a Linear link); "
          f"{len(unresolved)} unresolved.", file=sys.stderr)
    for label, reason in unresolved:
        print(f"unresolved {label}: {reason}", file=sys.stderr)

    return EXIT_UNRESOLVED if unresolved else EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
