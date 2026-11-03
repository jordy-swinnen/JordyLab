# Handoff: Compose cutover prep, waiting on a Tailscale policy change (2026-11-03, for the next agent)

Written at the end of a Claude Code session (Haiku 5.5). Read this first, then [tasks.md](tasks.md) and [quickstart.md](quickstart.md), the two records this file updates.

## Where things stand

- The branch `jordyswinnen/jor-31-compose-local-stack` is merged as PR #194 (merge commit `67f5247`). Main has `deploy/compose/` (stack, Caddyfile, environment names, backup timers, `render-env.sh`, `deploy-release.sh`) and the backend artwork fix. This handover is committed on the local branch `jordyswinnen/jor-31-compose-local-stack`. That branch is merged, and this commit is not pushed.
- Production: k3s serves jordylab.be (homepage and OpenID discovery return 200). The Build for `67f5247` deployed `sha-67f5247` to k3s automatically, because `AUTO_DEPLOY` is `true`. Docker 29.9.2 and Compose 5.7.0 are installed on the host with no containers. k3s pods are unchanged.
- Done and verified: T003–T012 (on main), T015 (deploy user `jordylab-deploy`, its key stored as `DEPLOY_SSH_KEY`, login tested), T016 (Docker), T018 (`/opt/jordylab/{env,state,staging}`), T021 (artwork copy, checksums match; the source volume is empty, 0 files).
- Run, not closed: T022. The drill to 2026-11-02 12:00 UTC became ready and was deleted with its PVC. Eight Keycloak tables have more rows in the drill than in production (details in the quickstart log). The owner decides whether that passes.
- Edited, not committed: T013. `deploy/k8s/overlays/prod/secrets.sops.yaml` has the `POSTGRES_SUPERUSER_PASSWORD` key (the owner edited it in IntelliJ). The decrypt check passes and the key name is present. The file is NOT in the handover commit. Committing it to main triggers a Build and an automatic production deploy, so the owner decides when.
- Not done: T014, T017, T019, T020, and everything from T023 on.
- Not verified: the Compose stack has never run. The health checks have never run. The systemd timer units have not been checked on the host (T020, after T043 installs them). `render-env.sh` has only been run on a synthetic file; it has not been run against the real file with `SOPS_AGE_KEY` set. The artwork smoke check in T037 cannot pass as written (see decisions).

## Next steps (priority order)

1. **T014 (blocked, owner).** Add `tcp:22` to the Tailscale grant from `tag:ci` to `tag:jordylab-vps`, keeping `tcp:6443`. The save is refused by a policy test: `address "tag:jordylab-vps:22" (protocol "tcp"): want: Drop, got: Accept`. The `tests` section expects `tag:ci` to be dropped on port 22. Changing that test is a policy change only the owner can make. An agent does not edit the Tailscale policy. See HANDOFF-41.
2. **T023 (after T014).** Tailnet SSH to the host as `jordylab-deploy` from the Mac, and from a CI test job (it needs `DEPLOY_SSH_KEY`). Check the tailnet address with `tailscale status`.
3. **T017 and BUG-078.** Run the pre-cutover external scan, `nmap -Pn -p- 57.129.163.110`, from outside the tailnet. `nmap` is not installed on the Mac. Expect 22, 80 and 443, plus the two extra public ports 31460 and 32531 (BUG-078, OPEN). Record it in quickstart step 10.
4. **T019 (memory before the window).** `kubectl -n jordylab top pods` and `free -m` on the host. Record in quickstart step 13.
5. **T020.** `systemd-analyze calendar` for both schedules on the host. The timers are not installed until T043.
6. **T047.** Write `.github/workflows/deploy-prod-compose.yml`, which does not exist yet. It must:
   - install PyYAML on the runner, because `render-env.sh` needs it (verified locally in a venv);
   - copy `deploy/compose` to `/opt/jordylab/compose` on the host, which `deploy-release.sh` expects;
   - decide whether the Postgres image is built on the host from that copy or pushed to GHCR;
   - connect over SSH as `jordylab-deploy` with `DEPLOY_SSH_KEY`.
