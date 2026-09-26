from jordylab_scan import __main__ as cli
from jordylab_scan.config import state_dir, token_file


def test_version_flag(capsys):
    assert cli.main(["--version"]) == 0
    assert capsys.readouterr().out.strip()


def test_version_command(capsys):
    assert cli.main(["version"]) == 0


def test_selftest_passes():
    assert cli.main(["--selftest"]) == 0


def test_scan_with_missing_configured_root_is_partial(tmp_path, capsys):
    missing = tmp_path / "does-not-exist"

    code = cli.main(["scan", "--path", str(missing), "--library", "EMUDECK"])

    assert code == 5
    state = __import__("json").loads((state_dir() / "last-run.json").read_text())
    assert state["outcome"] == "PARTIAL"
    assert str(missing) in state["skippedRoots"]


def test_scan_without_session_exits_reauth(tmp_path, monkeypatch):
    roms = tmp_path / "roms"
    (roms / "snes").mkdir(parents=True)
    (roms / "snes" / "Game.sfc").write_text("rom", encoding="utf-8")

    code = cli.main(["scan", "--path", str(roms), "--library", "EMUDECK"])

    assert code == 2
    assert not token_file().exists()


def test_unknown_library_type_is_config_error(tmp_path):
    code = cli.main(["scan", "--library", "SWITCH"])

    assert code == 6


def test_status_command_runs(capsys):
    assert cli.main(["status"]) == 0
    assert "Last run" in capsys.readouterr().out


def test_detect_hostname_is_contract_safe(monkeypatch):
    monkeypatch.setattr(cli.platform, "node", lambda: "Jordy Box.local")

    hostname = cli.detect_hostname()

    assert hostname == "Jordy-Box"
    assert all(character.isalnum() or character in "._-" for character in hostname)
