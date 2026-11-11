"""Library detection: Steam and EmuDeck roots, plus the Sunshine source.

Default roots come from the scanner's ``jordylab_scan.paths`` (kept byte-for-byte
compatible); roots configured in the desktop config are treated as explicit, so
a missing explicit root surfaces as a skipped root instead of silently vanishing.
"""

import os
import sys
from dataclasses import dataclass, field
from pathlib import Path

from jordylab_scan.paths import default_roots

from .sunshine import sunshine_apps, sunshine_config_dir

STEAM = "STEAM"
EMUDECK = "EMUDECK"
SUNSHINE = "SUNSHINE"

BASE_LIBRARY_TYPES = (STEAM, EMUDECK)


@dataclass
class DetectedLibrary:
    library_type: str
    roots: list = field(default_factory=list)
    explicit: bool = False
    sunshine_apps: list = field(default_factory=list)

    @property
    def available(self):
        if self.library_type == SUNSHINE:
            return bool(self.sunshine_apps)

        return bool(self.roots)


def _windows_default_roots(library_type):
    """Windows defaults (the scanner never supported Windows, spec 003)."""
    if sys.platform != "win32" or library_type != STEAM:
        return []
    candidates = [
        Path(os.environ.get("PROGRAMFILES(X86)", r"C:\Program Files (x86)")) / "Steam",
        Path(os.environ.get("PROGRAMFILES", r"C:\Program Files")) / "Steam",
        Path(os.environ.get("LOCALAPPDATA", "")) / "Steam",
    ]

    return [str(path) for path in candidates if path.is_dir()]


def library_roots(library_type, extra_roots=None):
    """Roots for a library type and whether they are user-configured.

    Configured roots win; otherwise the scanner's probed defaults are used, with
    the app's Windows defaults as a fallback where the scanner has none.
    """
    configured = [str(path) for path in (extra_roots or {}).get(library_type, []) if path]
    if configured:
        return configured, True

    probed = [str(path) for path in default_roots(library_type)]
    if not probed:
        probed = _windows_default_roots(library_type)

    return probed, False


def detect_libraries(extra_roots=None):
    """All libraries this machine can sync, Sunshine included when configured."""
    detected = []
    for library_type in BASE_LIBRARY_TYPES:
        roots, explicit = library_roots(library_type, extra_roots)
        detected.append(DetectedLibrary(library_type, roots, explicit))
    if sunshine_config_dir().is_dir():
        detected.append(
            DetectedLibrary(SUNSHINE, sunshine_apps=sunshine_apps())
        )

    return detected
