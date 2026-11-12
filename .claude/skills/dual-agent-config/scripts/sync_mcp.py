#!/usr/bin/env python3
"""Translate MCP server definitions between Claude Code and OpenCode 2 formats.

Claude Code stores servers under `mcpServers` in .mcp.json, splitting the
executable (`command`) from its arguments (`args`). OpenCode 2 stores them under
`mcp.servers` in opencode.json(c) with a single `command` array, `environment`
rather than `env`, `disabled` rather than `enabled`, and snake_case OAuth keys.
OpenCode 1 entries (servers directly under `mcp`) are read too, and moved under
`mcp.servers` when OpenCode is the target.

Previews by default. Pass --write to actually modify the target file (a .bak
copy is made first). An opencode.jsonc source is read with its comments; an
opencode.jsonc target is preview-only, because rewriting it would drop them.

OAuth settings (client id, callback port, scope) are carried across. Each
harness keeps its own callback port when the target already has the server,
because Claude Code and OpenCode run separate OAuth callbacks. A client secret
in either file is refused.

Usage:
    python sync_mcp.py to-opencode --source .mcp.json --target opencode.json
    python sync_mcp.py to-claude   --source opencode.json --target .mcp.json --write
"""

import argparse
import json
import re
import shutil
import sys
from pathlib import Path

CLAUDE_KEY = "mcpServers"
OPENCODE_KEY = "mcp"
# Members of OpenCode's `mcp` object that are settings, not V1 server names.
OPENCODE_MCP_SETTINGS = {"servers", "timeout"}


class TranslationError(Exception):
    """Raised when a server entry cannot be translated safely."""


def strip_jsonc(text: str) -> str:
    """Remove // and /* */ comments and trailing commas, leaving strings untouched."""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
        elif text.startswith("//", i):
            end = text.find("\n", i)
            i = n if end < 0 else end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
        elif c == "," and re.match(r"\s*[}\]]", text[i + 1:]):
            i += 1  # trailing comma
        else:
            out.append(c)
            i += 1
    return "".join(out)


def load_json(path: Path) -> dict:
    if not path.exists():
        raise TranslationError(f"File not found: {path}")
    try:
        return json.loads(strip_jsonc(path.read_text(encoding="utf-8")))
    except json.JSONDecodeError as exc:
        raise TranslationError(f"{path} is not valid JSON or JSONC: {exc}") from exc


