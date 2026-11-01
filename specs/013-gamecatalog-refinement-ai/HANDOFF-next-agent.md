# Handoff: take over JordyLab (2026-11-01, for a fresh agent — Claude Code)

Written at the end of an OpenCode session that started from the previous `HANDOFF-next-agent.md` (now in git
history). Read this first, then `docs/testing/bug-log.md` (BUG-075 to BUG-077) and
`specs/013-gamecatalog-refinement-ai/validation-results.md` for the detailed records.

## UPDATE 2026-11-01 14:50 UTC: VPS recovered, rc24 deployed, DNS still broken

- The owner rebooted the VPS from the OVH panel; k3s, Tailscale and CloudNativePG came back by themselves, the
  database survived (no restore). `deploy-prod.yml` for `v0.0.1-rc24` then ran green; backend, frontend and keycloak
  are on rc24.
- **`jordylab.be` does not resolve for anyone** (SERVFAIL on 1.1.1.1/8.8.8.8/9.9.9.9). The OVH zone's DNSSEC
  signatures expired on 2026-10-29. The owner toggled DNSSEC on in the OVH console, OVH re-signed the zone with new
  keys (KSK tag 12932, signatures valid to 2026-12-01), but the **DS record at the registry still holds the old key
  (tag 16339)** until OVH pushes the update (the console said up to 24 h). Check:
  `dig jordylab.be DS @a.nsset.be +short` — when it shows `12932`, `dig jordylab.be A @1.1.1.1 +short` should print
  `57.129.163.110`. The owner can also replace/delete the DS record in the OVH console (security setting, so the
  agent does not touch it).
- Still to do once DNS works: verify login, library, a game page, Sources and one LibBot question; then the owner
  presses "Refresh game data" on Sources (open thread 2). `multiplayer_source` right after the deploy: STEAM 332,
  UNKNOWN 112, IGDB 65.

The rest of this section is the original outage record, kept for the diagnosis steps:

## (Resolved) production was down

The OVH VPS was **offline** — bigger than any code task:

- `tailscale status`: `jordylab-vps ... offline, last seen 2d ago` (~2026-10-30)
- `https://jordylab.be` does not answer (`curl` exit 000)
- `ssh ubuntu@57.129.163.110` times out (port 22 unreachable)
- The `v0.0.1-rc24` deploy failed at 2026-11-01 13:54 with `dial tcp 100.93.81.41:6443: i/o timeout` — the CI runner
  could not reach the k3s API because the server is gone, not because of the manifests.

Diagnosis (in order):

1. OVH control panel → the VPS: is it still running? Reboot it from there if it is not (SSH is impossible while it is
   down). Watch for a rescue-mode prompt or a suspended service (unpaid invoice, abuse).
2. When SSH is back: `sudo systemctl status k3s`, `uptime`, `df -h`, `journalctl -k | tail`, `sudo ufw status verbose`
   — the runbook `docs/runbook.md` §2, §3, §19 and the `jordylab-ops` skill cover hardening and k3s recovery.
3. Verify Tailscale (`tailscale status`, the node must show `active`), then `kubectl get nodes` from the Mac.
4. Redeploy the release once the cluster is healthy:
   `gh workflow run deploy-prod.yml --repo jordy-swinnen/JordyLab -f version=v0.0.1-rc24`, approve, watch rollout.
5. The database is safe even if the VPS is not: CloudNativePG ships base + WAL backups to OVH Object Storage
   (`jordylab-cnpg-backups`, runbook §15 restore drill, §16 full VPS rebuild). Do **not** rebuild before checking
   whether the local `local-path` volume survived — a simple reboot keeps everything.

## What shipped this session (all on `main`, deployed to prod)

| Tag | PR | What | State |
|-----|----|------|-------|
| `v0.0.1-rc20` | #134 (pre-session) | spec 013 release: pgvector `Database`, 3 Flyway migrations | deployed, verified |
| `v0.0.1-rc21` | #135 | BUG-075: `temperature` no longer sent to the Anthropic fallback (it 400s: "temperature is deprecated for this model") | deployed, verified |
| `v0.0.1-rc22` | #136 | BUG-076: `Language` wire values — the generated schema says `EN/NL/OTHER`, the parser accepted only `en/nl/other`; every LibBot question died on every model | deployed, verified |
| `v0.0.1-rc23` | #138 | BUG-077 part 1: `MultiplayerService.derivePending` had **no caller** (backlog never ran) and Steam games never got player counts; auto-fill + bulk "Refresh game data" now drive it, IGDB fills counts fill-only | deployed (verification pending) |
| `v0.0.1-rc24` | #139 | BUG-077 part 2: IGDB title matching (article order, edition suffixes) + `game_modes` flag fallback where `multiplayer_modes` rows don't exist | deployed 2026-11-01 (after the VPS came back); verification blocked on DNS |

