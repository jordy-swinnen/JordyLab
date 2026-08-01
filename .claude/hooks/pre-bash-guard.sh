#!/usr/bin/env bash
set -euo pipefail

# Read the tool input from stdin
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

if [[ -z "$COMMAND" ]]; then
  exit 0
fi

# Anchors a check to the segment actually invoking the given command, not
# merely mentioning it anywhere (e.g. inside a quoted echo argument).
# Tolerates a leading env-var assignment or a bare sudo/exec/command wrapper.
# Does NOT handle command substitution, subshells, or backgrounding — an
# accepted residual gap, same class as this hook's quote-unawareness, that
# would need real shell tokenization to close properly.
CMD_PREFIX_RE='^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*[[:space:]]+)*(sudo[[:space:]]+|exec[[:space:]]+|command[[:space:]]+)?'

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
# false block (e.g. "ls /home/jordy && rm -rf build/" must not block). The
# command-invocation check itself is anchored to the start of the segment
# (via CMD_PREFIX_RE) so a command name merely mentioned inside another
# command's argument (e.g. "echo \"run: rm -rf /home\"") doesn't count either
# — only the flag/target checks that follow it remain unanchored substring
# matches, since by that point the segment is confirmed to actually invoke
# the command in question.
check_segment() {
  local seg="$1"

  if echo "$seg" | grep -qE "${CMD_PREFIX_RE}rm\b" && echo "$seg" | grep -qE "$RM_RECURSIVE_RE" && echo "$seg" | grep -qE "$RM_DANGEROUS_TARGET_RE"; then
    echo "BLOCKED: Destructive recursive rm against a root/home/system path is not allowed." >&2
    return 2
  fi

  # Block --no-preserve-root unconditionally, regardless of other rm flags.
  if echo "$seg" | grep -qE "${CMD_PREFIX_RE}rm\b.*--no-preserve-root"; then
    echo "BLOCKED: rm --no-preserve-root is not allowed." >&2
    return 2
  fi

  # Block git reset --hard
  if echo "$seg" | grep -qE "${CMD_PREFIX_RE}git\b\s+reset\s+--hard"; then
    echo "BLOCKED: git reset --hard is not allowed. Use git stash or git checkout for specific files." >&2
    return 2
  fi

  # Block git clean with a force flag (-f, -fd, -fdx, --force, ...) since it
  # permanently deletes untracked files. Non-forcing dry-run/interactive forms
  # (git clean -n, git clean -i) are left alone.
  if echo "$seg" | grep -qE "${CMD_PREFIX_RE}git\b\s+clean\b" && echo "$seg" | grep -qE -- '-[a-zA-Z]*f[a-zA-Z]*\b|--force\b'; then
    echo "BLOCKED: git clean -f permanently deletes untracked files and is not allowed. Use git clean -n to preview, or -i for interactive." >&2
    return 2
  fi

  # Block git push --force / -f as a COMPLETE flag, but allow --force-with-lease
  # alone. Checked unconditionally (not "only if --force-with-lease is absent"):
  # git applies last-flag-wins semantics, so "--force-with-lease --force" (or
  # the reverse order) is still an unconditional force push despite mentioning
  # --force-with-lease. The trailing boundary requires whitespace/end-of-string
  # rather than \b, since \b treats the hyphen in "--force-with-lease" as a
  # boundary too and would wrongly match that flag on its own.
  if echo "$seg" | grep -qE "${CMD_PREFIX_RE}git\b\s+push\b"; then
    if echo "$seg" | grep -qE -- '(^|[[:space:]])--force([[:space:]]|$)|(^|[[:space:]])-f([[:space:]]|$)'; then
      echo "BLOCKED: git push --force is not allowed. Use --force-with-lease if you must force push." >&2
      return 2
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
  if echo "$seg" | grep -qE "${CMD_PREFIX_RE}(docker\s+compose|docker-compose)" && echo "$seg" | grep -qE '\bdown\b' && echo "$seg" | grep -qE -- '(^|[[:space:]])(-v|--volumes)([[:space:]]|$)'; then
    echo "BLOCKED: docker compose down -v destroys volumes. Use docker compose down without -v." >&2
    return 2
  fi

  return 0
}

# Splits $1 on ; && || | into one segment per line, but only treats a
# separator as a real split point when it appears outside single or double
# quotes — a plain text-based split (e.g. sed) can't tell a real command
# separator from the same character appearing inside a quoted string (e.g.
# echo "warning; rm -rf /home is dangerous"), and would otherwise split that
# quoted text into a fragment that looks like a genuine rm invocation. Does
# NOT handle backslash-escaped quotes or command substitution/backticks —
# an accepted residual gap, same class as this hook's other quote-unaware
# limitations, that would need real shell tokenization to close fully.
split_segments() {
  local s="$1"
  local i=0 len=${#s} c c2
  local in_single=0 in_double=0
  local buf=""
  while (( i < len )); do
    c="${s:i:1}"
    if [[ "$in_single" -eq 0 && "$in_double" -eq 0 ]]; then
      c2="${s:i:2}"
      if [[ "$c2" == "&&" || "$c2" == "||" ]]; then
        printf '%s\n' "$buf"
        buf=""
        i=$((i + 2))
        continue
      elif [[ "$c" == ";" || "$c" == "|" ]]; then
        printf '%s\n' "$buf"
        buf=""
        i=$((i + 1))
        continue
      fi
    fi
    if [[ "$in_double" -eq 0 && "$c" == "'" ]]; then
      in_single=$((1 - in_single))
    elif [[ "$in_single" -eq 0 && "$c" == '"' ]]; then
      in_double=$((1 - in_double))
    fi
    buf+="$c"
    i=$((i + 1))
  done
  printf '%s\n' "$buf"
}

while IFS= read -r segment; do
  [[ -z "$segment" ]] && continue
  if ! check_segment "$segment"; then
    exit 2
  fi
done < <(split_segments "$COMMAND")

exit 0
