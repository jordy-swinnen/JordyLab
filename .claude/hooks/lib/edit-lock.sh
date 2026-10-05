#!/usr/bin/env bash
# Per-file lock shared by the post-edit hooks (spec 012 FR-017). Claude Code runs all hooks of one event in parallel, so the
# formatter (rewrites the file) and the lint check (reads it) could overlap; the formatter holds the lock while it writes and
# the lint check waits for it. mkdir is atomic on macOS and Linux. A lock older than 30 s is treated as stale.
#   edit_lock_acquire <file> <max_wait_seconds>   returns 0 when taken, 1 on timeout
#   edit_lock_release                              no-op when nothing was taken

EDIT_LOCK_DIRECTORY=""

edit_lock_path() {
  local key
  key="$(printf '%s' "$1" | cksum | cut -d' ' -f1)"
  echo "${TMPDIR:-/tmp}/jordylab-edit-hooks/$key.lock"
}

edit_lock_acquire() {
  local file="$1" max_wait_seconds="${2:-1}" lock_path waited_tenths=0 limit_tenths
  lock_path="$(edit_lock_path "$file")"
  limit_tenths=$(( max_wait_seconds * 10 ))
  mkdir -p "$(dirname "$lock_path")" 2>/dev/null
  while ! mkdir "$lock_path" 2>/dev/null; do
    if [[ -n "$(find "$lock_path" -maxdepth 0 -mmin +0.5 2>/dev/null)" ]]; then
      rmdir "$lock_path" 2>/dev/null
      continue
    fi
    (( waited_tenths >= limit_tenths )) && return 1
    sleep 0.1
    waited_tenths=$(( waited_tenths + 1 ))
  done
  EDIT_LOCK_DIRECTORY="$lock_path"
  return 0
}

edit_lock_release() {
  if [[ -n "$EDIT_LOCK_DIRECTORY" ]]; then
    rmdir "$EDIT_LOCK_DIRECTORY" 2>/dev/null
    EDIT_LOCK_DIRECTORY=""
  fi
}
