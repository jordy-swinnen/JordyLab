"""Last-run state and human-readable status output."""

import json
import os
import tempfile

from .config import state_dir

EXIT_MESSAGES = {
    0: "ok",
    2: "re-authentication needed; run 'login'",
    3: "network failure; will retry on the next run",
    4: "server rejected the scan",
    5: "partial result: one or more configured roots were skipped",
    6: "configuration or fatal error",
}


def last_run_path():
    return state_dir() / "last-run.json"


def write_last_run(state):
    path = last_run_path()
    directory = path.parent
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    handle, temp_path = tempfile.mkstemp(dir=str(directory), prefix=".last-run-")
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as stream:
            json.dump(state, stream, sort_keys=True)
        os.replace(temp_path, str(path))
    except OSError:
        try:
            os.unlink(temp_path)
        except OSError:
            pass
        raise

    return path


def read_last_run():
    path = last_run_path()
    if not path.is_file():
        return None
    try:
        with open(str(path), encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return None

    return data if isinstance(data, dict) else None


def exit_message(code):
    return EXIT_MESSAGES.get(code, f"unknown result ({code})")


def format_status(last_run, schedule_info=None, token_present=False, machine_id=None):
    lines = []
    if schedule_info is not None:
        lines.append(f"Automatic startup: {schedule_info}")
    lines.append("Session: {0}".format("cached" if token_present else "not logged in"))
    if machine_id:
        lines.append(f"Machine id: {machine_id}")
    if not last_run:
        lines.append("Last run: never")
    else:
        when = last_run.get("finishedAt") or last_run.get("startedAt") or "unknown"
        lines.append(f"Last run: {when}")
        lines.append("  library: {0}".format(last_run.get("libraryType", "unknown")))
        lines.append("  outcome: {0}".format(last_run.get("outcome", "unknown")))
        code = last_run.get("exitCode")
        if code is not None:
            lines.append(f"  result: {exit_message(int(code))}")
        skipped = last_run.get("skippedRoots")
        if skipped:
            lines.append("  skipped roots: {0}".format(", ".join(str(root) for root in skipped)))

    return "\n".join(lines)
