#!/usr/bin/env python3
"""Translate MCP server definitions between Claude Code and OpenCode formats.

Claude Code stores servers under `mcpServers` in .mcp.json, splitting the
executable (`command`) from its arguments (`args`). OpenCode stores them under
`mcp` in opencode.json with a single `command` array, and uses `environment`
rather than `env`. This script handles that translation in both directions.

Previews by default. Pass --write to actually modify the target file (a .bak
copy is made first).

Usage:
    python sync_mcp.py to-opencode --source .mcp.json --target opencode.json
    python sync_mcp.py to-claude   --source opencode.json --target .mcp.json --write
"""

import argparse
import json
import shutil
import sys
from pathlib import Path

CLAUDE_KEY = "mcpServers"
OPENCODE_KEY = "mcp"


class TranslationError(Exception):
    """Raised when a server entry cannot be translated safely."""


def load_json(path: Path) -> dict:
    if not path.exists():
        raise TranslationError(f"File not found: {path}")
    try:
        with path.open(encoding="utf-8") as handle:
            return json.load(handle)
    except json.JSONDecodeError as exc:
        raise TranslationError(f"{path} is not valid JSON: {exc}") from exc


def claude_entry_to_opencode(name: str, entry: dict, warnings: list) -> dict:
    server_type = entry.get("type")

    # Claude Code treats a missing type with a command present as stdio.
    if server_type is None:
        server_type = "stdio" if "command" in entry else "http"

    if server_type == "stdio":
        command = entry.get("command")
        if not command:
            raise TranslationError(f"'{name}' is a stdio server with no command")
        args = entry.get("args", [])
        if isinstance(command, list):
            merged = list(command) + list(args)
        else:
            merged = [command] + list(args)
        translated = {"type": "local", "command": merged, "enabled": True}

    elif server_type in ("http", "sse"):
        url = entry.get("url")
        if not url:
            raise TranslationError(f"'{name}' is a remote server with no url")
        translated = {"type": "remote", "url": url, "enabled": True}
        if server_type == "sse":
            warnings.append(
                f"'{name}': SSE transport is deprecated; verify OpenCode accepts "
                "this server as a plain remote endpoint."
            )

    elif server_type == "websocket":
        raise TranslationError(
            f"'{name}' uses websocket transport, which has no documented OpenCode "
            "equivalent. Translate this one by hand."
        )

    else:
        raise TranslationError(f"'{name}' has unrecognized type '{server_type}'")

    if entry.get("env"):
        translated["environment"] = dict(entry["env"])
    if entry.get("headers"):
        translated["headers"] = dict(entry["headers"])
        warnings.append(
            f"'{name}': headers were copied as-is. Confirm the field name against "
            "https://opencode.ai/docs/mcp-servers/ before relying on it."
        )

    flag_secret_syntax(name, translated, "opencode", warnings)
    return translated


def opencode_entry_to_claude(name: str, entry: dict, warnings: list) -> dict:
    server_type = entry.get("type")

    if server_type is None:
        server_type = "local" if "command" in entry else "remote"

    if server_type == "local":
        command = entry.get("command")
        if not command:
            raise TranslationError(f"'{name}' is a local server with no command")
        if isinstance(command, str):
            parts = command.split()
        else:
            parts = list(command)
        if not parts:
            raise TranslationError(f"'{name}' has an empty command array")
        translated = {"type": "stdio", "command": parts[0], "args": parts[1:]}

    elif server_type == "remote":
        url = entry.get("url")
        if not url:
            raise TranslationError(f"'{name}' is a remote server with no url")
        translated = {"type": "http", "url": url}

    else:
        raise TranslationError(f"'{name}' has unrecognized type '{server_type}'")

    if entry.get("environment"):
        translated["env"] = dict(entry["environment"])
    if entry.get("headers"):
        translated["headers"] = dict(entry["headers"])

    if entry.get("enabled") is False:
        warnings.append(
            f"'{name}' is disabled in OpenCode. Claude Code has no enabled flag — "
            "it was translated as active. Remove it if that is wrong."
        )

    flag_secret_syntax(name, translated, "claude", warnings)
    return translated


def flag_secret_syntax(name: str, entry: dict, target: str, warnings: list) -> None:
    """Warn about env-var syntax that will not survive the translation.

    The two tools use incompatible expansion syntax, so a faithfully copied
    value becomes a literal string in the target and fails confusingly.
    """
    blob = json.dumps(entry)
    if target == "opencode" and "${" in blob:
        warnings.append(
            f"'{name}' uses Claude Code's ${{VAR}} expansion. OpenCode expects "
            "{env:VAR} — convert these by hand."
        )
    if target == "claude" and "{env:" in blob:
        warnings.append(
            f"'{name}' uses OpenCode's {{env:VAR}} expansion. Claude Code expects "
            "${VAR} — convert these by hand."
        )


def translate(direction: str, source_doc: dict) -> tuple:
    if direction == "to-opencode":
        servers = source_doc.get(CLAUDE_KEY, {})
        convert, target_key = claude_entry_to_opencode, OPENCODE_KEY
    else:
        servers = source_doc.get(OPENCODE_KEY, {})
        convert, target_key = opencode_entry_to_claude, CLAUDE_KEY

    if not servers:
        raise TranslationError(
            f"No servers found. Expected a non-empty "
            f"'{CLAUDE_KEY if direction == 'to-opencode' else OPENCODE_KEY}' object."
        )

    warnings, translated, failed = [], {}, []
    for name, entry in servers.items():
        if not isinstance(entry, dict):
            failed.append(f"'{name}': entry is not an object")
            continue
        try:
            translated[name] = convert(name, entry, warnings)
        except TranslationError as exc:
            failed.append(str(exc))

    return target_key, translated, warnings, failed


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Translate MCP server definitions between Claude Code and OpenCode."
    )
    parser.add_argument("direction", choices=["to-opencode", "to-claude"])
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--target", required=True, type=Path)
    parser.add_argument(
        "--write",
        action="store_true",
        help="Apply changes. Without this flag the result is only printed.",
    )
    args = parser.parse_args()

    try:
        source_doc = load_json(args.source)
        target_key, translated, warnings, failed = translate(args.direction, source_doc)
    except TranslationError as exc:
        print(f"error: {exc}", file=sys.stderr)

        return 1

    target_doc = {}
    if args.target.exists():
        try:
            target_doc = load_json(args.target)
        except TranslationError as exc:
            print(f"error reading target: {exc}", file=sys.stderr)

            return 1

    existing = target_doc.get(target_key, {})
    overwritten = sorted(set(existing) & set(translated))
    merged = {**existing, **translated}
    target_doc[target_key] = merged

    if target_key == OPENCODE_KEY and "$schema" not in target_doc:
        target_doc["$schema"] = "https://opencode.ai/config.json"

    print(f"translated {len(translated)} server(s): {', '.join(sorted(translated)) or 'none'}")
    if overwritten:
        print(f"overwriting existing entries: {', '.join(overwritten)}")
    for problem in failed:
        print(f"  SKIPPED  {problem}")
    for warning in warnings:
        print(f"  WARNING  {warning}")

    rendered = json.dumps(target_doc, indent=2) + "\n"

    if not args.write:
        print(f"\n--- preview of {args.target} (not written; pass --write to apply) ---")
        print(rendered, end="")

        return 0

    if args.target.exists():
        backup = args.target.with_suffix(args.target.suffix + ".bak")
        shutil.copy2(args.target, backup)
        print(f"backed up existing file to {backup}")

    args.target.write_text(rendered, encoding="utf-8")
    print(f"wrote {args.target}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
