# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for the JordyLab sync app (macOS aarch64, Linux x86_64, Windows x64).

Built **one-dir** on purpose: the Qt shared libraries stay as separate files next
to the executable, which is what the LGPLv3 requirement to allow replacing the
library needs (see THIRD-PARTY-NOTICES.md). Do not switch to one-file.

The scanner package is added through ``pathex`` so the shared scan rules are
bundled without being copied into this tree.
"""

from pathlib import Path

root = Path(SPECPATH).parent
core = root / "core"
notices = root / "THIRD-PARTY-NOTICES.md"

a = Analysis(
    [str(root / "packaging" / "launcher.py")],
    pathex=[str(root / "src"), str(core)],
    binaries=[],
    datas=[(str(notices), ".")],
    hiddenimports=[
        "jordylab_scan",
        "jordylab_scan.api",
        "jordylab_scan.auth",
        "jordylab_scan.collect",
        "jordylab_scan.config",
        "jordylab_scan.grouping",
        "jordylab_scan.manifest",
        "jordylab_scan.paths",
        "jordylab_scan.payload",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="jordylab-desktop",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    name="jordylab-desktop",
)
