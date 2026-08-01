#!/usr/bin/env bash
set -euo pipefail

# Read the tool input from stdin
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

if [[ -z "$COMMAND" ]]; then
  exit 0
fi

# Block recursive rm (with or without an explicit force flag) targeting root,
# home, or system-level paths. Project-relative targets (e.g. "rm -rf build/")
# are intentionally left alone. Force is deliberately NOT required: an agent
# invocation is always non-interactive, and GNU "rm -r" without "-f" still
# deletes every regular file recursively with no prompt (it only stumbles on
# individual write-protected files) — so "rm -r /" is still catastrophic.
# Recursive is matched as an independent, space-bounded token rather than
# part of an adjacency-dependent regex, so any number of intervening flags
# (e.g. "rm -r -v /home/x") is still caught, and it can't match inside an
# unrelated word (e.g. "confirm").
RM_RECURSIVE_RE='(^|[[:space:]])(-[a-zA-Z]*r[a-zA-Z]*|--recursive)([[:space:]]|$)'
RM_DANGEROUS_TARGET_RE='(^|[[:space:]])(/|~|~/[^[:space:]]*|\$HOME[^[:space:]]*|\.|\./|\.\.|\.\./|\*|/home[^[:space:]]*|/etc[^[:space:]]*|/usr[^[:space:]]*|/var[^[:space:]]*|/bin[^[:space:]]*|/boot[^[:space:]]*|/opt[^[:space:]]*|/root[^[:space:]]*)([[:space:]]|$)'

# Each rule below is checked against a single command segment (see the split
# at the bottom of this file), not the raw multi-command string, so a token
# from an unrelated chained command can't combine with another to trigger a
# false block (e.g. "ls /home/jordy && rm -rf build/" must not block).
check_segment() {
  local seg="$1"

  if echo "$seg" | grep -qE '\brm\b' && echo "$seg" | grep -qE "$RM_RECURSIVE_RE" && echo "$seg" | grep -qE "$RM_DANGEROUS_TARGET_RE"; then
    echo "BLOCKED: Destructive recursive rm against a root/home/system path is not allowed." >&2
    return 2
  fi

  # Block --no-preserve-root unconditionally, regardless of other rm flags.
  if echo "$seg" | grep -qE '\brm\b.*--no-preserve-root'; then
    echo "BLOCKED: rm --no-preserve-root is not allowed." >&2
    return 2
  fi

  # Block git reset --hard
  if echo "$seg" | grep -qE '\bgit\b\s+reset\s+--hard'; then
    echo "BLOCKED: git reset --hard is not allowed. Use git stash or git checkout for specific files." >&2
    return 2
  fi

  # Block git clean with a force flag (-f, -fd, -fdx, --force, ...) since it
  # permanently deletes untracked files. Non-forcing dry-run/interactive forms
  # (git clean -n, git clean -i) are left alone.
  if echo "$seg" | grep -qE '\bgit\b\s+clean\b' && echo "$seg" | grep -qE -- '-[a-zA-Z]*f[a-zA-Z]*\b|--force\b'; then
    echo "BLOCKED: git clean -f permanently deletes untracked files and is not allowed. Use git clean -n to preview, or -i for interactive." >&2
    return 2
  fi

  # Block git push --force / -f, but explicitly allow --force-with-lease.
  if echo "$seg" | grep -qE '\bgit\b\s+push\b'; then
    if ! echo "$seg" | grep -qE -- '--force-with-lease\b'; then
      if echo "$seg" | grep -qE -- '--force\b|(^|[[:space:]])-f([[:space:]]|$)'; then
        echo "BLOCKED: git push --force is not allowed. Use --force-with-lease if you must force push." >&2
        return 2
      fi
    fi
  fi

  # Block direct DDL
  if echo "$seg" | grep -qiE '(DROP\s+(TABLE|SCHEMA|DATABASE)|TRUNCATE)'; then
    echo "BLOCKED: Direct DDL (DROP/TRUNCATE) is not allowed. Use Flyway migrations instead." >&2
    return 2
  fi

  # Block docker compose down -v/--volumes, including the hyphenated docker-compose
  # form and flags placed between the subcommand and "down". The volumes flag is
  # matched as a bounded token so it can't match inside an unrelated argument
  # (e.g. "--env-file .env-vault").
  if echo "$seg" | grep -qE '(docker\s+compose|docker-compose)' && echo "$seg" | grep -qE '\bdown\b' && echo "$seg" | grep -qE -- '(^|[[:space:]])(-v|--volumes)([[:space:]]|$)'; then
    echo "BLOCKED: docker compose down -v destroys volumes. Use docker compose down without -v." >&2
    return 2
  fi

  return 0
}

# Split on command separators (longest alternatives first so "||" isn't
# consumed as two "|"s) and evaluate each piece independently.
while IFS= read -r segment; do
  [[ -z "$segment" ]] && continue
  if ! check_segment "$segment"; then
    exit 2
  fi
done < <(printf '%s\n' "$COMMAND" | sed -E 's/(&&|\|\||;|\|)/\n/g')

exit 0
