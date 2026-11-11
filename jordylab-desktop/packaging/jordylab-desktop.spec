# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for the JordyLab sync app (macOS aarch64 and Linux x86_64).

The scanner package is added through ``pathex`` so the shared scan rules are
bundled without being copied into this tree.
"""

from pathlib import Path

root = Path(SPECPATH).parent
scanner = root.parent / "gamecatalog-scanner" / "src"

a = Analysis(
    [str(root / "packaging" / "launcher.py")],
    pathex=[str(root / "src"), str(scanner)],
    binaries=[],
    datas=[],
    hiddenimports=[
        "jordylab_scan",
        "jordylab_scan.api",
        "jordylab_scan.auth",
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
    a.binaries,
    a.datas,
    [],
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
