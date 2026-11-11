"""Frozen-app entry point (PyInstaller runs a script, not a package module)."""

from jordylab_desktop.app import main

raise SystemExit(main())
