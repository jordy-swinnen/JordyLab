# Quickstart: validating spec 013

A runnable validation guide, in the order the owner set: **A. everything works locally → release → B. re-test on production (jordylab.be)**.
Contracts: [contracts/](./contracts/). Entities: [data-model.md](./data-model.md). Design reference: [ui-design.md](./ui-design.md).

Rules that apply throughout (root `AGENTS.md`): data comes only from the real scanner and the app; never insert rows by hand; never print secrets or
tokens; the `jordylab-be-*` dev containers are read-only for agents (no stop/remove/recreate without the owner's yes).

## A. Local

### A0. Prerequisites

```bash
cd jordylab-be && podman compose up -d             # dev Postgres (pgvector image) + Keycloak, if not already running (check with `podman ps`)
./gradlew bootRun                                   # backend on :8080 (profile local)
cd ../jordylab-fe && bunx nx serve jordylab         # web on :4200
```

- `OPENROUTER_API_KEY`, `ANTHROPIC_API_KEY`, `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` in the local `.env` (check by behaviour, never print them).
- Sign in as the dev admin; create a guest through Keycloak sign-up and approve it (Settings → Users) for the guest checks.
- Real data: run the downloaded scan client on the Mac (Steam) and on the Linux box (Steam + EmuDeck) against the local backend. If a scan cannot run, **stop and
  report**; do not seed.

### A1. Automated gates (each must be green before manual checks)

```bash
cd jordylab-be
./gradlew test --tests '*ModularityTests' --tests '*RoleMatrixTest'
./gradlew test --tests '*ResilientAiServiceTest' --tests '*AiGatewayWiringTest' --tests '*AiPropertiesTest'
./gradlew test                                      # gamecatalog + settings + shared
./gradlew goldenLive                                # NEW (Phase 4): AI golden set against the real models (costs a few cents), see below
cd ../jordylab-fe
bunx nx run-many -t lint oxlint test
E2E_SKIP_BACKEND_BUILD=0 e2e/run.sh web             # Playwright journeys incl. axe and the 360/390/430 overflow check
```

`goldenLive` prints pass rate per category and token cost. Gate: ≥ 90 % overall, 100 % on "out-of-scope → zero references" and "never offers a game that fails
a requirement". The replay tier runs inside `./gradlew test`.

### A2. Migration rehearsal (before the first local run on real data)

```bash
cd jordylab-be
./gradlew bootRun --args='--jordylab.migration.dry-run=true'   # NEW (Phase 0): runs migration 2 in a rolled-back transaction, prints the merge plan
```

Read the plan: every merge lists survivor id and merged ids by title. Expect only same-title games that had been split by platform. Then run for real.

### A3. User-story walk-through (tick each in the validation results)

| # | Story | Steps and expected result |
|---|---|---|
| 1 | LibBot answers | Ask "which games can I play with four people", "what is the best racing game I have installed", "why is my cat black", "how many are like this" (new conversation). Expect: grounded answer with ≤ 10 references all named in the text; cat question has **zero** references and a polite decline; the vague one gets one clarifying question; confirmed vs unknown separated with a count |
| 2 | Situations | "There are 6 people here today for game night" → applied chips `6+ local players`, `local multiplayer`; "just me tonight" → single-player; "online with friends" → online; "what can I play on <host display name>" → that host; Dutch: "er zijn vandaag 6 mensen voor spelavond" → same constraints, Dutch answer |
| 3 | Memory | "two people here, what should we play" → "which of those are installed" → "how many other games like the first one"; then **New conversation** → the follow-up is no longer understood. Second browser (guest): cannot see the first conversation. Reload: conversation empty |
| 4 | Data completes itself | After a real scan, wait ≤ 10 min without pressing anything: ≥ 90 % of new games have cover, facts, description. Sources page health counts shown. `SELECT`-free check: library grid shows the covers |
| 5 | One game, many places | Same title on Steam, an emulator host and a console → one card; detail lists all places; remove each in turn; disable a source and re-enable; previously duplicated titles are merged |
| 6 | Consoles | Add console "Nintendo Switch" via autocomplete (type "nin"); add a game by search, one by plain title, one by pasted list; add a custom console name; "Add game" disabled with zero consoles; remove a console (dialog shows counts); the Switch entry is gone from Scan sources; guest cannot reach Consoles |
| 7 | Filters | Nothing active → search + Filters only (the default `Installed` chip is shown); choose platform, status, players; chips appear with ×, Clear all, live count; reload and Back keep them; at 360 px the panel is a bottom sheet; keyboard: Tab/Esc; empty result suggests what to relax |
| 8 | Host display name | Name `cachyos-htpc` "Living room PC": grid chips, detail, filters, Sources (hostname as secondary line), LibBot answers all use it; clear it → hostname returns; scan again → name unchanged; duplicate name → refused |
| 9 | Labels and colours | Steam (Owned), Steam (Family), Emulated, Console; platform chips in brand colours; Installed teal ✓ vs Not installed amber dashed; axe journey green |
| 10 | Marks | As admin and guest: pick Want to play, then Played & liked (replaces), pick again (clears); totals identical for both; sort by most wanted; LibBot "which of my want-to-play games fit four players" and "what does everyone want to play"; a game with 5 votes outranks an equal fit; a requirement-failing popular game is not offered; remove the guest in Settings → their votes leave the totals |
| 11 | Refresh runs | Sources → Refresh game data (progress, summary); Regenerate AI data (cost dialog with the game count; only starts after confirm; Stop works; second start refused); "Refresh pending data" is gone; guest sees neither |
| 12 | Labels/nav | Nav reads "LibBot" (sparkle) and "Consoles"; Sources lists scan sources only; disabling a source shows the hide-impact counts first |
| 13 | ROM status | Mark one emulated copy Broken on one machine, Validated on another: card shows the summary, detail lists both; Steam-only game offers no control (API returns 409); LibBot does not offer a broken copy as playable on that machine; filter by ROM status; status survives a rescan |
| 14 | Mobile | Phone (or emulator) at 360/390/430: every page has no sideways scroll (Playwright proves it); fingerprint sign-in **20 times** with no red message at any moment (manual, SC-018) |
| 15 | Game page | Write a description with a fixed model and one with the router default: About reads the answering model (and "picked by <router>"); a Steam-only description reads "description from Steam"; read 20 AI-written descriptions beside 20 Steam ones (SC-022, at least 18 as good); an old description reads "written by AI"; the Spec sheet shows the facts source; at 360 px the cover overlaps the banner like desktop (compare with the owner's screenshot) |

Record results (pass/fail, evidence, screenshot) in `specs/013-gamecatalog-refinement-ai/validation-results.md`.

### A4. Gate to release

Every row of A3 green, A1 green, golden set ≥ 90 %, SC-001 and SC-007 measured locally. If any row fails, fix and re-run; **no partial release**.

## B. Release, then production (jordylab.be)

### B0. Before the release (cluster change, needs the owner's yes)

1. On-demand backup of the CNPG cluster (a `Backup` resource using the Barman Cloud plugin). `docs/runbook.md` §15 covers restores but has no on-demand backup
   step yet; a task adds it, and the backup is confirmed `completed` before going on.
2. Apply the CNPG `Database` resource that enables the `vector` extension (**approved by the owner 2026-10-07**) through the deploy pipeline; verify that `vector` is listed:
   `kubectl -n jordylab exec cnpg-cluster-1 -c postgres -- psql -d jordylab -c '\dx'`. Rehearse first on the restore-drill cluster (`deploy/k8s/drills`, runbook §15).
3. PR → CI green (`e2e-web`, tests, lint, commit references with `Refs: 013 …`) → merge → tag release candidate → approve the deploy (`jordylab-ops` skill).

### B1. After the deploy

```bash
kubectl -n jordylab rollout status deploy/backend      # healthy (cluster access is over Tailscale; use the jordylab-ops skill)
kubectl -n jordylab logs deploy/backend | grep -i "flyway\|merge"   # migrations applied, merge log reviewed (titles only, no secrets)
```

- Migration log shows the merges; library count equals or is below the pre-release count only by merged duplicates.
- Run the real scan client on the Mac and the Linux box against **jordylab.be**; do not seed.

### B2. Production acceptance (same table as A3, on https://jordylab.be)

Repeat A3 rows 1 to 15 as admin and as the guest account. Additionally measure on the real library:

| Measure | How | Target |
|---|---|---|
| SC-001 covers | Sources → health: `1 - gamesWithoutCover/totalGames` | ≥ 90 % |
| SC-007 auto-fill | after a scan, no clicks, 10 minutes | ≥ 90 % of new games complete |
| SC-006 LibBot latency | 20 questions, note stage and completion times | 95 % ≤ 10 s |
| SC-018 fingerprint | 20 sign-ins on the real phone with the release APK | 0 error messages |
| SC-017 retry | a game that lacked a cover during an IGDB outage gets it within 24 h | yes |

Record in `validation-results.md` with the release tag. Findings go to `docs/testing/bug-log.md` first, then get fixed (commit rule: log first).
