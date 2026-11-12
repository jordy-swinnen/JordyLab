from jordylab_scan import paths


def test_steam_candidates_include_flatpak_and_os_defaults(monkeypatch, tmp_path):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(paths.sys, "platform", "linux")

    linux_candidates = [str(candidate) for candidate in paths.steam_candidates()]
    assert linux_candidates[0].endswith(".local/share/Steam")
    assert any("com.valvesoftware.Steam" in candidate for candidate in linux_candidates)
    assert not any("Application Support" in candidate for candidate in linux_candidates)

    monkeypatch.setattr(paths.sys, "platform", "darwin")
    mac_candidates = [str(candidate) for candidate in paths.steam_candidates()]
    assert any("Application Support/Steam" in candidate for candidate in mac_candidates)


def test_default_roots_returns_only_existing(monkeypatch, tmp_path):
    monkeypatch.setenv("HOME", str(tmp_path))
    emudeck = tmp_path / "Emulation" / "roms"
    emudeck.mkdir(parents=True)

    assert paths.default_roots("EMUDECK") == [emudeck]
    assert paths.default_roots("STEAM") == []
    assert paths.default_roots("UNKNOWN") == []
