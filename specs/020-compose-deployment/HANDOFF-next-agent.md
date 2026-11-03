# Handoff: production is live on Compose — T045 verification and the release-retag decision are next (2026-11-03, for the next agent)

Written at the end of an OpenCode session (GLM-5.2). Read this first, then [tasks.md](tasks.md) and [quickstart.md](quickstart.md) — every claim below is recorded there with timestamps.

## Where things stand

- **The cutover is done.** The maintenance window ran 2026-11-03 20:09–20:45 UTC (~36 minutes of downtime; the abort deadline of start + 2.5 h was never approached). `https://jordylab.be` serves from the Compose stack on the VPS: all six services healthy, a fresh Let's Encrypt certificate issued by Caddy itself (expires 2027-02-01), the owner signed in and reached their own data. Bonus proof: the owner's SSO cookie survived the cutover (restored Keycloak keys/sessions), so the sign-in wrote no new rows.
- **k3s is gone from the data path**: stopped, disabled, and fully cleaned with `k3s-killall.sh` (stopping the service alone was NOT enough — BUG-083). Its data under `/var/lib/rancher` is intact for the R14 rebuild path. `AUTO_DEPLOY` is `false` (paused at cutover, with the owner's yes) — a green main build no longer deploys anything until T062 rewires the pipeline to Compose.
- **Backups are live**: first full backup `20261103-203343F` in S3 (`/pgbackrest` prefix in `jordylab-cnpg-backups`, next to the untouched old Barman chain), WAL archiving on, both systemd timers enabled (differential Mon–Sat 03:00 UTC, full Sun 04:00). The point-in-time restore is **proven**: a restore to 2026-11-03 20:38 UTC returned `finance.article` at exactly its value at that time (823 vs 822 in the pre-window baseline).
- **The live release** is `JORDYLAB_RELEASE=sha-9cc83c4f636f6e6a309cf571d9402619ed413c18` (the PR #199 merge — the same code that was live on k3s). `/opt/jordylab/state/current-release` is **empty** until the first `deploy-release.sh` run (T049) — the window brought the stack up directly.
- **Bugs found and fixed inside the window** (all logged in `docs/testing/bug-log.md` with root causes, all verified on prod): BUG-081 (Postgres base image pinned to the arm64 manifest — now the multi-arch list digest), BUG-082 (Caddy healthcheck probed `localhost`→::1 — now 127.0.0.1), BUG-083 (stopping the k3s service left its iptables hostPort DNAT + shims serving the public ports — `k3s-killall.sh` is the real cleanup), BUG-084 (the Postgres image shipped no CA bundle, so S3 TLS verification failed — `ca-certificates` added).
- **Branch** `jordyswinnen/jor-31-compose-cutover-continue` carries the window records and fixes; this handover rides in its PR. PR #199 (Phase A + the deploy workflow + the T013 secrets key) is already merged.
- Done and verified this session: T013, T014, T017, T019, T020, T022 (accepted by the owner), T023 (both halves), T024, T025, T026–T039 (the whole window), T040–T044, T047, T048, T055. HANDOFF-40 and HANDOFF-41 are both resolved.

## Next steps (priority order)

1. **T045** (after 03:00 UTC tonight): `systemctl list-timers 'jordylab-backup-*'` and `pgbackrest --stanza=jordylab info` — the differential must appear on schedule (quickstart step 12). Run on the host as ubuntu; the timers run as root via `docker compose exec`.
2. **T046 (owner decision)**: keep or delete the encrypted Mac copies (`~/jordylab-backups/prod-2026-11-02/` and `~/jordylab-backups/cutover/`) once T045 passes.
3. **The retag decision (owner, blocks T049/T050/T052)**: `deploy-prod-compose.yml` deploys release tags (`vX.Y.Z`) whose images must exist in GHCR — and **nothing creates new v-tag images any more** (spec 019 removed `release.yml`; existing `v0.0.1-rcN` images are from the old pipeline). Options: a retag step inside the compose workflow, a small release workflow that builds/pushes tag images, or deploying by sha instead of tag (would need `deploy-release.sh`'s pattern widened). Recorded on the T047/T049 task lines.
4. **T049 (owner approval)**: the first `deploy-prod-compose.yml` run by hand, after the retag decision; it also writes the state files (currently empty).
5. **Phase 6 (US4)**: T053 (`ufw status verbose` + `docker ps` ports), T054 (post-cutover external scan — rerun the two commands of record in quickstart step 10; expect only 22/80/443, the NodePorts 31460/32531 must be GONE — that closes BUG-078), T056 (**owner**: close public SSH, only after T055), T057.
6. **T058+ (US5)**: rewrite `docs/runbook.md` for Compose (it still describes k3s), update the `jordylab-ops` skill the same way (only its Tailscale section is current so far), T063 (forced cert renewal test), T064 (reboot test), T065 (memory after a 30-minute soak vs the T019 baseline), and T062 (rewire the deploy pipeline — note the spec's `release.yml` references are stale; the real pipeline is `build.yml` → `deploy-prod.yml`).

## Waiting on the owner

- **T046** — keep or delete the encrypted Mac copies (after T045).
- **The retag decision** — see next step 3; everything in Phase 5 (T049–T052) waits on it.
- **T056** — closing public SSH (after T054/T055).

## Decisions and approvals from this session (2026-11-03)

- Window tonight with abort at start + 2.5 h; T026 (site down) and T031 (stop k3s) approved in-flow; `AUTO_DEPLOY` paused with an explicit yes; T043 (timers) approved.
- T022 accepted without a row-by-row re-run (no table lost data; the 8 Keycloak extra-row tables match post-restore-point deletions).
- T037's artwork check **changed**: production's artwork volume is empty, so the smoke check verifies the volume migration with whatever it contains, and artwork loading is verified after the first real scan on the new stack (never seeded — Validation Data rule).
- T047 design decisions: the Postgres image is **built on the host** (not pushed to GHCR), the workflow input is named `target` (T049's command updated), PyYAML installs into a venv on the runner.
- PR #199 opened and merged with the owner's yes; the T013 secrets commit (HANDOFF-40) approved and merged through it; the redundant uncommitted copy in the Claude worktree was reverted after a byte-identity check.

## Tried and rejected / lessons

- **Never scan this host fast**: a 2000 pps raw SYN scan is silently mitigated upstream (OVH) and reports *all* 65535 ports filtered while the site keeps serving 200s. Unprivileged full-range connect scans crawl for hours against the drop-everything firewall, and `-T5` aborts at its 900 s host timeout. The commands of record (quickstart step 10) are `sudo nmap -Pn -p- -T4 --max-rate 200` plus the NodePort-range connect sweep at `--max-rate 50`.
- **`pkill -f containerd-shim-runc-v2` is too broad** — dockerd uses the same shim binary; it briefly killed the Compose containers (healed by `restart: unless-stopped`). Select k3s shims by their `-namespace k8s.io` argument.
- **PITR to a minutes-old target** needs `SELECT pg_switch_wal();` on production first (a quiet database does not fill its 16 MB WAL segment), and the throwaway restore container needs the S3 keys at replay time (`archive-get`).
- Stopping a systemd unit is not the same as cleaning it: check iptables (`PREROUTING` for `KUBE-`/`CNI-` chains) after any k3s teardown.

## Gotchas and commands

- The stack: `sudo -u jordylab-deploy env JORDYLAB_RELEASE=<tag> docker compose --env-file /opt/jordylab/env/prod.env -f /opt/jordylab/compose/compose.yaml …` on the host (ssh `ubuntu@100.93.81.41`, tailnet SSH works key-only; passwordless sudo for ubuntu; `jordylab-deploy` has no sudo but is in the docker group).
- `deploy/compose` is mirrored to `/opt/jordylab/compose` (rsync as ubuntu via `--rsync-path='sudo rsync'`, then `chown -R jordylab-deploy:jordylab-deploy`); the deploy workflow does this as the deploy user in normal runs.
- The Mac's pip is broken (pip 26.2.2 truststore bug against this macOS version string) — for `render-env.sh` use the surviving venv: `/private/tmp/claude-501/-Users-jordy-Projects-JordyLab--claude-worktrees-zen-mendel-076554/3b71c84d-b254-40ba-86ab-b7b62862e54b/scratchpad/render-test/venv` (prepend its `bin` to PATH; it has PyYAML 6.0.3). If that temp dir is gone, run the render inside a Linux container instead.
- The row-count sweep used all evening (save as a file, pipe to `psql -f -`, keep only lines matching `\| *[0-9]+ *$`, sort, diff):
  ```sql
  \t on
  SELECT format('SELECT %L, count(*) FROM %I.%I;', table_schema||'.'||table_name, table_schema, table_name)
  FROM information_schema.tables
  WHERE table_schema NOT IN ('pg_catalog','information_schema') AND table_type='BASE TABLE'
  ORDER BY 1;
  \gexec
  ```
- The encrypted backups decrypt with the SOPS age key pair (`age -d -i "$HOME/Library/Application Support/sops/age/keys.txt"`); the recipient in `recipient.txt` is that key's public half (verified by `age-keygen -y`).
- The kubeconfig (`~/.kube/jordylab.yaml`) is useless now (k3s is dead) except for the R14 rebuild path.
- Not ours, leave alone: PR #191 (JOR-75) merged tonight by another session; a Build for its merge ran before ours.
