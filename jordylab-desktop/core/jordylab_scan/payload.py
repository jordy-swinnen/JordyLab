"""Scan request body construction and the canonical client fingerprint."""

import datetime

from .manifest import compute_digest, nfc

# The estimate is ~100 bytes per path row, so 8 MiB carries ~80,000 files. The server allows 50,000 games per source;
# a source with many multi-file games (bin/cue, m3u) can exceed 8 MiB first — the clear error then says so.
MAX_PAYLOAD_BYTES = 8 * 1024 * 1024


class PayloadTooLarge(Exception):
    """Raised when the upload body would exceed the server's byte cap."""


def iso8601_from_ns(mtime_ns):
    seconds = mtime_ns / 1_000_000_000
    moment = datetime.datetime.fromtimestamp(seconds, tz=datetime.timezone.utc)

    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


def path_rows(entries):
    rows = []
    for entry in entries:
        rows.append(
            {
                "relpath": nfc(entry.relpath),
                "size": int(entry.size),
                "mtime": iso8601_from_ns(entry.mtime_ns),
            }
        )
    rows.sort(key=lambda row: row["relpath"])

    return rows


def game_rows(games):
    rows = [{"externalRef": game[0], "title": game[1], "platform": game[2]} for game in games]
    rows.sort(key=lambda row: row["externalRef"])

    return rows


def estimated_bytes(paths, manifest_contents, games):
    total = 0
    for row in paths:
        total += len(row["relpath"]) + 32
    for text in (manifest_contents or {}).values():
        total += len(text or "")
    for row in games:
        total += len(row["externalRef"]) + len(row["title"]) + len(row["platform"]) + 16

    return total


def build_payload(
    library_type,
    hostname,
    captured_at,
    entries,
    manifest_contents=None,
    games=None,
    client_digest=None,
    machine_id=None,
    force=False,
):
    """Build the request body plus its fingerprint.

    Returns ``(body, digest)``. Raises :class:`PayloadTooLarge` when the body
    would exceed the server cap.
    """
    manifest_contents = manifest_contents or {}
    games = games or []
    digest = client_digest or compute_digest(entries, manifest_contents, games)

    paths = path_rows(entries)
    rows = game_rows(games) if games else []
    if estimated_bytes(paths, manifest_contents, rows) > MAX_PAYLOAD_BYTES:
        raise PayloadTooLarge(f"scan payload exceeds {MAX_PAYLOAD_BYTES} bytes")

    body = {
        "hostname": hostname,
        "libraryType": library_type,
        "capturedAt": captured_at,
        "clientDigest": digest,
        "force": bool(force),
        "paths": paths,
        "manifestContents": manifest_contents,
        "games": rows,
    }
    if machine_id:
        body["machineId"] = machine_id

    return body, digest


def build_check_body(library_type, hostname, client_digest, machine_id=None):
    body = {
        "hostname": hostname,
        "libraryType": library_type,
        "clientDigest": client_digest,
    }
    if machine_id:
        body["machineId"] = machine_id

    return body
