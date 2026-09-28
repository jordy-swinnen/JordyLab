# 10. CI/CD

## `build.yml` — every push to `main`

Runs on every push (and every PR, for early feedback): a secret scan (`gitleaks`), a licence check
(no MIT text anywhere — Chapter 9's counterpart in US7), backend and frontend tests, and — only on
`main`, and only once everything else passes — builds and pushes all three images to GHCR, tagged
by commit SHA (never `latest`). This last job never runs on a pull request, deliberately: only code
that's actually merged produces a publishable image.

## `deploy-prod.yml` — gated behind you

Triggered automatically when `build.yml` finishes successfully (or manually via
`workflow_dispatch`, useful for re-deploying an older SHA — see the Rollback section below).
The job's `environment: production` is what makes it **wait for your approval** in the GitHub
Actions UI — this is a GitHub Environment with you configured as a required reviewer
(`docs/runbook.md` §9), not custom code. Nothing after that line runs until you click Approve.

Once approved, the job:
1. Joins your Tailscale network as a temporary, single-use node (Chapter 3 touched on why 6443
   is firewalled; this is how CI reaches it anyway — over a private overlay network, never the
   public internet).
2. Decrypts `secrets.sops.yaml` using the age private key stored as a GitHub Actions secret
   (Chapter 6) — only inside this ephemeral runner, never written back to your checkout.
3. Points Kustomize at the newly built images (`kustomize edit set image`) and applies the whole
   prod overlay.
4. Waits on `kubectl rollout status` for every Deployment — if a rollout doesn't finish within its
   timeout, the job fails loudly. It does not leave a half-applied cluster and report success.

## Rollback

Two equivalent options (`docs/runbook.md` §11): `kubectl rollout undo`, or re-running
`deploy-prod.yml` via `workflow_dispatch` with an older commit SHA. Both should have you back on a
known-good version within 5 minutes (SC-002).

## Hands-on

Push a trivial change, watch `build.yml` run in the GitHub Actions tab, approve the resulting
`deploy-prod.yml` run, and follow its steps live.
