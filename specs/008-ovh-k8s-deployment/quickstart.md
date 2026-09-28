# Quickstart: Validating the Production Deployment

Runnable checks that prove this feature works end-to-end, one per user story in `spec.md`. These assume the
cluster is already bootstrapped (US8's bootstrap guide, written up fully in `docs/runbook.md` during
`/speckit-implement`) and at least one deploy has succeeded (US4). Full command detail belongs in the runbook;
this is the validation pass, not the how-to.

## Prerequisites

- `kubectl` configured against the prod cluster (over Tailscale, per `research.md` §8 — not the public internet).
- The age private key available locally (from the password manager) if a secret needs decrypting/inspecting.
- A phone on mobile data (not the home network) for the "friend on mobile data" checks below.

## US1 — Live on a public HTTPS domain

```sh
curl -Iv https://<domain>/                 # expect HTTP/2 200, a valid Let's Encrypt cert, no redirect loop
curl -I http://<domain>/                   # expect a 301/308 redirect to https
curl -I https://<domain>/api/actuator/health   # expect 200 from the backend, same origin
curl -I https://<domain>/auth/realms/jordylab  # expect 200 from Keycloak
curl -I https://<domain>/auth/admin            # expect connection refused / not routed (contracts/http-routing.md)
```

Open `https://<domain>` from a phone on mobile data, log in, open the Game Catalog (US1's Independent Test).
Confirm the JordyBox scanner's existing scan command works unchanged against the prod URL.

## US2 — Two clearly separated environments

```sh
kubectl -n jordylab exec deploy/backend -- env | grep -i SPRING_PROFILES_ACTIVE   # expect exactly "prod"
```

Start the backend locally with no `SPRING_PROFILES_ACTIVE` set and confirm it refuses to start with a clear error
(the fail-fast test from `plan.md`'s Constitution Check — also covered by an automated JUnit test, not just this
manual check). Open `docs/environments.md` and confirm every differing setting is listed (FR-003).

## US3 — Secrets are injected, never committed

```sh
cat deploy/k8s/overlays/prod/secrets.sops.yaml   # expect only key names + ciphertext, no plaintext values
gitleaks detect --source . --no-git=false        # expect zero findings (same check CI runs on every push)
sops deploy/k8s/overlays/prod/secrets.sops.yaml  # edit one value, save; commit; let deploy-prod.yml run
kubectl -n jordylab exec deploy/backend -- env | grep <ROTATED_KEY>   # expect the new value, post-restart
```

See `contracts/secrets-schema.md` for the full key list and which workload consumes each one.

## US4 — One-click, approved deployments with rollback

Push a small change to `main`, approve the resulting `deploy-prod.yml` run in GitHub, then:

```sh
kubectl -n jordylab rollout status deploy/backend   # expect "successfully rolled out" within 10 minutes
```

Then trigger a rollback (re-run with the previous SHA, or `kubectl -n jordylab rollout undo deploy/backend`) and
confirm the previous version is serving again within 5 minutes (SC-002).

## US5 — Data is durable and restorable

```sh
kubectl get backups.postgresql.cnpg.io -n jordylab   # expect a recent base backup + WAL archive entries
```

Follow `docs/runbook.md`'s restore procedure to restore to a fresh CNPG `Cluster` from a point in time; confirm the
app works against it, and that the whole drill completes in under 30 minutes (SC-004). This is mandatory once
before go-live and quarterly thereafter (`/speckit-clarify`). Reboot the VPS and confirm game artwork and DB data
are both still present after it comes back (local-path survives a reboot, not a disk loss).

## US6 — Learn Kubernetes and Podman

Work through `docs/learn/`'s hands-on exercises: build an image locally, sandbox a manifest with
`podman kube play`, port-forward to the Keycloak admin console, read pod logs, rotate a SOPS secret (same steps as
the US3 check above), and perform the US4 rollback unassisted.

## US7 — The public code is readable but not reusable

```sh
grep -ril "mit license" . --exclude-dir=node_modules --exclude-dir=.git   # expect zero matches (CI's own check)
docker inspect ghcr.io/jordy-swinnen/jordylab-backend:<sha> --format '{{ index .Config.Labels "org.opencontainers.image.licenses" }}'
```

Confirm exactly one root `LICENSE` file exists, package manifests declare the matching licence, and the README's
licence section reads correctly.

## US8 — First-time setup from zero

Not independently re-run once bootstrapped — validated the first time by simply reaching this point at all. Any
future full VPS rebuild re-validates it end-to-end via `docs/runbook.md`'s rebuild procedure.

## Security check (SC-007, SC-008)

```sh
nmap -Pn <vps-public-ip>   # expect only 22 (restricted), 80, 443 open — never 6443, 10250, or 8472/udp
```
