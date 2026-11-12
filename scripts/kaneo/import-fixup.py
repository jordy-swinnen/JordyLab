#!/usr/bin/env python3
"""Fix-up after the one-time Linear import into Kaneo (spec 022, data-model "Linear -> Kaneo state mapping").

The Kaneo importer puts Linear's Triage, Drafted and Specified tickets in the Backlog view, and it does not create
those columns. This script reads Linear's JSON export (not the Linear API, so no Linear key is needed) and:

- creates the missing columns triage, drafted and specified in the JOR project;
- moves each ticket to the column its Linear state implies, when it is not there already;
- writes `Spec: specs/<nnn-name>/` as the first description line of each spec ticket (the folder comes from
  `repo_inventory.sh`, run from the repository root, or from a saved copy passed with --inventory);
- reports tickets whose labels do not give exactly one App and one Type label.

It is a dry run unless --apply is passed. Writes are idempotent: a ticket already in its target status or already
carrying its spec line is skipped, so a second run changes nothing. It paces its requests to stay under Kaneo's
limit of 100 requests a minute per key, stops on the first 401 or 403, and waits Retry-After on a 429.

Environment (never printed, never written to a file):
    KANEO_API_KEY        the API key for this one run (required)
    KANEO_BASE_URL       default https://kaneo.jordylab.be
    KANEO_PROJECT_KEY    default JOR
    KANEO_WORKSPACE_ID   optional; otherwise the single workspace is used
    KANEO_FIXUP_INVENTORY_SCRIPT  optional; repo_inventory.sh to run (default: the one in this repository)

Usage (run with -I, because the export is data):
    python3 -I scripts/kaneo/import-fixup.py --export linear-export.json            # dry run
    python3 -I scripts/kaneo/import-fixup.py --export linear-export.json --apply    # writes

Exit codes: 0 done, 1 bad input or a Kaneo error, 2 the key was refused (401/403).
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# Linear state name (lower case) -> Kaneo status. "planned" is the Backlog view, which holds Backlog and Drafted
# until the fix-up moves Drafted and Specified to their own columns (data-model.md).
STATE_TO_STATUS = {
    "triage": "triage",
    "backlog": "planned",
    "drafted": "drafted",
    "specified": "specified",
    "todo": "todo",
    "in progress": "in-progress",
    "in review": "in-review",
    "done": "done",
    "canceled": "canceled",
    "cancelled": "canceled",
    "duplicate": "canceled",
}

# Columns the fix-up must create when they are missing (slug -> display name).
COLUMNS_TO_CREATE = {"triage": "Triage", "drafted": "Drafted", "specified": "Specified"}

APP_LABELS = {"Game Catalog", "Finance News", "Garmin", "Mobile", "Recipe", "Trading", "Platform"}
TYPE_LABELS = {"Bug", "Feature", "Improvement", "Idea", "Chore"}

IDENTIFIER = re.compile(r"^[A-Z][A-Z0-9]*-\d+$")
MAX_WRITES_PER_MINUTE = 40
DEFAULT_MIN_INTERVAL = 0.7  # seconds between requests: about 85 a minute, under the 100 limit
SPEC_LINE = re.compile(r"^Spec:\s*(specs/[A-Za-z0-9._/-]+?)/?\s*$")
PROJECT_SPEC = re.compile(r"Spec:\s*(specs/[A-Za-z0-9._/-]+?)/?(?:\s|$)")
INVENTORY_SCRIPT_ENV = "KANEO_FIXUP_INVENTORY_SCRIPT"
DEFAULT_INVENTORY_SCRIPT = (
    Path(__file__).resolve().parents[2] / ".claude/skills/backlog-sync-drafts/scripts/repo_inventory.sh"
)


class ExportError(Exception):
    """The Linear export is not in a shape this script understands."""


class KaneoError(Exception):
    """Kaneo returned an error this script does not retry."""


class KeyRefused(KaneoError):
    """401 or 403: stop at once, the key is not accepted."""


def find_issue_list(data):
    """Find the list of issues in a Linear export, whatever wrapper it arrives in."""
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ("issues", "nodes"):
            value = data.get(key)
            if isinstance(value, list):
                return value
            if isinstance(value, dict) and isinstance(value.get("nodes"), list):
                return value["nodes"]
        if "data" in data:
            return find_issue_list(data["data"])
    raise ExportError(
        "no list of issues found: expected a JSON array of issues, or an object with 'issues' "
        "(a list, or {'nodes': [...]})"
    )


def label_names(raw_labels):
    """Label names from a Linear issue: {'nodes': [{'name': ...}]}, a list of dicts, or a list of strings."""
    if isinstance(raw_labels, dict):
        raw_labels = raw_labels.get("nodes", [])
    names = []
    for item in raw_labels or []:
        if isinstance(item, dict) and item.get("name"):
            names.append(str(item["name"]))
        elif isinstance(item, str):
            names.append(item)
    return names


def load_export(path):
    """Return one record per issue: identifier, Linear state, labels, description and project. No titles are kept."""
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ExportError(f"cannot read {path}: {exc}") from exc

    records, skipped = [], []
    for raw in find_issue_list(data):
        identifier = (raw.get("identifier") or raw.get("key")) if isinstance(raw, dict) else None
        if not identifier or not IDENTIFIER.match(str(identifier)):
            skipped.append(str(identifier))
            continue
        state = raw.get("state")
        state_name = state.get("name") if isinstance(state, dict) else state
        description = raw.get("description")
        project = raw.get("project")
        if isinstance(project, dict):
            project = {"name": project.get("name"), "description": project.get("description"),
                       "number": project.get("number"), "key": project.get("key")}
        elif project is not None:
            project = {"name": str(project)}
        records.append(
            {
                "identifier": str(identifier),
                "state": str(state_name or ""),
                "labels": label_names(raw.get("labels")),
                "description": description if isinstance(description, str) else "",
                "project": project,
            }
        )
    if skipped:
        print(f"skipped {len(skipped)} record(s) without a JOR-style identifier", file=sys.stderr)
    return records


def target_status(state_name):
    return STATE_TO_STATUS.get(state_name.strip().lower())


def label_problem(labels):
    """Return a short reason when the labels are not exactly one App and one Type, else None."""
    apps = [name for name in labels if name in APP_LABELS]
    types = [name for name in labels if name in TYPE_LABELS]
    if len(apps) == 1 and len(types) == 1:
        return None
    return f"App labels {len(apps)}, Type labels {len(types)}"


def first_line(text):
    """The first non-blank line of a description, stripped."""
    if not isinstance(text, str):
        return ""
    for line in text.splitlines():
        if line.strip():
            return line.strip()
    return ""


def spec_reference(record):
    """The spec a ticket belongs to: its own first line, or its Linear project (description, then name/number)."""
    match = SPEC_LINE.match(first_line(record.get("description")))
    if match:
        return match.group(1)
    project = record.get("project")
    if isinstance(project, str):
        project = {"name": project}
    if isinstance(project, dict):
        description = project.get("description")
        if isinstance(description, str):
            match = PROJECT_SPEC.search(description)
            if match:
                return match.group(1)
        for field in ("name", "key", "number"):
            value = str(project.get(field) or "").strip()
            if value:
                return value
    return None


def index_inventory(data):
    """Index `repo_inventory.sh` items so a reference matches by folder, number, slug or title."""
    index = {"by_key": {}, "by_number": {}, "by_slug": {}, "by_title": {}}
    for item in data if isinstance(data, list) else []:
        if not isinstance(item, dict) or item.get("kind") != "spec":
            continue
        key = str(item.get("key") or "").strip().rstrip("/")
        if not key.startswith("specs/"):
            continue
        index["by_key"][key] = key
        for field, bucket in (("number", "by_number"), ("name", "by_slug"), ("title", "by_title")):
            value = str(item.get(field) or "").strip()
            if value:
                index[bucket][value.lower()] = key
    return index


def load_inventory(args):
    """The spec folders from `repo_inventory.sh`: a saved JSON copy, or the script run from the repository root."""
    if args.inventory:
        try:
            data = json.loads(Path(args.inventory).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ExportError(f"cannot read the inventory {args.inventory}: {exc}") from exc
        return index_inventory(data)

    script = Path(os.environ.get(INVENTORY_SCRIPT_ENV, DEFAULT_INVENTORY_SCRIPT)).resolve()
    if not script.is_file():
        raise ExportError(
            "no spec inventory: pass --inventory with the JSON that repo_inventory.sh prints, or run from the "
            f"repository root (looked for {script})"
        )
    repo_root = script.parents[4]
    try:
        completed = subprocess.run(
            ["sh", str(script)], cwd=repo_root, capture_output=True, text=True, timeout=120, check=False
        )
    except OSError as exc:
        raise ExportError(f"cannot run {script}: {exc}") from exc
    if completed.returncode != 0:
        raise ExportError(f"repo_inventory.sh failed: {completed.stderr.strip() or completed.returncode}")
    try:
        data = json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise ExportError(f"repo_inventory.sh printed no JSON: {exc}") from exc
    return index_inventory(data)


def resolve_spec(reference, index):
    """The inventory folder for a reference (folder, number, slug or title), or None."""
    ref = str(reference or "").strip().strip("/")
    if not ref:
        return None
    full = ref if ref.startswith("specs/") else "specs/" + ref
    if full in index["by_key"]:
        return index["by_key"][full]
    bare = full[len("specs/"):]
    number = bare.split("-", 1)[0]
    for value in (bare.lower(), number.lower()):
        for bucket in ("by_number", "by_slug", "by_title"):
            hit = index[bucket].get(value)
            if hit:
                return hit
    return None


def ensure_spec_line(client, identifier, task, link, apply):
    """Make `Spec: <link>/` the first description line. Returns "in place", "would write" or "written"."""
    description = task.get("description")
    if description is None and task.get("descriptionDeferred"):
        detail = client.request("GET", f"/task/{task['id']}") or {}
        description = detail.get("description")
    description = description or ""
    target = f"Spec: {link}/"
    lines = description.splitlines()
    first_index = next((i for i, line in enumerate(lines) if line.strip()), None)
    if first_index is not None and lines[first_index].strip() == target:
        return "in place"
    if not apply:
        print(f"would link {identifier} to {link}/")
        return "would write"
    if first_index is not None and SPEC_LINE.match(lines[first_index].strip()):
        lines[first_index] = target
        new_description = "\n".join(lines)
    else:
        new_description = target + ("\n\n" + description if description.strip() else "")
    client.request("PUT", f"/task/description/{task['id']}", {"description": new_description})
    print(f"linked {identifier} to {link}/")
    return "written"


class Kaneo:
    """A small JSON client for the Kaneo API with pacing and the retry rules from the contract."""

    def __init__(self, base_url, api_key, min_interval):
        self.base = base_url.rstrip("/") + "/api"
        self.api_key = api_key
        self.min_interval = min_interval
        self.last_request = 0.0
        self.write_times = []
        self.requests_made = 0

    def _pace(self, method):
        now = time.monotonic()
        wait = self.min_interval - (now - self.last_request)
        if method != "GET":
            self.write_times = [t for t in self.write_times if now - t < 60]
            if len(self.write_times) >= MAX_WRITES_PER_MINUTE:
                wait = max(wait, 60 - (now - self.write_times[0]))
        if wait > 0:
            time.sleep(wait)
        now = time.monotonic()
        self.last_request = now
        if method != "GET":
            self.write_times.append(now)

    def request(self, method, path, body=None):
        url = self.base + path
        data = json.dumps(body).encode("utf-8") if body is not None else None
        for attempt in range(4):
            self._pace(method)
            self.requests_made += 1
            req = urllib.request.Request(url, data=data, method=method)
            req.add_header("Authorization", f"Bearer {self.api_key}")
            if data is not None:
                req.add_header("Content-Type", "application/json")
            try:
                with urllib.request.urlopen(req, timeout=30) as resp:
                    raw = resp.read()
                    return json.loads(raw) if raw else None
            except urllib.error.HTTPError as exc:
                if exc.code in (401, 403):
                    raise KeyRefused(f"{method} {path} was refused with {exc.code}; stopping") from None
                if exc.code == 429 and attempt < 3:
                    retry_after = exc.headers.get("Retry-After", "60")
                    seconds = int(retry_after) if str(retry_after).isdigit() else 60
                    print(f"  rate limited on {method} {path}; waiting {seconds}s", file=sys.stderr)
                    time.sleep(seconds)
                    continue
                raise KaneoError(f"{method} {path} failed with HTTP {exc.code}") from None
            except urllib.error.URLError as exc:
                raise KaneoError(f"{method} {path} could not connect: {exc.reason}") from None
        raise KaneoError(f"{method} {path} still rate limited after retries")


def resolve_project(client, project_key, workspace_id):
    if not workspace_id:
        workspaces = client.request("GET", "/workspace") or []
        if isinstance(workspaces, dict):
            workspaces = workspaces.get("workspaces", [workspaces])
        if len(workspaces) != 1:
            raise KaneoError("set KANEO_WORKSPACE_ID: the key sees more than one workspace")
        workspace_id = workspaces[0]["id"]
    query = urllib.parse.urlencode({"workspaceId": workspace_id})
    projects = client.request("GET", f"/project?{query}") or []
    matches = [p for p in projects if p.get("slug") == project_key]
    if len(matches) != 1:
        raise KaneoError(f"project with slug {project_key} not found in workspace {workspace_id}")
    return matches[0]["id"]


def column_ids(client, project_id):
    columns = client.request("GET", f"/column/{project_id}") or []
    return {column["slug"]: column["id"] for column in columns if column.get("slug")}


def ensure_columns(client, project_id, needed, apply):
    """Create the missing columns this run needs. Returns the slugs that exist after the run (dry run: planned)."""
    existing = column_ids(client, project_id)
    missing = [slug for slug in COLUMNS_TO_CREATE if slug in needed and slug not in existing]
    for slug in missing:
        if apply:
            client.request("POST", f"/column/{project_id}", {"name": COLUMNS_TO_CREATE[slug]})
            print(f"created column {slug}")
        else:
            print(f"would create column {slug}")
    if apply and missing:
        existing = column_ids(client, project_id)
        still_missing = [slug for slug in missing if slug not in existing]
        if still_missing:
            raise KaneoError(f"columns still missing after create: {', '.join(still_missing)}")
    return existing, missing


def run(args):
    api_key = os.environ.get("KANEO_API_KEY", "")
    if not api_key:
        print("error: KANEO_API_KEY is not set", file=sys.stderr)
        return 1
    base_url = os.environ.get("KANEO_BASE_URL", "https://kaneo.jordylab.be")
    project_key = os.environ.get("KANEO_PROJECT_KEY", "JOR")
    workspace_id = os.environ.get("KANEO_WORKSPACE_ID", "")
    min_interval = float(os.environ.get("KANEO_FIXUP_MIN_INTERVAL", DEFAULT_MIN_INTERVAL))

    records = load_export(args.export)
    if not records:
        print("error: the export has no issues with a JOR identifier", file=sys.stderr)
        return 1

    plan, unknown_states, label_problems = [], [], []
    for record in records:
        status = target_status(record["state"])
        if status is None:
            unknown_states.append(f"{record['identifier']} ({record['state'] or 'no state'})")
            continue
        problem = label_problem(record["labels"])
        if problem:
            label_problems.append(f"{record['identifier']}: {problem}")
        plan.append({**record, "target": status})

    client = Kaneo(base_url, api_key, min_interval)
    project_id = resolve_project(client, project_key, workspace_id)
    needed = {item["target"] for item in plan}
    existing_columns, _ = ensure_columns(client, project_id, needed, args.apply)

    spec_links, unresolved = {}, []
    references = {item["identifier"]: spec_reference(item) for item in plan}
    if any(references.values()):
        inventory = load_inventory(args)
        for identifier, reference in references.items():
            if reference is None:
                continue
            link = resolve_spec(reference, inventory)
            if link:
                spec_links[identifier] = link
            else:
                unresolved.append(f"{identifier} ({reference})")

    counts = {"already in place": 0, "moved": 0, "would move": 0, "not in Kaneo": 0}
    spec_counts = {"in place": 0, "would write": 0, "written": 0}
    per_target = {}
    print("\nIDENTIFIER | Linear state | Kaneo now | target")
    for item in plan:
        try:
            task = client.request("GET", f"/task/by-ticket-id/{item['identifier']}")
        except KaneoError as exc:
            if "HTTP 404" in str(exc):
                counts["not in Kaneo"] += 1
                print(f"{item['identifier']} | {item['state']} | not in Kaneo | {item['target']}")
                continue
            raise
        current = task.get("status")
        print(f"{item['identifier']} | {item['state']} | {current} | {item['target']}")
        per_target[item["target"]] = per_target.get(item["target"], 0) + 1
        if current == item["target"]:
            counts["already in place"] += 1
        elif not args.apply:
            counts["would move"] += 1
        else:
            if "id" not in task:
                raise KaneoError(f"{item['identifier']}: the by-ticket-id reply has no task id")
            client.request("PUT", f"/task/status/{task['id']}", {"status": item["target"]})
            counts["moved"] += 1
        link = spec_links.get(item["identifier"])
        if link:
            spec_counts[ensure_spec_line(client, item["identifier"], task, link, args.apply)] += 1

    print("\n--- summary (" + ("applied" if args.apply else "dry run, nothing written") + ")")
    for label, value in counts.items():
        print(f"{label}: {value}")
    print("tickets per target status: " + (", ".join(f"{k}={v}" for k, v in sorted(per_target.items())) or "none"))
    if spec_links or unresolved:
        print(f"spec links already in place: {spec_counts['in place']}")
        if args.apply:
            print(f"spec links written: {spec_counts['written']}")
        else:
            print(f"spec links would be written: {spec_counts['would write']}")
    if unresolved:
        print(f"spec links not resolved ({len(unresolved)}): " + ", ".join(unresolved))
    if unknown_states:
        print(f"Linear states this script does not map ({len(unknown_states)}): " + ", ".join(unknown_states))
    if label_problems:
        print(f"tickets without exactly one App and one Type label ({len(label_problems)}):")
        for line in label_problems:
            print("  " + line)
    print(f"requests made: {client.requests_made}")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Fix the Kaneo board after the Linear import (spec 022).")
    parser.add_argument("--export", required=True, help="path to Linear's JSON export (kept off the server)")
    parser.add_argument("--apply", action="store_true", help="write changes; without this the run is a dry run")
    parser.add_argument(
        "--inventory",
        help="saved JSON from repo_inventory.sh; without it the script in this repository is run",
    )
    args = parser.parse_args()
    try:
        return run(args)
    except KeyRefused as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    except (ExportError, KaneoError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
