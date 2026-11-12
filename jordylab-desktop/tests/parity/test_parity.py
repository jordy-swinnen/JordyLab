"""The parity gate (quickstart Scenario 1, task T006).

The app must collect exactly what the scanner's own client collects: same
entries, same manifest contents, same grouped games and therefore the same
fingerprint. This is what keeps catalog identity stable (no duplicate games, no
lost AI descriptions). If this fails, nothing else may ship.

The comparison imports the scanner's ``collect_library`` (the reference
implementation) and runs the app's collection over the same fixture.
"""

import json

from jordylab_scan.collect import collect_library as scanner_collect
from jordylab_scan.manifest import compute_digest

from jordylab_desktop.services.sync_service import _collect


def _emudeck_fixture(root):
    snes = root / "roms" / "snes"
    snes.mkdir(parents=True)
    (snes / "Super Mario World (USA).sfc").write_bytes(b"rom")
    psx = root / "roms" / "psx"
    psx.mkdir(parents=True)
    (psx / "Final Fantasy VII.bin").write_bytes(b"data")
    (psx / "Final Fantasy VII.cue").write_text(
        'FILE "Final Fantasy VII.bin" BINARY\n', encoding="utf-8"
    )

    return [str(root / "roms")]


def _steam_fixture(root):
    steamapps = root / "steamapps"
    steamapps.mkdir(parents=True)
    (steamapps / "appmanifest_570.acf").write_text(
        '"AppState"\n{\n'
        '\t"appid"\t\t"570"\n'
        '\t"name"\t\t"Dota 2"\n'
        '\t"StateFlags"\t\t"4"\n'
        '\t"installdir"\t\t"dota 2 beta"\n'
        '\t"SizeOnDisk"\t\t"1000"\n'
        '\t"LastUpdated"\t\t"1700000000"\n'
        "}\n",
        encoding="utf-8",
    )

    return [str(root)]


def _compare(library_type, roots):
    scanner_entries, scanner_manifests, scanner_games, _skipped = scanner_collect(
        library_type, roots, True
    )
    app_entries, app_manifests, app_games, _skipped = _collect(library_type, roots, True)

    scanner_rows = [(entry.relpath, entry.size, entry.mtime_ns) for entry in scanner_entries]
    app_rows = [(entry.relpath, entry.size, entry.mtime_ns) for entry in app_entries]
    assert scanner_rows == app_rows
    assert scanner_manifests == app_manifests
    assert scanner_games == app_games
    assert compute_digest(scanner_entries, scanner_manifests, scanner_games) == compute_digest(
        app_entries, app_manifests, app_games
    )


def test_emudeck_parity(tmp_path):
    _compare("EMUDECK", _emudeck_fixture(tmp_path))


def test_steam_parity(tmp_path):
    _compare("STEAM", _steam_fixture(tmp_path))


def test_sunshine_digest_is_stable(tmp_path):
    config_dir = tmp_path / "sunshine"
    config_dir.mkdir()
    (config_dir / "apps.json").write_text(
        json.dumps([{"name": "Desktop"}, {"name": "Steam Big Picture"}]),
        encoding="utf-8",
    )
    from jordylab_desktop.services.sync_service import _collect_sunshine

    first = _collect_sunshine(config_dir)
    second = _collect_sunshine(config_dir)

    assert compute_digest(*first[:3]) == compute_digest(*second[:3])
