# Contract: shared lint command

`jordylab-fe/tools/lint-changed.sh <file>...` — the single entry point both agent tools and CI-adjacent scripts call.
It knows nothing about Claude Code or OpenCode.

| Aspect | Contract |
|--------|----------|
| Arguments | one or more file paths (absolute or relative to the repo root) |
| Filtering | silently drops files that are not `.ts` under `jordylab-fe/` or that match the ESLint ignores |
| Runner | Oxlint through `bunx` with `jordylab-fe/.oxlintrc.json`; if the baseline decision selected ESLint, ESLint on the same files (same output format) |
| Output | `path:line:col  rule  message`, one per line, nothing else; empty output means clean or skipped |
| Exit code | `0` always for skip/clean/findings when run by a hook (`--strict` flag exits `1` on findings, for manual use) |
| Time limit | internal guard (default 3 s); on expiry prints nothing and exits `0` |
| Missing linter / parse failure | prints nothing, exits `0` |
| Side effects | none (read-only; never fixes) |

# Contract: Claude Code hook `post-lint-check.sh`

| Aspect | Contract |
|--------|----------|
| Registration | `.claude/settings.json` PostToolUse, matcher `Write|Edit|MultiEdit`, with a `timeout` of 5 s |
| Input | tool input JSON on stdin; `file_path` read with `jq` (same pattern as the other post-edit hooks) |
| Action | take the format lock (wait up to 2 s, else skip), call `lint-changed.sh` on the one file, release |
| Output on findings | `{"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":"<diagnostics>"}}`, exit 0 |
| Output otherwise | nothing, exit 0 |
| Never | blocks, edits the file, prints anything on skip, or runs longer than its limit |

`post-edit-format.sh` takes the same lock around its Prettier call (and switches `npx` to `bunx`).
Fixture cases (`.claude/hooks/tests/lint-cases.sh`): error file, clean file, ignored path, non-TypeScript file,
outside `jordylab-fe`, missing linter binary, time limit exceeded, concurrent run with the format hook.

# OpenCode instruction (in `jordylab-fe/AGENTS.md`)

"After editing a TypeScript file under `jordylab-fe/`, run `tools/lint-changed.sh <file>` and fix what it reports."
A plugin calling the same script is added only if the instruction is unreliable.
