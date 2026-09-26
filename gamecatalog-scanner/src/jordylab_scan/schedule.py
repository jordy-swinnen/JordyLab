"""Automatic-startup installers for Linux (systemd user units) and macOS.

No administrator/root rights are used: Linux writes user units and probes
``loginctl enable-linger`` (falling back to a login-triggered unit when polkit
denies self-linger); macOS writes a LaunchAgent that runs at login.
"""

import getpass
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import List

SERVICE_NAME = "jordylab-scan"
LAUNCH_LABEL = "dev.jordylab.scan"


def install_dir():
    return Path.home() / ".local" / "share" / "jordylab-scan"


def _systemd_user_dir():
    return Path.home() / ".config" / "systemd" / "user"


def _launchagents_dir():
    return Path.home() / "Library" / "LaunchAgents"


def systemd_service_text(exec_start):
    return (
        "[Unit]\n"
        "Description=JordyLab game catalog scan\n"
        "After=network.target\n"
        "\n"
        "[Service]\n"
        "Type=oneshot\n"
        f"ExecStart={exec_start}\n"
    )


def systemd_timer_text():
    return (
        "[Unit]\n"
        "Description=Run the JordyLab game catalog scan periodically\n"
        "\n"
        "[Timer]\n"
        "OnBootSec=5min\n"
        "OnUnitActiveSec=6h\n"
        "RandomizedDelaySec=10min\n"
        "Persistent=true\n"
        "\n"
        "[Install]\n"
        "WantedBy=timers.target\n"
    )


def systemd_login_service_text(exec_start):
    return (
        "[Unit]\n"
        "Description=JordyLab game catalog scan (login-triggered)\n"
        "\n"
        "[Service]\n"
        "Type=oneshot\n"
        f"ExecStart={exec_start}\n"
        "\n"
        "[Install]\n"
        "WantedBy=default.target\n"
    )


def launchagent_plist_text(label, program_args, stdout_path, stderr_path):
    arguments = "".join(
        f"    <string>{_xml_escape(argument)}</string>\n" for argument in program_args
    )

    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" '
        '"http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n'
        '<plist version="1.0">\n'
        "<dict>\n"
        "  <key>Label</key>\n"
        f"  <string>{_xml_escape(label)}</string>\n"
        "  <key>ProgramArguments</key>\n"
        "  <array>\n"
        f"{arguments}  </array>\n"
        "  <key>RunAtLoad</key>\n"
        "  <true/>\n"
        "  <key>StandardOutPath</key>\n"
        f"  <string>{_xml_escape(str(stdout_path))}</string>\n"
        "  <key>StandardErrorPath</key>\n"
        f"  <string>{_xml_escape(str(stderr_path))}</string>\n"
        "</dict>\n"
        "</plist>\n"
    )


def _xml_escape(value):
    return str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _run(command):
    try:
        return subprocess.run(command, capture_output=True, text=True, check=False).returncode
    except (OSError, FileNotFoundError):  # pragma: no cover - tool not installed
        return 127


def probe_linger(user=None):
    """Try to enable lingering non-interactively; return whether it succeeded."""
    user = user or os.environ.get("USER") or getpass.getuser()

    return _run(["loginctl", "enable-linger", user]) == 0


def read_old_shell_schedules():
    """Find pre-existing shell-script schedules referencing jordylab-scan-*.sh."""
    found: List[str] = []

    for directory in (_systemd_user_dir(), _launchagents_dir()):
        if not directory.is_dir():
            continue
        for path in sorted(directory.glob("*jordylab*")):
            try:
                text = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            if "jordylab-scan" in text and ".sh" in text:
                found.append(str(path))

    crontab = _crontab_lines()
    for line in crontab:
        if "jordylab-scan" in line and ".sh" in line:
            found.append("crontab: " + line.strip())

    return found


def _crontab_lines():
    try:
        result = subprocess.run(["crontab", "-l"], capture_output=True, text=True, check=False)
    except (OSError, FileNotFoundError):  # pragma: no cover
        return []
    if result.returncode != 0:
        return []

    return result.stdout.splitlines()


def remove_old_shell_schedules():
    """Remove old shell-script schedules; returns what was removed."""
    removed: List[str] = []

    for directory in (_systemd_user_dir(), _launchagents_dir()):
        if not directory.is_dir():
            continue
        for path in sorted(directory.glob("*jordylab*")):
            try:
                text = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            if "jordylab-scan" in text and ".sh" in text:
                try:
                    path.unlink()
                    removed.append(str(path))
                except OSError:
                    pass

    lines = _crontab_lines()
    kept = [line for line in lines if not ("jordylab-scan" in line and ".sh" in line)]
    if len(kept) != len(lines):
        _write_crontab(kept)
        removed.append("crontab entry")

    return removed


