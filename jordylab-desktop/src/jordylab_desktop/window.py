"""Main window and background workers.

All scanning, checking and updating happens in worker threads; the widgets only
render results and emit user intents.
"""

import threading

from jordylab_scan.auth import clear_token, load_token
from PySide6.QtCore import QThread, QTimer, Signal
from PySide6.QtGui import QAction, QColor, QIcon, QPainter, QPixmap
from PySide6.QtWidgets import QApplication, QMainWindow, QMenu, QStackedWidget, QSystemTrayIcon

from . import __version__
from .platform import autostart
from .screens import AppearanceScreen, ConnectScreen, StatusScreen
from .services import theme_service
from .services.auth_service import ConnectError, ConnectFlow
from .services.sync_service import (
    APPLIED,
    REAUTH_REQUIRED,
    UP_TO_DATE,
    SyncService,
    detect_hostname,
)
from .services.update_service import UpdateService, apply_staged, platform_key
from .themes import stylesheet as theme_styles


def _tray_icon():
    pixmap = QPixmap(64, 64)
    pixmap.fill(QColor("#131314"))
    painter = QPainter(pixmap)
    painter.setBrush(QColor("#ece8dd"))
    painter.setPen(QColor("#ece8dd"))
    painter.drawEllipse(12, 12, 40, 40)
    painter.end()

    return QIcon(pixmap)


class SyncWorker(QThread):
    reportReady = Signal(object)

    def __init__(self, service, force=False, parent=None):
        super().__init__(parent)
        self.service = service
        self.force = force

    def run(self):
        self.reportReady.emit(self.service.run_once(force=self.force))


class ConnectWorker(QThread):
    connected = Signal(str)
    failed = Signal(str)

    def __init__(self, flow, parent=None):
        super().__init__(parent)
        self.flow = flow

    def run(self):
        try:
            token = self.flow.wait()
            self.connected.emit(token)
        except ConnectError as error:
            self.failed.emit(str(error))


class UpdateCheckWorker(QThread):
    found = Signal(object)
    failed = Signal(str)

    def __init__(self, service, parent=None):
        super().__init__(parent)
        self.service = service

    def run(self):
        try:
            info = self.service.check()
            self.found.emit(info)
        except Exception as error:  # noqa: BLE001 - surfaced in the window
            self.failed.emit(str(error))


class UpdateInstallWorker(QThread):
    progress = Signal(int)
    finishedUpdate = Signal()
    failed = Signal(str)

    def __init__(self, service, info, executable_path, parent=None):
        super().__init__(parent)
        self.service = service
        self.info = info
        self.executable_path = executable_path

    def run(self):
        try:
            self.progress.emit(10)
            staged = self.service.download(self.info)
            self.progress.emit(80)
            apply_staged(staged, self.executable_path)
            self.progress.emit(100)
            self.finishedUpdate.emit()
        except Exception as error:  # noqa: BLE001 - surfaced in the window
            self.failed.emit(str(error))


