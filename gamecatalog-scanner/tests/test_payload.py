import pytest

from jordylab_scan.manifest import ScanEntry
from jordylab_scan.payload import (
    MAX_PAYLOAD_BYTES,
    PayloadTooLarge,
    build_check_body,
    build_payload,
    iso8601_from_ns,
)


def test_build_payload_has_expected_shape():
    entries = [ScanEntry("a.sfc", 10, 1_700_000_000_000_000_000)]
    body, digest = build_payload(
        "EMUDECK",
        "jordybox",
        "2026-01-01T00:00:00Z",
        entries,
        games=[("a.sfc", "A", "SNES")],
        machine_id="machine-1",
    )

    assert digest.startswith("sha256:")
    assert body["machineId"] == "machine-1"
    assert body["hostname"] == "jordybox"
    assert body["libraryType"] == "EMUDECK"
    assert body["force"] is False
    assert body["paths"] == [{"relpath": "a.sfc", "size": 10, "mtime": "2023-11-14T22:13:20Z"}]
    assert body["games"] == [{"externalRef": "a.sfc", "title": "A", "platform": "SNES"}]
    assert body["clientDigest"] == digest


def test_build_payload_omits_machine_id_when_absent():
    body, _ = build_payload("EMUDECK", "host", "2026-01-01T00:00:00Z", [])

    assert "machineId" not in body


def test_iso8601_from_ns_formats_utc():
    assert iso8601_from_ns(0) == "1970-01-01T00:00:00Z"


def test_build_payload_rejects_oversize_body():
    entries = []
    games = [("a", "x" * (MAX_PAYLOAD_BYTES + 1), "SNES")]

    with pytest.raises(PayloadTooLarge):
        build_payload("EMUDECK", "host", "2026-01-01T00:00:00Z", entries, games=games)


def test_build_check_body_includes_machine_id():
    body = build_check_body("STEAM", "host", "sha256:abc", "m1")

    assert body == {
        "machineId": "m1",
        "hostname": "host",
        "libraryType": "STEAM",
        "clientDigest": "sha256:abc",
    }
