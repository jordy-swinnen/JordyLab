"""Start on login: launchd (macOS), XDG autostart (Linux), Startup folder (Windows).

The app runs from login with its window hidden; closing the window only hides
it (the tray keeps it alive) so the schedule keeps running.
"""

import os
import plistlib
import sys
from pathlib import Path

APP_NAME = "JordyLab Sync"
BUNDLE_ID = "be.jordylab.desktop"


def _executable():
    return sys.executable


def _launch_command():
    if getattr(sys, "frozen", False):
        return [_executable()]

    return [_executable(), "-m", "jordylab_desktop"]


def launchd_plist_path(home=None):
    base = Path(home) if home else Path.home()

    return base / "Library" / "LaunchAgents" / f"{BUNDLE_ID}.plist"


def xdg_autostart_path(home=None):
    base = Path(home) if home else Path.home()

    return base / ".config" / "autostart" / "jordylab-desktop.desktop"


def windows_startup_path(home=None):
    base = Path(home) if home else Path.home()

    return (
        base
        / "AppData"
        / "Roaming"
        / "Microsoft"
        / "Windows"
        / "Start Menu"
        / "Programs"
        / "Startup"
        / "jordylab-desktop.bat"
    )


def install_autostart(home=None, platform_name=None):
    """Write the per-OS autostart entry; returns the path written."""
    platform_name = platform_name or sys.platform
    command = _launch_command()
    if platform_name == "darwin":
        path = launchd_plist_path(home)
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "Label": BUNDLE_ID,
            "ProgramArguments": command,
            "RunAtLoad": True,
            "KeepAlive": False,
        }
        with open(path, "wb") as handle:
            plistlib.dump(payload, handle)

        return path
    if platform_name.startswith("win"):
        path = windows_startup_path(home)
        path.parent.mkdir(parents=True, exist_ok=True)
        quoted = " ".join(f'"{part}"' for part in command)
        path.write_text(f'@echo off\r\nstart "" {quoted}\r\n', encoding="utf-8")

        return path
    path = xdg_autostart_path(home)
    path.parent.mkdir(parents=True, exist_ok=True)
    command_line = " ".join(command)
    path.write_text(
        "[Desktop Entry]\n"
        "Type=Application\n"
        f"Name={APP_NAME}\n"
        f"Exec={command_line}\n"
        "X-GNOME-Autostart-enabled=true\n",
        encoding="utf-8",
    )
    os.chmod(path, 0o644)

    return path


def remove_autostart(home=None, platform_name=None):
    platform_name = platform_name or sys.platform
    if platform_name == "darwin":
        paths = [launchd_plist_path(home)]
    elif platform_name.startswith("win"):
        paths = [windows_startup_path(home)]
    else:
        paths = [xdg_autostart_path(home)]
    removed = []
    for path in paths:
        if path.is_file():
            path.unlink()
            removed.append(str(path))

    return removed


def autostart_installed(home=None, platform_name=None):
    platform_name = platform_name or sys.platform
    if platform_name == "darwin":
        return launchd_plist_path(home).is_file()
    if platform_name.startswith("win"):
        return windows_startup_path(home).is_file()

    return xdg_autostart_path(home).is_file()
