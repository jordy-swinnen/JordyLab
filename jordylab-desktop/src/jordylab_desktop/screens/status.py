"""Library sync status (designs ``D_Status``, ``D_Syncing``, ``D_Failed``, ``D_Empty``,
``D_Update``, ``D_Update_Failed``)."""

from PySide6.QtCore import Signal
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QProgressBar,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

OUTCOME_LABELS = {
    "checking": "Checking…",
    "NO_CHANGE": "Up to date",
    "APPLIED": "Synced",
    "NO_CONTENT": "Nothing found",
    "NO_SOURCE": "Not configured",
    "PARTIAL": "Some roots skipped",
    "REJECTED": "Rejected",
    "REAUTH_REQUIRED": "Reconnect needed",
    "NETWORK_ERROR": "Server unreachable",
    "FAILED": "Failed",
}


class StatusScreen(QWidget):
    """The main window: sync state, libraries, app version and updates."""

    syncNowRequested = Signal()
    appearanceRequested = Signal()
    checkUpdateRequested = Signal()
    installUpdateRequested = Signal()
    reconnectRequested = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(14)

        header = QHBoxLayout()
        brand = QLabel("JordyLab")
        brand.setObjectName("Section")
        header.addWidget(brand)
        header.addStretch(1)
        self.connection_badge = QLabel("Connected")
        self.connection_badge.setObjectName("Badge")
        header.addWidget(self.connection_badge)
        layout.addLayout(header)

        title = QLabel("Library sync")
        title.setObjectName("Title")
        layout.addWidget(title)

        self.status_label = QLabel("Checking…")
        self.status_label.setObjectName("Muted")
        self.status_label.setWordWrap(True)
        layout.addWidget(self.status_label)

        self.sync_button = QPushButton("Sync now")
        self.sync_button.setObjectName("Primary")
        self.sync_button.clicked.connect(self.syncNowRequested.emit)
        layout.addWidget(self.sync_button)

        libraries_label = QLabel("Libraries")
        libraries_label.setObjectName("Section")
        layout.addWidget(libraries_label)

        self.libraries_box = QVBoxLayout()
        self.libraries_box.setSpacing(8)
        layout.addLayout(self.libraries_box)

        self.empty_label = QLabel("No libraries found on this PC yet.")
        self.empty_label.setObjectName("Muted")
        self.empty_label.hide()
        layout.addWidget(self.empty_label)

        app_label = QLabel("App")
        app_label.setObjectName("Section")
        layout.addWidget(app_label)

        app_card = QFrame()
        app_card.setObjectName("Card")
        app_layout = QHBoxLayout(app_card)
        app_layout.setContentsMargins(18, 14, 18, 14)
        self.version_label = QLabel("Version 0.1.0")
        app_layout.addWidget(self.version_label)
        app_layout.addStretch(1)
        self.update_button = QPushButton("Check for updates")
        self.update_button.clicked.connect(self.checkUpdateRequested.emit)
        app_layout.addWidget(self.update_button)
        layout.addWidget(app_card)

        self.update_banner = QFrame()
        self.update_banner.setObjectName("Card")
        update_layout = QVBoxLayout(self.update_banner)
        update_layout.setContentsMargins(18, 14, 18, 14)
        update_layout.setSpacing(8)
        self.update_label = QLabel("")
        self.update_label.setWordWrap(True)
        update_layout.addWidget(self.update_label)
        self.update_progress = QProgressBar()
        self.update_progress.setRange(0, 100)
        self.update_progress.hide()
        update_layout.addWidget(self.update_progress)
        self.update_action = QPushButton("Update")
        self.update_action.setObjectName("Primary")
        self.update_action.clicked.connect(self.installUpdateRequested.emit)
        update_layout.addWidget(self.update_action)
        self.update_error = QLabel("")
        self.update_error.setObjectName("Error")
        self.update_error.setWordWrap(True)
        self.update_error.hide()
        update_layout.addWidget(self.update_error)
        self.update_banner.hide()
        layout.addWidget(self.update_banner)

        appearance = QPushButton("Appearance")
        appearance.clicked.connect(self.appearanceRequested.emit)
        layout.addWidget(appearance)

        layout.addStretch(1)

        footer = QLabel(
            "Syncs on schedule while this app runs. Nothing is uploaded unless the server asks for it."
        )
        footer.setObjectName("Muted")
        footer.setWordWrap(True)
        layout.addWidget(footer)

        self.reconnect_button = QPushButton("Reconnect this PC")
        self.reconnect_button.clicked.connect(self.reconnectRequested.emit)
        self.reconnect_button.hide()
        layout.addWidget(self.reconnect_button)

    def set_connected(self, connected):
        self.connection_badge.setText("Connected" if connected else "Not connected")
        self.reconnect_button.setVisible(not connected)

    def set_status(self, text):
        self.status_label.setText(text)

    def set_libraries(self, rows):
        """``rows`` is a list of (library_type, detail, state)."""
        while self.libraries_box.count():
            item = self.libraries_box.takeAt(0)
            widget = item.widget()
            if widget is not None:
                widget.deleteLater()
        self.empty_label.setVisible(not rows)
        for library_type, detail, state in rows:
            card = QFrame()
            card.setObjectName("Card")
            row = QHBoxLayout(card)
            row.setContentsMargins(16, 12, 16, 12)
            name = QLabel(library_type.title())
            row.addWidget(name)
            note = QLabel(detail)
            note.setObjectName("Muted")
            row.addWidget(note)
            row.addStretch(1)
            badge = QLabel(OUTCOME_LABELS.get(state, state))
            badge.setObjectName("Badge")
            row.addWidget(badge)
            self.libraries_box.addWidget(card)

    def set_version(self, version):
        self.version_label.setText(f"Version {version}")

    def set_update_available(self, version):
        self.update_banner.show()
        self.update_label.setText(f"New version {version} available.")
        self.update_progress.hide()
        self.update_error.hide()
        self.update_action.show()
        self.update_action.setText("Update")

    def set_update_progress(self, percent):
        self.update_progress.show()
        self.update_progress.setValue(percent)
        self.update_action.setEnabled(False)

    def show_update_error(self, message):
        self.update_progress.hide()
        self.update_error.setText(message)
        self.update_error.show()
        self.update_action.setEnabled(True)
        self.update_action.setText("Retry")

    def clear_update(self):
        self.update_banner.hide()
        self.update_action.setEnabled(True)
        self.update_action.setText("Update")
        self.update_error.hide()
        self.update_progress.hide()

    def set_syncing(self, busy):
        self.sync_button.setEnabled(not busy)
        self.sync_button.setText("Syncing…" if busy else "Sync now")
