---
name: project-module-state-2026-08-01
description: Snapshot of which JordyLab modules are actually implemented vs scaffolding-only, as of 2026-08-01
metadata:
  type: project
---

As of 2026-08-01 (commit 30eec01, 9 commits total, repo created recently), only the `fna` module has real
implementation. `shared` has partial infra (AI wrapper, JPA/Jackson/RestClient config, BaseEntity).
`gamecatalog`, `recipe`, and `trading` do not exist anywhere in `jordylab-be` (no package, no Flyway schema).
`garmin-sync-service` contains zero Python source — only AGENTS.md/CLAUDE.md/LICENSE/.gitignore/.idea, no
requirements.txt, no src/, no tests/. jordylab-fe has only `apps/fna` + `libs/fna/{api,ui}` — no other domain
apps/libs exist.

Flyway migrations: only 3 files, all in the `finance` schema (V1, V2 old-style naming; V20260317 uses the new
`V<yyyyMMdd>__` convention documented in jordylab-be/AGENTS.md). No gamecatalog/garmin/recipe schema migrations
exist yet despite AGENTS.md's schema-ownership table listing them under jordylab-be.

**Why:** This is ground truth from directly listing directories and reading files, not from docs — root
AGENTS.md's module table is aspirational/target-state, not current-state.
**How to apply:** Treat this as a frozen-in-time snapshot. Before citing "module X doesn't exist" in future
conversations, re-verify with a fresh directory listing — this will go stale as the project progresses quickly
(the git log shows active daily work).
