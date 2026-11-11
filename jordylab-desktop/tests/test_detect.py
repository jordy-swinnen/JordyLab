"""Detection tests: Sunshine apps.json parsing and library discovery."""

import json

from jordylab_desktop.detect import libraries, sunshine


def test_sunshine_apps_reads_names(tmp_path):
    (tmp_path / "apps.json").write_text(
        json.dumps(
            [
                {"name": "Steam Big Picture", "cmd": "steam"},
                {"name": "   "},
                {"not": "an app"},
                {"name": "Desktop"},
            ]
        ),
        encoding="utf-8",
    )

    apps = sunshine.sunshine_apps(tmp_path)
    assert [app.name for app in apps] == ["Steam Big Picture", "Desktop"]


def test_sunshine_apps_missing_file(tmp_path):
    assert sunshine.sunshine_apps(tmp_path) == []


def test_sunshine_apps_invalid_json(tmp_path):
    (tmp_path / "apps.json").write_text("{oops", encoding="utf-8")
    assert sunshine.sunshine_apps(tmp_path) == []


def test_library_roots_prefers_configured(monkeypatch):
    roots, explicit = libraries.library_roots("STEAM", {"STEAM": ["/mnt/steam"]})
    assert roots == ["/mnt/steam"]
    assert explicit is True


def test_library_roots_falls_back_to_defaults(monkeypatch, tmp_path):
    default_root = tmp_path / "steam"
    default_root.mkdir()
    monkeypatch.setattr(
        "jordylab_desktop.detect.libraries.default_roots",
        lambda library_type: [default_root],
    )

    roots, explicit = libraries.library_roots("STEAM", {})
    assert roots == [str(default_root)]
    assert explicit is False


def test_detect_libraries_includes_sunshine_when_configured(monkeypatch, tmp_path):
    config_dir = tmp_path / ".config" / "sunshine"
    config_dir.mkdir(parents=True)
    (config_dir / "apps.json").write_text(json.dumps([{"name": "Desktop"}]), encoding="utf-8")
    monkeypatch.setattr(
        "jordylab_desktop.detect.libraries.sunshine_config_dir",
        lambda: config_dir,
    )
    monkeypatch.setattr(
        "jordylab_desktop.detect.libraries.sunshine_apps",
        lambda: [object()],
    )
    monkeypatch.setattr(
        "jordylab_desktop.detect.libraries.default_roots",
        lambda library_type: [],
    )

    detected = libraries.detect_libraries({})
    types = [library.library_type for library in detected]
    assert "SUNSHINE" in types
    sunshine_library = next(item for item in detected if item.library_type == "SUNSHINE")
    assert sunshine_library.available is True