7. Cutover from T025 on. Each approval-gated step needs the owner's yes. See [tasks.md](tasks.md).
8. T058 to T061 (runbook, Postgres upgrade procedure, `jordylab-ops` skill, devops agents) once the stack runs.

## Waiting on the owner

- **HANDOFF-41 (T014).** The Tailscale access policy needs two changes: add `tcp:22` to the `tag:ci` → `tag:jordylab-vps` grant, and change the policy test for `tag:jordylab-vps:22` from Drop to Accept. The owner tried in the console and could not edit or delete the rule. Check first: the owner's role in the Tailscale admin console (Settings → Users; editing the policy needs Owner or Admin), and whether Access controls shows a lock or a "managed by" note.
- **HANDOFF-40 (T013).** Commit the secrets file when you decide. Then tick T013. Do not commit it without the owner's decision, because it goes to production on the next main build.
- **Decision: T022 row counts.** Accept the drill with the eight Keycloak differences, or investigate them row by row. Likely cause, not confirmed: two clients (`jordylab-mobile`, `mobile-release-ci`) and one user existed at the restore point and do not exist now.
- **Decision: T037 artwork check.** The k3s artwork volume is empty. Either generate real artwork with the real scanner, as the Validation Data rule requires, or change the check. Do not seed rows.
- **Decision: keep or delete the encrypted Mac copies (T046).** Later, after the stack is proven.

## Decisions and approvals from this session

- Approved by the owner on 2026-11-03, scoped to these steps, all now done or blocked as noted: T013 (owner did it), T014 (grant only, blocked), T015, T016, T018, T021, T022 (drill run and delete, done).
- The owner approved merging PR #194. It is merged.
- `AUTO_DEPLOY` is `true` and was not paused, so a green build on main deploys to k3s automatically (spec 019).
- The deploy user `jordylab-deploy` is in the `docker` group. That is root-equivalent on the host. Accepted as the cost of the deploy path; revisit if the owner objects.

## Tried and rejected

- `handle_path /ntfy /ntfy/*` in Caddy: invalid, because `handle_path` takes one pattern. Fixed with a named matcher and `uri strip_prefix`.
- `sops -d` on one document of the multi-document secrets file: MAC mismatch. Fixed by decrypting the whole file and splitting it in Python.
- `sops -d --output-type json` on the whole file: only the first document comes out.
- A policy change that keeps the drop test for port 22: the save is refused.

## Gotchas and commands

- Artwork volume path: `kubectl -n jordylab get pvc gamecatalog-artwork -o jsonpath='{.spec.volumeName}'`, then `/var/lib/rancher/k3s/storage/<pv>_jordylab_gamecatalog-artwork` on the host. It is root-owned, so use sudo.
- Row counts: `query_to_xml` over `information_schema.tables` in `jordylab` and `keycloak`, run as `postgres` in container `postgres` of `cnpg-cluster-1` and of the drill pod.
- After a drill: the cluster restarts once after recovery. Wait with `kubectl -n jordylab wait pod -l cnpg.io/cluster=<name>,cnpg.io/instanceRole=primary --for=condition=Ready`.
- Compose validation needs no daemon: `docker compose --env-file deploy/compose/env/prod.env.example -f deploy/compose/compose.yaml config -q`. The three `JORDYLAB_RELEASE` warnings are expected.
- Caddy validation in a container: `podman run --rm -v "$PWD/deploy/compose/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2.11.7-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile`.
- Render test on a synthetic file: a throwaway age key and dummy values in the session scratchpad, plus PyYAML in a venv. Do not run it against the real file, except with `SOPS_AGE_KEY` set and output discarded.
- Not ours, leave alone: `stash@{0}` ("before-main-switch", on `chore/add-role-agents`); PR #198 (JOR-32) and PR #191 (JOR-75); a Build for `2c1091d` on main.
