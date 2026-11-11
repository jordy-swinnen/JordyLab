"""Offscreen smoke test: the window builds, shows Connect first run, and applies a theme."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from jordylab_desktop.services import config_service
from jordylab_desktop.window import MainWindow


def test_window_builds_offscreen(qtbot, tmp_path, monkeypatch):
    monkeypatch.setenv("JORDYLAB_DESKTOP_CONFIG_DIR", str(tmp_path))
    monkeypatch.setenv("JORDYLAB_SCAN_CONFIG_DIR", str(tmp_path))
    monkeypatch.setattr("jordylab_desktop.window.load_token", lambda: None)

    window = MainWindow(config_service.load())
    qtbot.addWidget(window)

    assert window.windowTitle() == "JordyLab Sync"
    assert window.stack.currentWidget() is window.connect_screen
    assert window.status_screen.version_label.text().startswith("Version ")


def test_window_applies_each_theme(qtbot, tmp_path, monkeypatch):
    from PySide6.QtWidgets import QApplication

    monkeypatch.setenv("JORDYLAB_DESKTOP_CONFIG_DIR", str(tmp_path))
    monkeypatch.setenv("JORDYLAB_SCAN_CONFIG_DIR", str(tmp_path))
    monkeypatch.setattr("jordylab_desktop.window.load_token", lambda: None)
    window = MainWindow(config_service.load())
    qtbot.addWidget(window)

    for theme in ("content", "oxblood", "flare", "paper", "glasshouse", "heather"):
        window._apply_theme(theme)
        assert QApplication.instance().styleSheet() != ""
        assert "#" in QApplication.instance().styleSheet()
