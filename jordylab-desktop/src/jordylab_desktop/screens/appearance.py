"""Appearance (design ``D_Theme.png``): the account-wide theme picker."""

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QButtonGroup, QLabel, QPushButton, QRadioButton, QVBoxLayout, QWidget

from ..services.theme_service import FOLLOW_DEVICE, THEMES

THEME_LABELS = {
    "content": "Content Led",
    "oxblood": "Oxblood",
    "flare": "Flare Orange",
    "paper": "Paper",
    "glasshouse": "Glasshouse",
    "heather": "Heather",
}


class AppearanceScreen(QWidget):
    """Pick one of the six themes, or follow the device."""

    themeSelected = Signal(str)
    backRequested = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(10)

        title = QLabel("Appearance")
        title.setObjectName("Title")
        layout.addWidget(title)

        note = QLabel("Your theme follows your account, so the web app uses it too.")
        note.setObjectName("Muted")
        note.setWordWrap(True)
        layout.addWidget(note)

        self.group = QButtonGroup(self)
        self.buttons = {}
        for theme_id in THEMES:
            button = QRadioButton(THEME_LABELS.get(theme_id, theme_id))
            self.buttons[theme_id] = button
            self.group.addButton(button)
            layout.addWidget(button)
            button.toggled.connect(lambda checked, theme=theme_id: self._selected(checked, theme))
        follow = QRadioButton("Follow this device")
        self.buttons[FOLLOW_DEVICE] = follow
        self.group.addButton(follow)
        layout.addWidget(follow)
        follow.toggled.connect(lambda checked: self._selected(checked, FOLLOW_DEVICE))

        layout.addStretch(1)
        back = QPushButton("Back")
        back.clicked.connect(self.backRequested.emit)
        layout.addWidget(back)

    def _selected(self, checked, theme_id):
        if checked:
            self.themeSelected.emit(theme_id)

    def set_theme(self, theme_id):
        button = self.buttons.get(theme_id)
        if button is not None:
            button.setChecked(True)