LibBot on prod was verified working (rc22/rc23 era, admin browser session): English and Dutch answers with cited
games, party-size questions with derived constraints and the honest unknown-count note. BUG-075 and BUG-076 are
VERIFIED-PROD in the bug log.

## Open threads (priority order)

1. **Get `jordylab.be` resolving again** (DS record, see the update above), then verify: login, library, a game page,
   Sources, one LibBot question. The VPS and the rc24 deploy are done.
2. **BUG-077 verification**: after rc24, the derive backlog runs in every auto-fill sweep (bounded 100/run) and on the
   free bulk **Refresh game data** button on the Sources page (that one resets attempt counters, so parked games get
   another look). A full drain wants one bulk press — it needs the owner's admin session in the browser pane
   (`agent-browser open https://jordylab.be --headed`, owner types the password; the agent never does).
   Success = `multiplayer_source='UNKNOWN'` near zero, `max_local_players` filled wherever a source has counts, and
   LibBot party answers covering the library.
3. **The player-count source wall** — owner decision needed. Proven by live IGDB API probes: `multiplayer_modes` (the
   only count source) has **no rows** for many N64/GameCube/PS2 titles (`Mario Party 7`, `Super Mario 64` …), Steam
   categories carry no counts by design. `game_modes` (rc24) fills the flags but never counts. Options recorded at the
   end of `validation-results.md`: accept the honest "unknown" note | curated count field in the admin UI |
   ScreenScraper integration (`joueurs` for ROMs; new external source, needs an account) | deterministic extraction
   from description text. The spec's "deterministic only, never LLM-inferred" rule (and the 006 migration that nulled
   the earlier AI-guessed values) stands unless the owner explicitly changes it.
4. **Golden live tier at 74 %** (23/31, first ever live run, `./gradlew goldenLive`, owner allowed credits). The 8
   misses are live-model vs recorded-expectation wording, not faults. T072 tuning = re-record goldens, prompt tuning
   or a model change on Settings → AI Models — owner's call. `GoldenLiveTest` also has a description-quality tier that
   failed alongside.
5. **Owner-declined leftover tasks** (he said he is happy with the work and not doing them): T150 sign-off, the local
   HANDOFFs (real scans, fingerprint sign-ins), T154–T157 close-out. Do not chase them; say so in any final report.

## Rules and gotchas (from root `AGENTS.md`, `jordylab-ops`, and this session)

- **Never print secrets** (`jordylab-be/.env`, cluster Secrets, tokens). Never remove `jordylab-be_pgdata`. Never
  hand-seed the dev or prod database. Read-only on the dev stack.
- **Every commit needs a `Refs:` trailer** (`Refs: BUG-077`, `Refs: 013/T155`, …); hooks are enabled
  (`git config core.hooksPath .githooks`); never `--no-verify`. Commits land via PR (branch → CI → merge), never
  directly on `main`.
- **Cluster mutations need the owner's yes** (deploy approvals were delegated for this work; re-confirm for anything
  new). Production changes go through Git + the release pipeline (tag `vX.Y.Z[-rcN]` → Release → approved deploy).
- **Backend tests on this Mac**: `DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')`
  `TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test --tests '<pattern>'`. Known local-only flakes (pass in CI):
  `RoleMatrixTest`, `GuestChatLimitIntegrationTest`, sometimes `JordylabApplicationTests` (`localhost:2375` /
  `DockerClientProviderStrategy`). `goldenLive` and `igdbCheck` cost/may cost money — owner allowed credits for
  testing the LibBot/multiplayer feature; re-confirm before large runs.
- **Frontend**: `bunx nx run-many -t lint oxlint test` (Bun/Nx). **E2E**: `jordylab-fe/e2e/run.sh web`. Reviewer rules:
  no `var`, no Mockito `any*()`, blank line before `return`, `assertSoftly`, TestBuilder pattern.
- **`opencode.json` at the repo root has an uncommitted local modification** from before this session — leave it be.
- **Commit message style** (from git history): conventional subject, body with why + how verified, then
  `Refs: …` and `Co-Authored-By:` trailers.

## Key commands (cluster once it is back)

```bash
export KUBECONFIG=~/.kube/jordylab.yaml
kubectl -n jordylab get pods,cluster,database          # backend/frontend/keycloak on :v0.0.1-rc24 when done
kubectl -n jordylab logs deploy/backend --tail=100 | grep -i -E "flyway|auto-fill|multiplayer"
kubectl -n jordylab exec cnpg-cluster-1 -c postgres -- psql -U postgres -d jordylab -At \
  -c "select multiplayer_source, count(*) from gamecatalog.game group by 1 order by 2 desc"
gh workflow run deploy-prod.yml --repo jordy-swinnen/JordyLab -f version=v0.0.1-rc24   # then approve in Actions
```

Full ops detail lives in the `jordylab-ops` skill and `docs/runbook.md` (§11 rollback, §20 release, §22 backup +
pgvector).
