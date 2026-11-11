"""Update service tests: versions, manifest, verification, swap."""

import hashlib
import io
import json

import pytest

from jordylab_desktop.services import update_service


def test_is_newer():
    assert update_service.is_newer("0.1.0", "0.2.0")
    assert update_service.is_newer("0.1.0", "0.1.1")
    assert update_service.is_newer("1.9.9", "1.10.0")
    assert not update_service.is_newer("0.2.0", "0.2.0")
    assert not update_service.is_newer("0.2.0", "0.1.9")


def test_update_for_platform():
    manifest = {
        "version": "0.2.0",
        "platforms": {
            "linux-x86_64": {"url": "https://x/app.bin", "sha256": "AB", "signature": "sig"},
        },
    }
    info = update_service.update_for_platform(manifest, "linux-x86_64")
    assert info.version == "0.2.0"
    assert info.sha256 == "ab"
    assert update_service.update_for_platform(manifest, "macos-aarch64") is None


def _manifest_opener(payload):
    def opener(_url, timeout=None):
        return io.BytesIO(json.dumps(payload).encode("utf-8"))

    return opener


def test_check_finds_newer_release():
    manifest = {"version": "9.9.9", "platforms": {
        "linux-x86_64": {"url": "https://x/asset", "sha256": "00"}}}
    service = update_service.UpdateService(
        "0.1.0", "linux-x86_64", manifest_url="https://x/manifest.json",
        opener=_manifest_opener(manifest))

    info = service.check()
    assert info.version == "9.9.9"


def test_check_returns_none_when_current():
    manifest = {"version": "0.1.0", "platforms": {
        "linux-x86_64": {"url": "https://x/asset", "sha256": "00"}}}
    service = update_service.UpdateService(
        "0.1.0", "linux-x86_64", opener=_manifest_opener(manifest))

    assert service.check() is None


def test_manifest_failure_raises():
    def opener(_url, timeout=None):
        raise OSError("offline")

    service = update_service.UpdateService("0.1.0", "linux-x86_64", opener=opener)
    with pytest.raises(update_service.UpdateError):
        service.check()


def test_download_verifies_checksum(tmp_path):
    content = b"new-binary"
    digest = hashlib.sha256(content).hexdigest()
    info = update_service.UpdateInfo("0.2.0", "https://x/app.bin", digest)

    def opener(_url, timeout=None):
        return io.BytesIO(content)

    staged = update_service.download_verified(info, tmp_path, opener=opener)
    assert staged.read_bytes() == content


def test_download_rejects_bad_checksum(tmp_path):
    info = update_service.UpdateInfo("0.2.0", "https://x/app.bin", "00")

    def opener(_url, timeout=None):
        return io.BytesIO(b"tampered")

    with pytest.raises(update_service.UpdateError):
        update_service.download_verified(info, tmp_path, opener=opener)


def test_apply_staged_swaps_and_keeps_backup(tmp_path):
    executable = tmp_path / "app"
    executable.write_bytes(b"old")
    staged = tmp_path / "new"
    staged.write_bytes(b"new")

    backup = update_service.apply_staged(staged, executable)

    assert executable.read_bytes() == b"new"
    assert backup.read_bytes() == b"old"
