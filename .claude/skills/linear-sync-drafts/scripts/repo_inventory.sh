#!/bin/sh
# Read-only inventory of the work the repository holds: every numbered spec under specs/ and every draft under
# specs/_drafts/ that has no spec yet. Prints one JSON array (sorted by key) on stdout; diagnostics go to stderr.
# Exit 0 on success, 2 when specs/_drafts/ is missing. Never writes a file, never calls Linear.
# Contract: specs/015-linear-backlog/contracts/draft-inventory.md. Run from the repository root.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=${INVENTORY_ROOT:-.}
OVERRIDES=${INVENTORY_OVERRIDES:-$HERE/../overrides.tsv}
APPMAP=${INVENTORY_APPMAP:-$HERE/../app-map.tsv}

if [ ! -d "$ROOT/specs/_drafts" ]; then
  echo "repo_inventory: specs/_drafts not found under $ROOT" >&2
  exit 2
fi

# first heading with the "Feature Specification:" / "Feature Draft:" prefix removed, else the fallback
title_of() {
  t=$(grep -m1 '^# ' "$1" 2>/dev/null | sed -e 's/^# *//' -e 's/^Feature Specification: *//' -e 's/^Feature Draft: *//' || true)
  if [ -n "$t" ]; then printf '%s' "$t"; else printf '%s' "$2"; fi
}
status_of() { grep -m1 '^\*\*Status\*\*:' "$1" 2>/dev/null | sed 's/^\*\*Status\*\*: *//' || true; }
priority_of() {
  for p in High Medium Low; do
    if grep -q "Priority: $p" "$1" 2>/dev/null; then printf '%s' "$p"; return; fi
  done
}
map_field() { # name column(2=App, 3=Type)
  [ -f "$APPMAP" ] || return 0
  awk -F'\t' -v n="$1" -v c="$2" '$1==n {print $c; exit}' "$APPMAP"
}
# first prose paragraph (Overview section when there is one), flattened and cut at 500 characters
summary_of() {
  [ -f "$1" ] || return 0
  awk '
    function flush() {
      if (para != "" && prose && first == "") first = para
      if (para != "" && prose && inov && ov == "") ov = para
      para = ""; prose = 0; started = 0
    }
    /^## (Overview|Summary)/ { flush(); inov = 1; next }
    /^#/ { flush(); inov = 0; next }
    /^[ \t]*$/ { flush(); next }
    {
      if (!started) { started = 1; prose = ($0 !~ /^(\*\*|-|\||>|`|[0-9]+\.|\[|<|\*)/) }
      if (prose) para = (para == "" ? $0 : para " " $0)
    }
    END { flush(); out = (ov != "" ? ov : first); print substr(out, 1, 500) }
  ' "$1"
}
json_list() { # stdin lines -> JSON array of strings
  jq -R -s 'split("\n") | map(select(length > 0))'
}

# draft names mentioned (as `name` or _drafts/name) in a draft's own files, excluding itself
related_of() {
  dir=$1; self=$2; out=""
  for other in "$ROOT"/specs/_drafts/*/; do
    o=$(basename "$other"); [ "$o" = "$self" ] && continue
    if cat "$dir"/*.md 2>/dev/null | grep -q -e "\`$o\`" -e "_drafts/$o"; then out="$out$o
"; fi
  done
  printf '%s' "$out" | json_list
}

# certain spec folder for a draft: slug equality, then the override list. Prints the folder or nothing.
certain_spec() {
  name=$1
  for s in "$ROOT"/specs/[0-9][0-9][0-9]-*/; do
    base=$(basename "$s")
    if [ "${base#[0-9][0-9][0-9]-}" = "$name" ]; then echo "$base slug"; return; fi
  done
  num=""
  [ -f "$OVERRIDES" ] && num=$(awk -F'\t' -v n="$name" '$1==n {print $2; exit}' "$OVERRIDES")
  if [ -n "$num" ]; then
    for s in "$ROOT"/specs/"$num"-*/; do
      [ -d "$s" ] && { echo "$(basename "$s") override"; return; }
    done
  fi
}

# spec folders that merely mention the draft's path
mentioning_specs() {
  name=$1
  for s in "$ROOT"/specs/[0-9][0-9][0-9]-*/; do
    if cat "$s"*.md 2>/dev/null | grep -q "_drafts/$name"; then echo "specs/$(basename "$s")"; fi
  done
}

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
: > "$TMP/claimed"   # drafts that share a spec issue

# ---- specs
for s in "$ROOT"/specs/[0-9][0-9][0-9]-*/; do
  [ -d "$s" ] || continue
  base=$(basename "$s"); number=${base%%-*}; slug=${base#*-}
  tasks="$s/tasks.md"
  total=0; done_n=0
  if [ -f "$tasks" ]; then
    total=$(grep -cE '^- \[[ xX]\] T[0-9]+' "$tasks" || true)
    done_n=$(grep -cE '^- \[[xX]\] T[0-9]+' "$tasks" || true)
  fi
  open_n=$((total - done_n))
  open_json=$( { grep -E '^- \[ \] T[0-9]+' "$tasks" 2>/dev/null || true; } | sed -E 's/^- \[ \] (T[0-9]+) (.*)/\1	\2/' \
    | jq -R -s 'split("\n") | map(select(length > 0) | split("\t") | {id: .[0], text: (.[1:] | join(" ") | .[0:100])})')
  sline=$(status_of "$s/spec.md")
  if [ "$total" -gt 0 ] && [ "$open_n" -eq 0 ]; then state="Done"
  elif printf '%s' "$sline" | grep -qi '^implemented'; then state="Done"
  elif [ "$done_n" -gt 0 ]; then state="In Progress"
  elif [ "$total" -gt 0 ]; then state="Todo"
  else state="Specified"; fi
  candidate=false
  if [ "$state" = "In Progress" ] && [ "$open_n" -le 3 ]; then candidate=true; fi

  drafts=""; how="none"; draft_status=""; draft_priority=""; related="[]"
  for d in "$ROOT"/specs/_drafts/*/; do
    [ -d "$d" ] || continue
    dn=$(basename "$d"); hit=$(certain_spec "$dn")
    if [ "${hit%% *}" = "$base" ]; then
      printf '%s\n' "$dn" >> "$TMP/claimed"
      drafts="$drafts$dn
"
      how=${hit#* }
      [ -z "$draft_status" ] && draft_status=$(status_of "$d/spec-draft.md")
      [ -z "$draft_priority" ] && draft_priority=$(priority_of "$d/spec-draft.md")
    fi
  done
  draft_json=$(printf '%s' "$drafts" | sed '/^$/d; s#^#specs/_drafts/#' | json_list)
  app=$(map_field "$base" 2); [ -z "$app" ] && app=$(map_field "$slug" 2)
  typ=$(map_field "$base" 3); [ -z "$typ" ] && typ=$(map_field "$slug" 3)
  if [ -z "$app" ]; then for dn in $drafts; do app=$(map_field "$dn" 2); [ -n "$app" ] && break; done; fi
  if [ -z "$typ" ]; then for dn in $drafts; do typ=$(map_field "$dn" 3); [ -n "$typ" ] && break; done; fi
  summary=$(summary_of "$s/spec.md")
  if [ -z "$summary" ]; then for dn in $drafts; do summary=$(summary_of "$ROOT/specs/_drafts/$dn/spec-draft.md"); [ -n "$summary" ] && break; done; fi
  wording=${draft_status:-$sline}
  prio=$draft_priority; [ -z "$prio" ] && prio=$(priority_of "$s/spec.md")

  jq -n --arg kind spec --arg key "specs/$base" --arg name "$slug" --arg number "$number" \
    --arg title "$(title_of "$s/spec.md" "$slug")" --arg wording "$wording" --arg prio "$prio" \
    --arg how "$how" --arg state "$state" --arg app "$app" --arg typ "$typ" --arg summary "$summary" \
    --argjson drafts "$draft_json" --argjson open "$open_json" --argjson cand "$candidate" \
    --argjson done "$done_n" --argjson total "$total" '
    {kind: $kind, key: $key, name: $name, number: $number, title: $title, summary: $summary,
     statusWording: (if $wording == "" then null else $wording end),
     priority: (if $prio == "" then null else $prio end),
     draftFolders: $drafts, matchHow: $how,
     matchConfidence: (if ($drafts | length) > 0 then "certain" else "none" end),
     tasksDone: $done, tasksTotal: $total, openTasks: $open,
     suggestedState: $state, doneCandidate: $cand,
     suggestedApp: (if $app == "" then null else $app end),
     suggestedType: (if $typ == "" then null else $typ end),
     relatedDrafts: [], possibleSpecs: []}' >> "$TMP/items"
done

# ---- drafts without a certain spec
for d in "$ROOT"/specs/_drafts/*/; do
  [ -d "$d" ] || continue
  dn=$(basename "$d")
  if grep -qx "$dn" "$TMP/claimed"; then continue; fi
  possible=$(mentioning_specs "$dn" | json_list)
  conf=none; [ "$possible" != "[]" ] && conf=needs-decision
  app=$(map_field "$dn" 2); typ=$(map_field "$dn" 3)
  jq -n --arg key "specs/_drafts/$dn" --arg name "$dn" --arg title "$(title_of "$d/spec-draft.md" "$dn")" \
    --arg wording "$(status_of "$d/spec-draft.md")" --arg prio "$(priority_of "$d/spec-draft.md")" \
    --arg conf "$conf" --arg app "$app" --arg typ "$typ" --argjson possible "$possible" \
    --arg summary "$(summary_of "$d/spec-draft.md")" \
    --argjson related "$(related_of "$d" "$dn")" '
    {kind: "draft", key: $key, name: $name, number: null, title: $title, summary: $summary,
     statusWording: (if $wording == "" then null else $wording end),
     priority: (if $prio == "" then null else $prio end),
     draftFolders: [$key], matchHow: "none", matchConfidence: $conf,
     tasksDone: 0, tasksTotal: 0, openTasks: [], suggestedState: "Drafted", doneCandidate: false,
     suggestedApp: (if $app == "" then null else $app end),
     suggestedType: (if $typ == "" then null else $typ end),
     relatedDrafts: $related, possibleSpecs: $possible}' >> "$TMP/items"
done

jq -s 'sort_by(.key)' "$TMP/items"
