#!/usr/bin/env python3
"""Freeze the ``jordylab_scan`` package into a single served Python file.

Deterministic: modules are concatenated in a fixed order with LF line endings.
The frozen file begins with a rendered constants header that the backend's
``ClientService`` substitutes via ``${...}`` placeholders.

Usage:
    python tools/build_client.py            # write the committed template
    python tools/build_client.py --check    # fail (exit 1) if it drifted
    python tools/build_client.py --local    # render local defaults for selftest
"""

import argparse
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = PROJECT_ROOT.parent
SRC_DIR = PROJECT_ROOT / "src" / "jordylab_scan"
OUTPUT_PATH = (
    REPO_ROOT
    / "jordylab-be"
    / "src"
    / "main"
    / "resources"
    / "scripts"
    / "jordylab-scan-template.py"
)

MODULE_ORDER = [
    "__init__",
    "normalize",
    "platforms",
    "paths",
    "config",
    "lock",
    "manifest",
    "grouping",
    "payload",
    "auth",
    "api",
    "status",
    "schedule",
    "__main__",
]

HEADER_TEMPLATE = (
    "# Rendered by jordylab-be ClientService. Do not edit by hand.\n"
    'KEYCLOAK_URL = "${KEYCLOAK_URL}"\n'
    'REALM = "${REALM}"\n'
    'CLIENT_ID = "${CLIENT_ID}"\n'
    'BACKEND_URL = "${BACKEND_URL}"\n'
    'SCAN_ENDPOINT = "${SCAN_ENDPOINT}"\n'
    'LIBRARY_TYPE = "${LIBRARY_TYPE}"\n'
)

LOCAL_DEFAULTS = {
    "KEYCLOAK_URL": "http://localhost:8180",
    "REALM": "jordylab",
    "CLIENT_ID": "gamecatalog-script",
    "BACKEND_URL": "http://localhost:8080",
    "SCAN_ENDPOINT": "/api/gamecatalog/ingest/scan",
    "LIBRARY_TYPE": "EMUDECK",
}


def _is_intra_package_import(stripped):
    return (
        stripped.startswith("from .")
        or stripped.startswith("from __future__")
        or stripped.startswith("from jordylab_scan")
        or stripped.startswith("import jordylab_scan")
    )


def strip_module(source):
    """Drop intra-package imports so the flattened modules resolve globally."""
    kept = []
    skip_continuation = False
    for line in source.splitlines():
        stripped = line.strip()
        if skip_continuation:
            if ")" in stripped:
                skip_continuation = False
            continue
        if _is_intra_package_import(stripped):
            if "(" in stripped and ")" not in stripped:
                skip_continuation = True
            continue
        kept.append(line.rstrip())
    while kept and not kept[-1]:
        kept.pop()

    return "\n".join(kept)


def render_header(local=False):
    header = HEADER_TEMPLATE
    if local:
        for key, value in LOCAL_DEFAULTS.items():
            header = header.replace(f"${{{key}}}", value)

    return header


def generate(local=False):
    chunks = [render_header(local)]
    for module in MODULE_ORDER:
        source = (SRC_DIR / (module + ".py")).read_text(encoding="utf-8")
        chunks.append(strip_module(source))
    text = "\n\n".join(chunks)
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    if not text.endswith("\n"):
        text += "\n"

    return text


def placeholder_outside_header(text, local=False):
    header_lines = render_header(local).count("\n")
    tail = "\n".join(text.splitlines()[header_lines:])

    return "$" + "{" in tail


def main(argv=None):
    parser = argparse.ArgumentParser(description="Freeze the scan client into one file")
    parser.add_argument(
        "--check", action="store_true", help="verify the committed template is current"
    )
    parser.add_argument("--local", action="store_true", help="render local defaults for selftest")
    args = parser.parse_args(argv)

    generated = generate(local=args.local)

    if args.local:
        OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT_PATH.write_text(generated, encoding="utf-8")
        print(f"Wrote local-rendered client to {OUTPUT_PATH}")

        return 0

    if args.check:
        if not OUTPUT_PATH.is_file():
            print("ERROR: {0} is missing".format(OUTPUT_PATH), file=sys.stderr)

            return 1
        committed = OUTPUT_PATH.read_text(encoding="utf-8")
        if committed != generated:
            print(
                "ERROR: {0} has drifted; run 'python tools/build_client.py'".format(OUTPUT_PATH),
                file=sys.stderr,
            )

            return 1
        if placeholder_outside_header(committed):
            print(
                "ERROR: '{0}' placeholders appear outside the constants header".format("$" + "{"),
                file=sys.stderr,
            )

            return 1
        print("OK: frozen client is up to date")

        return 0

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(generated, encoding="utf-8")
    print(f"Wrote frozen client to {OUTPUT_PATH}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
