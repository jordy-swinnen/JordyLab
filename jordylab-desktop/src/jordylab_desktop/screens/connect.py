"""Connect this PC (design ``D_Connect.png``)."""

import platform

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QFrame, QLabel, QPushButton, QVBoxLayout, QWidget


class ConnectScreen(QWidget):
    """First run: sign in through the browser, then the machine is connected."""

    signInRequested = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(16)

        heading = QLabel("Connect this PC")
        heading.setObjectName("Title")
        layout.addWidget(heading)

        subtitle = QLabel(
            "Sign in as admin, then connect this machine. Its Steam and EmuDeck "
            "libraries sync from then on."
        )
        subtitle.setWordWrap(True)
        subtitle.setObjectName("Muted")
        layout.addWidget(subtitle)

        card = QFrame()
        card.setObjectName("Card")
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(18, 18, 18, 18)
        card_layout.setSpacing(12)

        self.sign_in_button = QPushButton("Sign in with your browser")
        self.sign_in_button.setObjectName("Primary")
        self.sign_in_button.clicked.connect(self.signInRequested.emit)
        card_layout.addWidget(self.sign_in_button)

        self.hint = QLabel("Only the admin account can connect a machine.")
        self.hint.setObjectName("Muted")
        self.hint.setWordWrap(True)
        card_layout.addWidget(self.hint)
        layout.addWidget(card)

        machine_card = QFrame()
        machine_card.setObjectName("Card")
        machine_layout = QVBoxLayout(machine_card)
        machine_layout.setContentsMargins(18, 18, 18, 18)
        machine_layout.setSpacing(6)
        machine_label = QLabel("This PC")
        machine_label.setObjectName("Section")
        machine_layout.addWidget(machine_label)
        self.hostname_label = QLabel(platform.node() or "unknown-host")
        machine_layout.addWidget(self.hostname_label)
        machine_note = QLabel(
            "Shows up in Settings → Machines once connected. The machine token stays on this PC."
        )
        machine_note.setObjectName("Muted")
        machine_note.setWordWrap(True)
        machine_layout.addWidget(machine_note)
        layout.addWidget(machine_card)

        self.error_label = QLabel("")
        self.error_label.setObjectName("Error")
        self.error_label.setWordWrap(True)
        self.error_label.hide()
        layout.addWidget(self.error_label)
        layout.addStretch(1)

    def set_error(self, message):
        self.error_label.setText(message or "")
        self.error_label.setVisible(bool(message))

    def set_busy(self, busy):
        self.sign_in_button.setEnabled(not busy)
        self.sign_in_button.setText(
            "Waiting for sign-in…" if busy else "Sign in with your browser"
        )