def opencode_servers(doc: dict) -> dict:
    """All servers in an OpenCode config: V2 `mcp.servers` plus V1 entries directly under `mcp`."""
    mcp = doc.get(OPENCODE_KEY) or {}
    if not isinstance(mcp, dict):
        return {}
    v1 = {name: entry for name, entry in mcp.items() if name not in OPENCODE_MCP_SETTINGS}
    servers = mcp.get("servers") if isinstance(mcp.get("servers"), dict) else {}
    return {**v1, **servers}


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
        translated = {"type": "local", "command": merged}

    elif server_type in ("http", "sse"):
        url = entry.get("url")
        if not url:
            raise TranslationError(f"'{name}' is a remote server with no url")
        translated = {"type": "remote", "url": url}
        if server_type == "sse":
            warnings.append(
                f"'{name}': OpenCode 2 speaks Streamable HTTP only; check that this SSE "
                "server also serves Streamable HTTP at the same URL."
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
    if entry.get("oauth") is not None:
        translated["oauth"] = translate_oauth(name, entry["oauth"], "opencode", warnings)

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
    if entry.get("oauth") is False:
        warnings.append(
            f"'{name}' turns OAuth off in OpenCode (oauth: false). Claude Code has no "
            "such switch; it authenticates with the headers only if the server allows it."
        )
    elif entry.get("oauth") is not None:
        translated["oauth"] = translate_oauth(name, entry["oauth"], "claude", warnings)

    if entry.get("disabled") is True or entry.get("enabled") is False:
        warnings.append(
            f"'{name}' is disabled in OpenCode. Claude Code has no disabled flag, "
            "so it was translated as active. Remove it if that is wrong."
        )

    flag_secret_syntax(name, translated, "claude", warnings)
    return translated


def to_opencode_v2(name: str, entry: dict, warnings: list) -> dict:
    """Rewrite an OpenCode 1 server entry in the native OpenCode 2 shape."""
    entry = dict(entry)
    changed = False
    if "enabled" in entry:
        if entry.pop("enabled") is False:
            entry["disabled"] = True
        changed = True
    if isinstance(entry.get("timeout"), int):
        ms = entry.pop("timeout")
        entry["timeout"] = {"catalog": ms, "execution": ms}
        changed = True
    if isinstance(entry.get("oauth"), dict) and set(entry["oauth"]) & set(OAUTH_KEYS["claude"]):
        entry["oauth"] = translate_oauth(name, entry["oauth"], "opencode", warnings)
        changed = True
    if changed:
        warnings.append(f"'{name}': converted the existing OpenCode 1 entry to the OpenCode 2 shape.")
    return entry


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


# OAuth keys each harness understands, as {claude_name: opencode_name}. OpenCode 1 used the Claude spelling.
OAUTH_KEYS = {
    "claude": {"clientId": "client_id", "callbackPort": "callback_port"},
    "opencode": {"clientId": "client_id", "callbackPort": "callback_port", "scope": "scope"},
}
PORT_KEY = {"claude": "callbackPort", "opencode": "callback_port"}


def translate_oauth(name: str, oauth, target: str, warnings: list) -> dict:
    """Carry the OAuth settings for one server across the two formats.

    Accepts camelCase (Claude Code, OpenCode 1) and snake_case (OpenCode 2) keys
    and writes the target's spelling. A client secret is refused outright:
    config files are checked in, so a secret must come from the environment or
    a prompt, never from the file.
    """
    if not isinstance(oauth, dict):
        raise TranslationError(f"'{name}' has an oauth value that is not an object")
    if "clientSecret" in oauth or "client_secret" in oauth:
        raise TranslationError(
            f"'{name}' puts a client secret in its config. Remove it: a checked-in "
            "config must not carry a secret."
        )
    names = OAUTH_KEYS[target]
    result, dropped = {}, []
    for key, value in oauth.items():
        camel = next((c for c, s in OAUTH_KEYS["opencode"].items() if key in (c, s)), None)
        if camel in names:
            result[camel if target == "claude" else names[camel]] = value
        else:
            dropped.append(key)
    if dropped:
        warnings.append(
            f"'{name}': oauth field(s) {', '.join(sorted(dropped))} were not translated to "
            f"{target}. Check them by hand."
        )
    return result


def merge_servers(existing: dict, translated: dict, warnings: list, target: str = "opencode") -> dict:
    """Merge translated servers into the target's existing entries.

    Servers are replaced as a whole, except for two things that are per-harness
    by design: the target's own callback port is kept (Claude Code and OpenCode
    each run their own OAuth callback), and an oauth block the source does not
    mention is kept rather than dropped silently.
    """
    port = PORT_KEY[target]
    merged = dict(existing)
    for name, entry in translated.items():
        old = existing.get(name)
        old_oauth = old.get("oauth") if isinstance(old, dict) else None
        new_oauth = entry.get("oauth")
        if isinstance(old_oauth, dict) and isinstance(new_oauth, dict):
            oauth = {**old_oauth, **new_oauth}
            if port in old_oauth and old_oauth[port] != new_oauth.get(port):
                oauth[port] = old_oauth[port]
                warnings.append(
                    f"'{name}': kept the existing {port} {old_oauth[port]}. "
                    "Each harness has its own callback port, so the source's value is not copied."
                )
            entry = {**entry, "oauth": oauth}
        elif isinstance(old_oauth, dict) and new_oauth is None:
            entry = {**entry, "oauth": dict(old_oauth)}
            warnings.append(
                f"'{name}': the source has no oauth block, so the existing one was kept."
            )
        elif isinstance(new_oauth, dict) and port in new_oauth:
            warnings.append(
                f"'{name}': a new entry takes {port} {new_oauth[port]} from the "
                "source. Make sure no other server or harness already uses that port."
            )
        merged[name] = entry
    return merged


def translate(direction: str, source_doc: dict) -> tuple:
    if direction == "to-opencode":
        servers = source_doc.get(CLAUDE_KEY, {})
        convert = claude_entry_to_opencode
    else:
        servers = opencode_servers(source_doc)
        convert = opencode_entry_to_claude

    if not servers:
        raise TranslationError(
            "No servers found. Expected a non-empty "
            + ("'mcpServers' object." if direction == "to-opencode" else "'mcp.servers' (or OpenCode 1 'mcp') object.")
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

    return translated, warnings, failed


def write_target(direction: str, target_doc: dict, translated: dict, warnings: list) -> tuple:
    """Merge the translated servers into the target document. Returns (doc, overwritten names)."""
    if direction == "to-claude":
        existing = target_doc.get(CLAUDE_KEY, {})
        target_doc[CLAUDE_KEY] = merge_servers(existing, translated, warnings, "claude")
        return target_doc, sorted(set(existing) & set(translated))

    existing = {name: to_opencode_v2(name, entry, warnings) if isinstance(entry, dict) else entry
                for name, entry in opencode_servers(target_doc).items()}
    mcp = {key: value for key, value in (target_doc.get(OPENCODE_KEY) or {}).items() if key in OPENCODE_MCP_SETTINGS}
    mcp["servers"] = merge_servers(existing, translated, warnings, "opencode")
    target_doc[OPENCODE_KEY] = mcp
    target_doc.setdefault("$schema", "https://opencode.ai/config.json")
    return target_doc, sorted(set(existing) & set(translated))


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Translate MCP server definitions between Claude Code and OpenCode 2."
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

    if args.write and args.target.suffix == ".jsonc":
        print(f"error: {args.target} is JSONC; writing it would drop its comments. "
              "Preview without --write and copy the servers in by hand.", file=sys.stderr)
        return 1

    try:
        source_doc = load_json(args.source)
        translated, warnings, failed = translate(args.direction, source_doc)
        target_doc = load_json(args.target) if args.target.exists() else {}
    except TranslationError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    target_doc, overwritten = write_target(args.direction, target_doc, translated, warnings)

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
