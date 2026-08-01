#!/usr/bin/env bash
set -euo pipefail

# Read the tool input from stdin
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

if [[ -z "$COMMAND" ]]; then
  exit 0
fi

# Block rm -rf/-fr (recursive + force, in any flag order/combination) targeting
# root, home, or system-level paths. Project-relative targets (e.g. "rm -rf build/")
# are intentionally left alone.
RM_FLAGS_RE='(-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*r[a-zA-Z]*|-r[[:space:]]+-f|-f[[:space:]]+-r|--recursive[[:space:]]+--force|--force[[:space:]]+--recursive|-r[[:space:]]+--force|--force[[:space:]]+-r|--recursive[[:space:]]+-f|-f[[:space:]]+--recursive)'
RM_DANGEROUS_TARGET_RE='(^|[[:space:]])(/|~|~/[^[:space:]]*|\$HOME[^[:space:]]*|\.|\./|\*|/home[^[:space:]]*|/etc[^[:space:]]*|/usr[^[:space:]]*|/var[^[:space:]]*|/bin[^[:space:]]*|/boot[^[:space:]]*|/opt[^[:space:]]*|/root[^[:space:]]*)([[:space:]]|$)'

if echo "$COMMAND" | grep -qE "rm[[:space:]]+${RM_FLAGS_RE}" && echo "$COMMAND" | grep -qE "$RM_DANGEROUS_TARGET_RE"; then
  echo "BLOCKED: Destructive rm -rf against a root/home/system path is not allowed." >&2
  exit 2
fi

# Block --no-preserve-root unconditionally, regardless of other rm flags.
if echo "$COMMAND" | grep -qE '\brm\b.*--no-preserve-root'; then
  echo "BLOCKED: rm --no-preserve-root is not allowed." >&2
  exit 2
fi

# Block git reset --hard
if echo "$COMMAND" | grep -qE 'git\s+reset\s+--hard'; then
  echo "BLOCKED: git reset --hard is not allowed. Use git stash or git checkout for specific files." >&2
  exit 2
fi

# Block git clean with a force flag (-f, -fd, -fdx, --force, ...) since it
# permanently deletes untracked files. Non-forcing dry-run/interactive forms
# (git clean -n, git clean -i) are left alone.
if echo "$COMMAND" | grep -qE 'git\s+clean\b' && echo "$COMMAND" | grep -qE -- '-[a-zA-Z]*f[a-zA-Z]*\b|--force\b'; then
  echo "BLOCKED: git clean -f permanently deletes untracked files and is not allowed. Use git clean -n to preview, or -i for interactive." >&2
  exit 2
fi

# Block git push --force / -f, but explicitly allow --force-with-lease.
if echo "$COMMAND" | grep -qE 'git\s+push\b'; then
  if ! echo "$COMMAND" | grep -qE -- '--force-with-lease\b'; then
    if echo "$COMMAND" | grep -qE -- '--force\b|(^|[[:space:]])-f([[:space:]]|$)'; then
      echo "BLOCKED: git push --force is not allowed. Use --force-with-lease if you must force push." >&2
      exit 2
    fi
  fi
fi

# Block direct DDL
if echo "$COMMAND" | grep -qiE '(DROP\s+(TABLE|SCHEMA|DATABASE)|TRUNCATE)'; then
  echo "BLOCKED: Direct DDL (DROP/TRUNCATE) is not allowed. Use Flyway migrations instead." >&2
  exit 2
fi

# Block docker compose down -v/--volumes, including the hyphenated docker-compose
# form and flags placed between the subcommand and "down".
if echo "$COMMAND" | grep -qE '(docker\s+compose|docker-compose).*\bdown\b.*-v'; then
  echo "BLOCKED: docker compose down -v destroys volumes. Use docker compose down without -v." >&2
  exit 2
fi

exit 0