def _write_crontab(lines):
    try:
        subprocess.run(
            ["crontab", "-"],
            input="\n".join(lines) + ("\n" if lines else ""),
            text=True,
            check=False,
        )
    except (OSError, FileNotFoundError):  # pragma: no cover
        pass


def install(script_path, library_type, python_executable=None, remove_old=True):
    """Install the client to run automatically; returns a result dict."""
    python_executable = python_executable or sys.executable
    target_dir = install_dir()
    target_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    installed_script = target_dir / f"jordylab-scan-{library_type.lower()}.py"
    source = Path(script_path) if script_path else None
    if source is not None and source.is_file():
        shutil.copyfile(str(source), str(installed_script))
        os.chmod(str(installed_script), 0o700)
    else:
        # Packaged/dev mode: write a tiny launcher that runs the installed package.
        installed_script.write_text(
            "#!/usr/bin/env python3\nfrom jordylab_scan.__main__ import main\n"
            "raise SystemExit(main())\n",
            encoding="utf-8",
        )
        os.chmod(str(installed_script), 0o700)

    exec_start = f"{python_executable} {installed_script} scan --library {library_type}"
    removed = remove_old_shell_schedules() if remove_old else []

    if sys.platform == "darwin":
        mode = _install_macos(exec_start, target_dir)
    else:
        mode = _install_linux(exec_start)

    return {
        "installedScript": str(installed_script),
        "mode": mode,
        "removedOldSchedules": removed,
    }


def _install_linux(exec_start):
    unit_dir = _systemd_user_dir()
    unit_dir.mkdir(parents=True, exist_ok=True)
    service_path = unit_dir / f"{SERVICE_NAME}.service"
    timer_path = unit_dir / f"{SERVICE_NAME}.timer"
    service_path.write_text(systemd_service_text(exec_start), encoding="utf-8")
    timer_path.write_text(systemd_timer_text(), encoding="utf-8")
    _run(["systemctl", "--user", "daemon-reload"])
    _run(["systemctl", "--user", "enable", "--now", f"{SERVICE_NAME}.timer"])

    if probe_linger():
        return "timer+linger"

    # Linger was denied: also install a login-triggered unit so the scan still
    # runs without root.
    login_service = unit_dir / f"{SERVICE_NAME}-login.service"
    login_service.write_text(systemd_login_service_text(exec_start), encoding="utf-8")
    _run(["systemctl", "--user", "daemon-reload"])
    _run(["systemctl", "--user", "enable", f"{SERVICE_NAME}-login.service"])

    return "timer+login (linger unavailable; run 'sudo loginctl enable-linger {0}')".format(
        os.environ.get("USER", "$USER")
    )


def _install_macos(exec_start, target_dir):
    agents = _launchagents_dir()
    agents.mkdir(parents=True, exist_ok=True)
    arguments = exec_start.split(" ")
    log_path = target_dir / f"{SERVICE_NAME}.log"
    plist_path = agents / f"{LAUNCH_LABEL}.plist"
    plist_path.write_text(
        launchagent_plist_text(LAUNCH_LABEL, arguments, log_path, log_path),
        encoding="utf-8",
    )
    _run(["launchctl", "unload", str(plist_path)])
    _run(["launchctl", "load", "-w", str(plist_path)])

    return "launchagent"


def uninstall():
    """Remove the schedule; returns what was removed."""
    removed: List[str] = []
    if sys.platform == "darwin":
        plist_path = _launchagents_dir() / f"{LAUNCH_LABEL}.plist"
        if plist_path.exists():
            _run(["launchctl", "unload", str(plist_path)])
            try:
                plist_path.unlink()
                removed.append(str(plist_path))
            except OSError:
                pass
    else:
        unit_dir = _systemd_user_dir()
        _run(["systemctl", "--user", "disable", "--now", f"{SERVICE_NAME}.timer"])
        _run(["systemctl", "--user", "disable", f"{SERVICE_NAME}-login.service"])
        for suffix in (".service", ".timer"):
            path = unit_dir / (SERVICE_NAME + suffix)
            if path.exists():
                try:
                    path.unlink()
                    removed.append(str(path))
                except OSError:
                    pass
        login_service = unit_dir / f"{SERVICE_NAME}-login.service"
        if login_service.exists():
            try:
                login_service.unlink()
                removed.append(str(login_service))
            except OSError:
                pass
        _run(["systemctl", "--user", "daemon-reload"])

    return removed


def status_line():
    """One-line description of the installed startup mode."""
    if sys.platform == "darwin":
        present = (_launchagents_dir() / f"{LAUNCH_LABEL}.plist").exists()

        return "launchagent installed" if present else "not installed"
    present = (_systemd_user_dir() / f"{SERVICE_NAME}.timer").exists()

    return "systemd user timer installed" if present else "not installed"
