#!/usr/bin/env python3
"""
Standalone reimplementation of OpenCode's permission resolution (verified
against `opencode debug agent <name>` and the actual findLast-based merge
algorithm extracted from the compiled binary during this guardrail work),
so opencode.json's declarative rules can be regression-tested without
needing the opencode CLI installed in CI.

This deliberately does NOT assert byte-for-byte equivalence with
pre-bash-guard.sh's regex coverage — trying to keep two independently
written pattern languages identical is exactly the kind of enumeration
drift a generic "ask" fallback is meant to avoid. Instead it asserts the
actual safety invariant: every command the bash hook treats as
destructive must resolve to "deny" or "ask" here too, never a bare
"allow", even for flag/spelling variants that aren't explicitly
enumerated as glob patterns.

Known fidelity gap: Python's fnmatch does not give "**/" true zero-or-more-
directories semantics (unlike OpenCode's real glob engine) — "**/x.json"
won't match a bare "x.json" with no directory prefix here, even though it
should for a real "**/" glob. Test inputs below deliberately use a realistic
directory-qualified path for every "**/..." pattern to sidestep this rather
than assert something this reimplementation can't actually verify.

Run standalone: python3 .claude/hooks/tests/opencode_permission_cases.py
"""
from __future__ import annotations

import fnmatch
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
CONFIG = json.loads((REPO_ROOT / "opencode.json").read_text())


def merged(kind: str, agent: str) -> list[tuple[str, str]]:
    base = CONFIG["permission"].get(kind, {})
    override = CONFIG.get("agent", {}).get(agent, {}).get("permission", {}).get(kind, {})
    # Plain dict update: a key present in both keeps base's position but
    # takes override's value; a key only in override is appended at the
    # end. This mirrors OpenCode's own merge order exactly (confirmed via
    # `opencode debug agent`), which is what makes the ordering below
    # (catch-all first, specific denies last) load-bearing.
    merged_map = dict(base)
    merged_map.update(override)
    return list(merged_map.items())


def resolve(kind: str, agent: str, value: str) -> str:
    result = "ask"  # OpenCode's own fallback when nothing matches
    for pattern, action in merged(kind, agent):
        if fnmatch.fnmatchcase(value, pattern):
            result = action
    return result


pass_count = 0
fail_count = 0


def check(agent: str, kind: str, value: str, expected_one_of: set[str]) -> None:
    global pass_count, fail_count
    actual = resolve(kind, agent, value)
    ok = actual in expected_one_of
    status = "ok  " if ok else "FAIL"
    print(f"  {status} [{agent:9s}/{kind}] {value!r:55s} -> {actual:6s} (expected one of {sorted(expected_one_of)})")
    if ok:
        pass_count += 1
    else:
        fail_count += 1


print("--- explicit deny patterns must still resolve to deny (regression check) ---")
for agent in ("sdd-build", "sdd-plan"):
    check(agent, "bash", "rm -rf /", {"deny"})
    check(agent, "bash", "rm -rf ~", {"deny"})
    check(agent, "bash", "git reset --hard", {"deny"})
    check(agent, "bash", "git push --force", {"deny"})
    check(agent, "bash", "DROP TABLE users", {"deny"})
    check(agent, "bash", "docker compose down -v", {"deny"})
    check(agent, "bash", "docker-compose down -v", {"deny"})
    check(agent, "bash", "git clean -f", {"deny"})
    check(agent, "bash", "git clean -fdx", {"deny"})
    check(agent, "bash", "git clean --force", {"deny"})

print("--- safety invariant: dangerous commands never resolve to bare allow, even unenumerated ---")
for agent in ("sdd-build", "sdd-plan"):
    check(agent, "bash", "rm -r /", {"deny", "ask"})
    check(agent, "bash", "rm -rf ..", {"deny", "ask"})
    check(agent, "bash", "rm --recursive --force /home/jordy", {"deny", "ask"})
    check(agent, "bash", "git push origin --force", {"deny", "ask"})

print("--- safe commands must not be caught by the guardrails ---")
check("sdd-build", "bash", "git status", {"allow"})
check("sdd-build", "bash", "rm -rf build/", {"ask", "allow"})
check("sdd-build", "bash", "docker compose down", {"allow"})
check("sdd-plan", "bash", "git status", {"allow"})
check("sdd-plan", "bash", ".specify/scripts/bash/create-new-feature.sh foo", {"allow"})
# git clean -n is an explicit dry run; the force-flag denylist must not treat
# the "f" in an unrelated argument (e.g. an --exclude filename) as the flag.
check("sdd-build", "bash", "git clean -n --exclude=foo.txt", {"allow"})
check("sdd-build", "bash", "git clean -n", {"allow"})

print("--- edit permissions: migration scaffolding must not be hard-blocked (glob can't express pre-edit-protect.sh's file-exists check) ---")
check("sdd-build", "edit", "jordylab-be/src/main/resources/db/migration/V10__add_column.sql", {"ask"})
check("sdd-plan", "edit", "specs/001-foo/spec.md", {"allow"})
check("sdd-plan", "edit", "src/main/java/Foo.java", {"deny"})
check("sdd-build", "edit", "src/main/java/Foo.java", {"allow"})
check("sdd-build", "edit", "jordylab-fe/package-lock.json", {"deny"})

print()
print(f"{pass_count} passed, {fail_count} failed")
sys.exit(1 if fail_count else 0)
