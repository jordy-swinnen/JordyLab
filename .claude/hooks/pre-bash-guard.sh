#!/usr/bin/env bash
# Thin shim so the PreToolUse hook registration in .claude/settings.json
# doesn't need to change. The real logic — a proper (bounded) shell
# tokenizer, not regex/glob heuristics — lives in pre_bash_guard.py; see
# that file for why.
set -euo pipefail
exec python3 "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/pre_bash_guard.py"
