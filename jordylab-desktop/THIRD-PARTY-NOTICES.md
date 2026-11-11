# Third-party notices: jordylab-desktop

This application is built with open-source components. This notice satisfies
their attribution requirements; the full licence texts travel with each release
in `licenses/` (fetched by the release workflow).

## Qt 6 (via PySide6)

- **Component**: Qt 6 runtime libraries, as bundled by the PySide6 wheel.
- **Modules used**: QtCore, QtGui, QtWidgets only. No GPL-only Qt modules
  (QtCharts, QtDataVisualization, QtWebEngine, …) are used.
- **Licence**: GNU Lesser General Public License v3.0 (LGPL-3.0).
- **Source**: https://code.qt.io/ · https://download.qt.io/official_releases/qt/
  (the version bundled by the PySide6 release used at build time).
- **Obligations met**: Qt is linked **dynamically**; the shared libraries sit
  as separate files next to the executable, so a user may replace them with a
  modified build of the same library. The library is not modified. Qt is used
  under its open-source licence only (no commercial Qt in this product).

## PySide6 (Qt for Python)

- **Licence**: GNU Lesser General Public License v3.0 (LGPL-3.0).
- **Source**: https://code.qt.io/cgit/pyside/pyside-setup.git

## Python 3.12 (CPython)

- **Licence**: Python Software Foundation License Version 2.
- **Text**: https://docs.python.org/3/license.html

## PyInstaller

- **Licence**: GPL-2.0-or-later with the PyInstaller bootloader exception, which
  grants unlimited permission to bundle and distribute non-GPL applications.
- **Text**: https://pyinstaller.org/en/stable/license.html

## tufup and python-tuf

- **Licence**: MIT.
- **Source**: https://pypi.org/project/tufup/ · https://github.com/theupdateframework/python-tuf

## This application

Copyright (c) 2026 Jordy Swinnen. All rights reserved (see the repository
LICENSE); the terms above apply to the third-party components they cover.
