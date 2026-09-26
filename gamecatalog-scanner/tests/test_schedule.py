from jordylab_scan import schedule


def test_systemd_service_text_has_oneshot_exec():
    text = schedule.systemd_service_text("/usr/bin/python3 /tmp/scan.py scan --library STEAM")

    assert "Type=oneshot" in text
    assert "ExecStart=/usr/bin/python3 /tmp/scan.py scan --library STEAM" in text
    assert "${" not in text


def test_systemd_timer_text_has_expected_keys():
    text = schedule.systemd_timer_text()

    assert "OnBootSec=5min" in text
    assert "OnUnitActiveSec=6h" in text
    assert "RandomizedDelaySec=10min" in text
    assert "WantedBy=timers.target" in text
    assert "${" not in text


def test_launchagent_plist_text_has_run_at_load():
    text = schedule.launchagent_plist_text(
        "dev.jordylab.scan", ["/usr/bin/python3", "/tmp/scan.py"], "/tmp/out.log", "/tmp/err.log"
    )

    assert "<key>RunAtLoad</key>" in text
    assert "<true/>" in text
    assert "dev.jordylab.scan" in text
    assert "${" not in text


def test_install_linux_writes_units(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(schedule.sys, "platform", "linux")
    monkeypatch.setattr(schedule, "_run", lambda command: 0)
    monkeypatch.setattr(schedule, "probe_linger", lambda user=None: True)

    result = schedule.install(None, "EMUDECK")

    unit_dir = tmp_path / ".config" / "systemd" / "user"
    assert (unit_dir / "jordylab-scan.service").is_file()
    assert (unit_dir / "jordylab-scan.timer").is_file()
    assert result["mode"] == "timer+linger"
    assert result["installedScript"].endswith("jordylab-scan-emudeck.py")


def test_install_linux_falls_back_when_linger_denied(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(schedule.sys, "platform", "linux")
    monkeypatch.setattr(schedule, "_run", lambda command: 0)
    monkeypatch.setattr(schedule, "probe_linger", lambda user=None: False)

    result = schedule.install(None, "STEAM")

    unit_dir = tmp_path / ".config" / "systemd" / "user"
    assert (unit_dir / "jordylab-scan-login.service").is_file()
    assert "login" in result["mode"]


def test_install_macos_writes_launchagent(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(schedule.sys, "platform", "darwin")
    monkeypatch.setattr(schedule, "_run", lambda command: 0)

    result = schedule.install(None, "STEAM")
    plist = tmp_path / "Library" / "LaunchAgents" / "dev.jordylab.scan.plist"

    assert plist.is_file()
    assert "RunAtLoad" in plist.read_text(encoding="utf-8")
    assert result["mode"] == "launchagent"


def test_detect_and_remove_old_shell_schedules(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    unit_dir = tmp_path / ".config" / "systemd" / "user"
    unit_dir.mkdir(parents=True)
    (unit_dir / "jordylab-scan-old.service").write_text(
        "ExecStart=/bin/bash ~/jordylab-scan-steam.sh", encoding="utf-8"
    )

    found = schedule.read_old_shell_schedules()
    assert any("jordylab-scan-old.service" in entry for entry in found)

    removed = schedule.remove_old_shell_schedules()
    assert any("jordylab-scan-old.service" in entry for entry in removed)
    assert not (unit_dir / "jordylab-scan-old.service").exists()


def test_status_line_reports_not_installed(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(schedule.sys, "platform", "darwin")

    assert schedule.status_line() == "not installed"


def test_xml_escape():
    assert schedule._xml_escape("a<b>&c") == "a&lt;b&gt;&amp;c"


def test_login_service_text():
    text = schedule.systemd_login_service_text("/usr/bin/python3 /tmp/scan.py")

    assert "WantedBy=default.target" in text
    assert "${" not in text


def test_install_copies_running_script(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(schedule.sys, "platform", "linux")
    monkeypatch.setattr(schedule, "_run", lambda command: 0)
    monkeypatch.setattr(schedule, "probe_linger", lambda user=None: True)
    script = tmp_path / "jordylab-scan-emudeck.py"
    script.write_text("print('hi')", encoding="utf-8")

    result = schedule.install(str(script), "EMUDECK")

    assert (schedule.install_dir() / "jordylab-scan-emudeck.py").read_text() == "print('hi')"
    assert result["installedScript"].endswith("jordylab-scan-emudeck.py")


def test_uninstall_linux_removes_units(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(schedule.sys, "platform", "linux")
    monkeypatch.setattr(schedule, "_run", lambda command: 0)
    unit_dir = tmp_path / ".config" / "systemd" / "user"
    unit_dir.mkdir(parents=True)
    (unit_dir / "jordylab-scan.service").write_text("svc", encoding="utf-8")
    (unit_dir / "jordylab-scan.timer").write_text("tmr", encoding="utf-8")

    removed = schedule.uninstall()

    assert len(removed) == 2
    assert not (unit_dir / "jordylab-scan.service").exists()


def test_uninstall_macos_removes_plist(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setattr(schedule.sys, "platform", "darwin")
    monkeypatch.setattr(schedule, "_run", lambda command: 0)
    agents = tmp_path / "Library" / "LaunchAgents"
    agents.mkdir(parents=True)
    plist = agents / "dev.jordylab.scan.plist"
    plist.write_text("plist", encoding="utf-8")

    removed = schedule.uninstall()

    assert str(plist) in removed
    assert not plist.exists()
