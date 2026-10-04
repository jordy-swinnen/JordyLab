from jordylab_scan.grouping import (
    STEAM_MANIFEST_REGEX,
    build_emudeck,
    build_steam,
    parse_vdf,
)


def _write(path, content=""):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")

    return path


def test_steam_multi_library_discovery(tmp_path):
    steam = tmp_path / "steam"
    library_two = tmp_path / "library-two"
    _write(steam / "steamapps" / "appmanifest_220.acf", '"AppState" { "appid" "220" }')
    _write(library_two / "steamapps" / "appmanifest_440.acf", '"AppState" { "appid" "440" }')
    vdf = '"libraryfolders"\n{\n"0"\n{\n"path" "%s"\n}\n"1"\n{\n"path" "%s"\n}\n}\n' % (
        steam,
        library_two,
    )
    _write(steam / "steamapps" / "libraryfolders.vdf", vdf)

    entries, contents = build_steam([steam])
    relpaths = {entry.relpath for entry in entries}

    assert relpaths == {
        "libraryfolders/0/steamapps/appmanifest_220.acf",
        "libraryfolders/1/steamapps/appmanifest_440.acf",
    }
    assert set(contents) == relpaths
    assert all(STEAM_MANIFEST_REGEX.match(relpath) for relpath in relpaths)


def test_steam_without_vdf_scans_own_steamapps(tmp_path):
    steam = tmp_path / "steam"
    _write(steam / "steamapps" / "appmanifest_10.acf", '"AppState" { "appid" "10" }')

    entries, _ = build_steam([steam])

    assert [entry.relpath for entry in entries] == ["libraryfolders/0/steamapps/appmanifest_10.acf"]


def test_steam_deduplicates_repeated_appid(tmp_path):
    steam = tmp_path / "steam"
    _write(steam / "steamapps" / "appmanifest_10.acf", '"AppState" {}')
    vdf = '"libraryfolders"\n{\n"0"\n{\n"path" "%s"\n}\n"1"\n{\n"path" "%s"\n}\n}\n' % (
        steam,
        steam,
    )
    _write(steam / "steamapps" / "libraryfolders.vdf", vdf)

    entries, _ = build_steam([steam])

    assert len(entries) == 1


def test_parse_vdf_handles_comments_and_nesting():
    document = '// comment\n"root"\n{\n"child"\n{\n"key" "value"\n}\n}\n'
    parsed = parse_vdf(document)

    assert parsed["root"]["child"]["key"] == "value"


def test_m3u_playlist_is_one_game_and_components_are_not(tmp_path):
    root = tmp_path
    _write(
        root / "psx" / "Final Fantasy VII.m3u",
        "Final Fantasy VII (Disc 1).bin\nFinal Fantasy VII (Disc 2).bin\n",
    )
    _write(root / "psx" / "Final Fantasy VII (Disc 1).bin")
    _write(root / "psx" / "Final Fantasy VII (Disc 2).bin")

    _, games = build_emudeck([root])
    refs = [game[0] for game in games]

    assert refs == ["psx/Final Fantasy VII.m3u"]
    assert games[0][1] == "Final Fantasy VII"
    assert games[0][2] == "PlayStation"


def test_cue_and_referenced_bin_are_one_game(tmp_path):
    root = tmp_path
    _write(root / "psx" / "Metal Gear Solid.cue", 'FILE "Metal Gear Solid.bin" BINARY\n')
    _write(root / "psx" / "Metal Gear Solid.bin")

    _, games = build_emudeck([root])

    assert [game[0] for game in games] == ["psx/Metal Gear Solid.cue"]


def test_gdi_and_referenced_track_are_one_game(tmp_path):
    root = tmp_path
    _write(root / "dreamcast" / "Sonic Adventure.gdi", '1\n0 0 4 2048 "Sonic Adventure.bin" 0\n')
    _write(root / "dreamcast" / "Sonic Adventure.bin")

    _, games = build_emudeck([root])

    assert [game[0] for game in games] == ["dreamcast/Sonic Adventure.gdi"]
    assert games[0][2] == "Dreamcast"


def test_standalone_chd_is_one_game(tmp_path):
    root = tmp_path
    _write(root / "ps2" / "Shadow of the Colossus.chd")

    _, games = build_emudeck([root])

    assert [game[0] for game in games] == ["ps2/Shadow of the Colossus.chd"]
    assert games[0][2] == "PlayStation 2"


