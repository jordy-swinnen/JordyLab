"""Sync service tests: check-then-scan flow, outcomes, partial roots."""

from jordylab_desktop.services import sync_service


def _emudeck_root(tmp_path):
    root = tmp_path / "roms"
    snes = root / "snes"
    snes.mkdir(parents=True)
    (snes / "Super Mario World (USA).sfc").write_bytes(b"rom")

    return root


def test_scan_applies(tmp_path, monkeypatch):
    root = _emudeck_root(tmp_path)
    calls = {}

    def fake_check(cfg, body, token):
        calls["check"] = body
        return {"scanNeeded": True}

    def fake_scan(cfg, body, token):
        calls["scan"] = body
        return {"outcome": "APPLIED", "counts": {"added": 1}}

    monkeypatch.setattr(sync_service, "check", fake_check)
    monkeypatch.setattr(sync_service, "scan", fake_scan)
    cfg = sync_service.resolve_service(
        "EMUDECK", config_data={"service": {"backend_url": "http://localhost:8080"}}, env={}
    )

    result = sync_service.scan_library(
        "EMUDECK", [str(root)], True, cfg, "host", "machine-1", "jlmt_x"
    )

    assert result.outcome == sync_service.APPLIED
    assert result.games == 1
    assert calls["check"]["libraryType"] == "EMUDECK"
    assert calls["scan"]["games"][0]["title"] == "Super Mario World"
    assert calls["scan"]["machineId"] == "machine-1"


def test_scan_skipped_when_server_says_so(tmp_path, monkeypatch):
    root = _emudeck_root(tmp_path)
    monkeypatch.setattr(sync_service, "check", lambda *a, **k: {"scanNeeded": False})
    monkeypatch.setattr(
        sync_service,
        "scan",
        lambda *a, **k: (_ for _ in ()).throw(AssertionError("scan must not run")),
    )
    cfg = sync_service.resolve_service("EMUDECK", config_data={}, env={})

    result = sync_service.scan_library(
        "EMUDECK", [str(root)], True, cfg, "host", "machine-1", "jlmt_x"
    )

    assert result.outcome == sync_service.UP_TO_DATE


def test_missing_explicit_root_is_partial(tmp_path):
    cfg = sync_service.resolve_service("EMUDECK", config_data={}, env={})

    result = sync_service.scan_library(
        "EMUDECK", [str(tmp_path / "missing")], True, cfg, "host", "m", "jlmt_x"
    )

    assert result.outcome == sync_service.PARTIAL
    assert result.skipped_roots == (str(tmp_path / "missing"),)


def test_sunshine_games_are_reported(tmp_path):
    import json

    config_dir = tmp_path / "sunshine"
    config_dir.mkdir()
    (config_dir / "apps.json").write_text(
        json.dumps([{"name": "Steam Big Picture"}]), encoding="utf-8"
    )
    screenshot = {}

    def fake_check(cfg, body, token):
        return {"scanNeeded": True}

    def fake_scan(cfg, body, token):
        screenshot.update(body)
        return {"outcome": "APPLIED", "counts": {}}

    import jordylab_desktop.services.sync_service as module

    original_check, original_scan = module.check, module.scan
    module.check, module.scan = fake_check, fake_scan
    try:
        cfg = sync_service.resolve_service("SUNSHINE", config_data={}, env={})
        result = sync_service.scan_library(
            "SUNSHINE", [], False, cfg, "host", "m", "jlmt_x", sunshine_dir=config_dir
        )
    finally:
        module.check, module.scan = original_check, original_scan

    assert result.outcome == sync_service.APPLIED
    assert screenshot["games"][0]["title"] == "Steam Big Picture"
    assert screenshot["games"][0]["platform"] == "Sunshine"
