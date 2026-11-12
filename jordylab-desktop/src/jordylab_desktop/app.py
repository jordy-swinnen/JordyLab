"""Application entry point."""

import sys

from PySide6.QtWidgets import QApplication

from . import __version__
from .services import config_service
from .window import MainWindow


def main(argv=None):
    app = QApplication(sys.argv if argv is None else argv)
    app.setApplicationName("JordyLab Sync")
    app.setApplicationVersion(__version__)
    config = config_service.load()
    window = MainWindow(config)
    window.show()

    return app.exec()