def test_disc_numbered_set_collapses_to_lowest_disc(tmp_path):
    root = tmp_path
    _write(root / "psx" / "Final Fantasy VII (Disc 2).chd")
    _write(root / "psx" / "Final Fantasy VII (Disc 1).chd")
    _write(root / "psx" / "Final Fantasy VII (Disc 3).chd")

    _, games = build_emudeck([root])

    assert [game[0] for game in games] == ["psx/Final Fantasy VII (Disc 1).chd"]
    assert games[0][1] == "Final Fantasy VII"


def test_referenced_chd_is_not_standalone(tmp_path):
    root = tmp_path
    _write(root / "psx" / "Game.cue", 'FILE "Game.chd" BINARY\n')
    _write(root / "psx" / "Game.chd")

    _, games = build_emudeck([root])

    assert [game[0] for game in games] == ["psx/Game.cue"]


def test_unreferenced_bin_remains_a_game(tmp_path):
    root = tmp_path
    _write(root / "psx" / "Loose.bin")

    _, games = build_emudeck([root])

    assert [game[0] for game in games] == ["psx/Loose.bin"]


def test_playlist_ignores_comments_and_blank_lines(tmp_path):
    root = tmp_path
    _write(root / "psx" / "Set.m3u", "#EXTM3U\n\nDisc 1.bin\n")
    _write(root / "psx" / "Disc 1.bin")

    _, games = build_emudeck([root])

    assert [game[0] for game in games] == ["psx/Set.m3u"]


def test_playlist_absolute_reference_is_ignored(tmp_path):
    root = tmp_path
    _write(root / "psx" / "Set.m3u", "/absolute/Disc.bin\n")
    _write(root / "psx" / "Disc.bin")

    _, games = build_emudeck([root])

    assert {game[0] for game in games} == {"psx/Set.m3u", "psx/Disc.bin"}


def test_file_without_parent_directory_is_skipped(tmp_path):
    root = tmp_path
    _write(root / "Loose.sfc")

    _, games = build_emudeck([root])

    assert games == []


def test_library_folders_string_form(tmp_path):
    steam = tmp_path / "steam"
    library_two = tmp_path / "library-two"
    _write(library_two / "steamapps" / "appmanifest_7.acf", '"AppState" {}')
    vdf = '"libraryfolders"\n{\n"0" "%s"\n"1" "%s"\n}\n' % (steam, library_two)
    _write(steam / "steamapps" / "libraryfolders.vdf", vdf)

    entries, _ = build_steam([steam])

    assert [entry.relpath for entry in entries] == ["libraryfolders/1/steamapps/appmanifest_7.acf"]


def test_empty_vdf_falls_back_to_own_steamapps(tmp_path):
    steam = tmp_path / "steam"
    _write(steam / "steamapps" / "appmanifest_5.acf", '"AppState" {}')
    _write(steam / "steamapps" / "libraryfolders.vdf", "")

    entries, _ = build_steam([steam])

    assert len(entries) == 1


def test_disc_info_returns_none_without_tag():
    from jordylab_scan.grouping import _disc_info

    assert _disc_info("Plain Game") is None


def test_extracted_ps3_disc_is_one_game_named_after_its_folder(tmp_path):
    root = tmp_path
    game = root / "ps3" / "Demon's Souls (USA)" / "PS3_GAME"
    _write(game / "USRDIR" / "EBOOT.BIN")
    _write(game / "USRDIR" / "data" / "archive.bin")
    _write(game / "PS3_DISC.SFB")

    _, games = build_emudeck([root])

    assert games == [("ps3/Demon's Souls (USA)", "Demon's Souls", "PlayStation 3")]


def test_ps3_iso_and_extracted_folder_are_both_listed_without_junk_platforms(tmp_path):
    root = tmp_path
    _write(root / "ps3" / "Rayman Legends (USA)" / "PS3_GAME" / "USRDIR" / "EBOOT.BIN")
    _write(root / "ps3" / "London 2012.iso")

    _, games = build_emudeck([root])

    assert sorted((game[0], game[2]) for game in games) == [
        ("ps3/London 2012.iso", "PlayStation 3"),
        ("ps3/Rayman Legends (USA)", "PlayStation 3"),
    ]


def test_ps3_folder_title_keeps_dots_in_the_name(tmp_path):
    root = tmp_path
    _write(root / "ps3" / "Mr. Driller" / "PS3_GAME" / "USRDIR" / "EBOOT.BIN")

    _, games = build_emudeck([root])

    assert [game[1] for game in games] == ["Mr. Driller"]


def test_ps3_game_folder_directly_under_the_root_has_no_platform_and_is_skipped(tmp_path):
    root = tmp_path
    _write(root / "Loose Game" / "PS3_GAME" / "USRDIR" / "EBOOT.BIN")

    _, games = build_emudeck([root])

    assert games == []