class MainWindow(QMainWindow):
    """Stacked window: Connect → Status → Appearance."""

    def __init__(self, config, config_path=None, parent=None):
        super().__init__(parent)
        self.config = config
        self.config_path = config_path
        self.setWindowTitle("JordyLab Sync")
        self.resize(config.window_width, config.window_height)
        self._tray = None
        self._sync_worker = None
        self._update_info = None
        self._theme_preference = None

        self.stack = QStackedWidget()
        self.setCentralWidget(self.stack)
        self.connect_screen = ConnectScreen()
        self.status_screen = StatusScreen()
        self.appearance_screen = AppearanceScreen()
        self.stack.addWidget(self.connect_screen)
        self.stack.addWidget(self.status_screen)
        self.stack.addWidget(self.appearance_screen)
        self.status_screen.set_version(__version__)

        self.connect_screen.signInRequested.connect(self._connect)
        self.status_screen.syncNowRequested.connect(lambda: self._start_sync(force=False))
        self.status_screen.checkUpdateRequested.connect(self._check_update)
        self.status_screen.installUpdateRequested.connect(self._install_update)
        self.status_screen.appearanceRequested.connect(self._show_appearance)
        self.status_screen.reconnectRequested.connect(self._reconnect)
        self.appearance_screen.themeSelected.connect(self._theme_chosen)
        self.appearance_screen.backRequested.connect(self._show_status)

        self.timer = QTimer(self)
        self.timer.timeout.connect(lambda: self._start_sync(force=False))
        self.timer.start(int(config.schedule_hours * 3600 * 1000))

        self._setup_tray()
        self._apply_theme(
            theme_service.resolve_theme(
                theme_service.load_cached_theme() or theme_service.FOLLOW_DEVICE,
                theme_service.device_is_dark(),
            )
        )
        self._show_start()

    # ------------------------------------------------------------------ state

    def _show_start(self):
        if load_token() is None:
            self.stack.setCurrentWidget(self.connect_screen)
            self.status_screen.set_connected(False)
            return
        self._show_status()
        self._start_sync(force=False)
        self._refresh_theme()

    def _show_status(self):
        self.stack.setCurrentWidget(self.status_screen)
        self.status_screen.set_connected(load_token() is not None)

    def _show_appearance(self):
        self.appearance_screen.set_theme(self._theme_preference or theme_service.FOLLOW_DEVICE)
        self.stack.setCurrentWidget(self.appearance_screen)

    # ------------------------------------------------------------------ sync

    def _start_sync(self, force=False):
        if load_token() is None:
            self._show_start()
            return
        if self._sync_worker is not None and self._sync_worker.isRunning():
            return
        self.status_screen.set_syncing(True)
        self.status_screen.set_status("Checking whether a scan is needed…")
        self._sync_worker = SyncWorker(SyncService(self.config), force=force, parent=self)
        self._sync_worker.reportReady.connect(self._sync_finished)
        self._sync_worker.finished.connect(lambda: self.status_screen.set_syncing(False))
        self._sync_worker.start()

    def _sync_finished(self, report):
        self.status_screen.set_syncing(False)
        rows = []
        for result in report.results:
            detail = f"{result.games} games" if result.games else ""
            if result.outcome in (APPLIED, UP_TO_DATE):
                when = report.finished_at.replace("T", " ").replace("Z", " UTC")
                detail = f"{result.games} games · {when}"
            elif result.message:
                detail = result.message
            rows.append((result.library_type, detail, result.outcome))
        self.status_screen.set_libraries(rows)
        worst = report.worst_outcome()
        if worst == UP_TO_DATE:
            self.status_screen.set_status("Up to date.")
        elif worst == APPLIED:
            self.status_screen.set_status("Synced just now.")
        elif worst == REAUTH_REQUIRED:
            self.status_screen.set_status("This machine must be reconnected.")
            self.status_screen.set_connected(False)
        else:
            self.status_screen.set_status(
                "The last sync did not complete. See the libraries below."
            )
        self._refresh_theme()

    # ------------------------------------------------------------------ connect

    def _connect(self):
        self.connect_screen.set_error("")
        self.connect_screen.set_busy(True)
        flow = ConnectFlow(
            self.config.backend_url,
            hostname=detect_hostname(),
        )
        worker = ConnectWorker(flow, parent=self)
        worker.connected.connect(self._connected)
        worker.failed.connect(self._connect_failed)
        worker.finished.connect(lambda: self.connect_screen.set_busy(False))
        self._connect_worker = worker
        flow.start()
        worker.start()

    def _connected(self, token):
        self.connect_screen.set_error("")
        self.stack.setCurrentWidget(self.status_screen)
        self.status_screen.set_connected(True)
        self.status_screen.set_status("Connected. Starting the first sync…")
        autostart.install_autostart()
        self._start_sync(force=False)

    def _connect_failed(self, message):
        self.connect_screen.set_error(message)

    def _reconnect(self):
        clear_token()
        self.stack.setCurrentWidget(self.connect_screen)
        self.status_screen.set_connected(False)

    # ------------------------------------------------------------------ update

    def _check_update(self):
        service = UpdateService(__version__, platform_key())
        worker = UpdateCheckWorker(service, parent=self)
        worker.found.connect(self._update_found)
        worker.failed.connect(lambda message: self.status_screen.show_update_error(message))
        self._update_worker = worker
        worker.start()

    def _update_found(self, info):
        if info is None:
            self.status_screen.clear_update()
            self.status_screen.set_status("You are on the latest version.")
            return
        self._update_info = info
        self.status_screen.set_update_available(info.version)

    def _install_update(self):
        if self._update_info is None:
            self._check_update()
            return
        service = UpdateService(__version__, platform_key())
        worker = UpdateInstallWorker(service, self._update_info, executable_path())
        worker.progress.connect(self.status_screen.set_update_progress)
        worker.finishedUpdate.connect(self._update_finished)
        worker.failed.connect(self.status_screen.show_update_error)
        self._update_worker = worker
        worker.start()

    def _update_finished(self):

        QApplication.exit(0)
        self._relaunch()

    def _relaunch(self):
        import subprocess
        import sys

        subprocess.Popen(
            [sys.executable] + ([] if getattr(sys, "frozen", False) else ["-m", "jordylab_desktop"])
        )

    # ------------------------------------------------------------------ theme

    def _refresh_theme(self):
        token = load_token()
        if not token or not token.get("token"):
            return

        def run():
            theme = theme_service.current_theme(
                self.config, token["token"], theme_service.device_is_dark()
            )
            QApplication.instance().stheme = theme
            QTimer.singleShot(0, lambda: self._apply_theme(theme))

        threading.Thread(target=run, daemon=True).start()

    def _theme_chosen(self, theme_id):
        self._theme_preference = theme_id
        theme_service.save_cached_theme(theme_id)
        self._apply_theme(theme_service.resolve_theme(theme_id, theme_service.device_is_dark()))
        self._show_status()

    def _apply_theme(self, theme_id):
        QApplication.instance().setStyleSheet(theme_styles.stylesheet(theme_id))

    # ------------------------------------------------------------------ tray

    def _setup_tray(self):
        if not QSystemTrayIcon.isSystemTrayAvailable():
            return
        tray = QSystemTrayIcon(_tray_icon(), self)
        tray.setToolTip("JordyLab Sync")
        menu = QMenu()
        open_action = QAction("Open", self)
        open_action.triggered.connect(self._restore)
        sync_action = QAction("Sync now", self)
        sync_action.triggered.connect(lambda: self._start_sync(force=False))
        quit_action = QAction("Quit", self)
        quit_action.triggered.connect(QApplication.quit)
        menu.addAction(open_action)
        menu.addAction(sync_action)
        menu.addSeparator()
        menu.addAction(quit_action)
        tray.setContextMenu(menu)
        tray.activated.connect(self._restore)
        tray.show()
        self._tray = tray

    def _restore(self, *_arguments):
        self.show()
        self.raise_()
        self.activateWindow()

    def closeEvent(self, event):  # noqa: N802 - Qt API
        if self._tray is not None:
            event.ignore()
            self.hide()
            return
        event.accept()


def executable_path():
    """The running executable (or the module for a dev run)."""
    import sys

    return sys.executable
