# 10. CI/CD

## `build.yml` — every push to `main`

Runs on every push (and every PR, for early feedback): a secret scan (`gitleaks`), a licence check
(no MIT text anywhere — Chapter 9's counterpart in US7), backend and frontend tests, and — only on
`main`, and only once everything else passes — builds and pushes all three images to GHCR, tagged
by commit SHA (never `latest`). This last job never runs on a pull request, deliberately: only code
that's actually merged produces a publishable image.

The backend image also receives the commit it was built from as a build argument, and reports it on
`/actuator/info`. That is how "what is live?" gets an answer that is not a guess (spec 019, FR-017).

A `deploy` job then runs after the images are pushed, but only while the repository variable
`AUTO_DEPLOY` is `true`. The variable is the pause switch: set it to anything else and a green build
stops deploying, without a code change.

## `deploy-prod.yml` — a green build goes live by itself

Since spec 019 there is no approval step. A green build on `main` calls this workflow with its own
commit SHA, and the images that passed the tests are the images that run. Nothing is rebuilt and
nothing is retagged. The job's `environment: production` still gives it the secrets (SOPS key,
Tailscale credentials, the cluster token); the required reviewer that used to make it wait is gone
(`docs/runbook.md` §9).

The job, in order:

1. Checks the target. A commit must be in the history, and its three images must exist in GHCR. A
   target that fails either check ends the run before the cluster is touched.
2. Reads the live images from the cluster. If the commit is older than the live one (a slow build
   that finished late), it is skipped and the run stays green: it would otherwise undo a newer fix.
3. Checks the newest completed database backup. A stale or missing backup is a warning, never a block.
4. Decrypts `secrets.sops.yaml` with the age key from GitHub's secrets, only inside this ephemeral runner
   (Chapter 6), and points Kustomize at the target's images (`kustomize edit set image`). It applies the
   whole prod overlay.
5. Waits on `kubectl rollout status` for every Deployment, then smoke-checks the public site: the home
   page, an unauthenticated API call that must answer 401, and Keycloak's discovery document.
6. Reads the commit the running backend reports. A mismatch is a warning: the image, not the report, is
   what the cluster ran.

A failed rollout or smoke check does not leave the cluster half-changed and report success. It rolls
back to the commit that was live before, with that commit's own images and manifests, and the run ends
red.

Deploys never overlap. The concurrency group uses `queue: max`, so waiting deploys run in order and
none is dropped. A newer deploy does not cancel a queued rollback, which would be the one thing you
cannot afford to lose.

## Rollback

A rollback is either automatic (step 5 above) or by hand: *Deploy to Production* → *Run workflow* with a
commit SHA or a release tag (`docs/runbook.md` §11). A manual run is never skipped as superseded, so you
can always go back. Two things to remember: a rollback lasts only until the next green merge (revert the
bad commit to keep it), and it re-applies the previous secrets file (roll forward for a secret rotation).

## Dry run

Before changing anything on the deploy path, run *Deploy to Production* with `dry_run` ticked. It
validates the target, reads the live state, does a server-side apply dry run and runs the smoke checks,
and changes nothing.

## Hands-on

Push a trivial change, watch `build.yml` run in the GitHub Actions tab, and follow the `deploy` job's
summary. Then run a dry run of the same commit, and look at the commit the backend reports on its info
endpoint.
