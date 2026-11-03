# 10. CI/CD

## `build.yml` — every push to `main`

Runs on every push (and every PR, for early feedback): a secret scan (`gitleaks`), a licence check
(no MIT text anywhere — Chapter 9's counterpart in US7), backend and frontend tests, and — only on
`main`, and only once everything else passes — builds and pushes all three images to GHCR, tagged
by commit SHA (never `latest`). This last job never runs on a pull request, deliberately: only code
that's actually merged produces a publishable image.

The backend image also receives the commit it was built from as a build argument, and reports it on
`/actuator/info`. That is how "what is live?" gets an answer that is not a guess (spec 019, FR-017).

The Build publishes images; it deploys nothing. Production is the Docker Compose stack on the VPS (spec 020),
and k3s was retired at the cutover.

## `deploy-prod-compose.yml` — a deploy you start

Deploying is by hand: *Deploy to Production (Compose)* → *Run workflow* with a target. The target is a full
commit sha or a release tag `vX.Y.Z` / `vX.Y.Z-rcN`, and it maps to the image tag `sha-<sha>` or the tag
itself, so a sha deploy runs exactly the images its Build published. Nothing is rebuilt and nothing is
retagged.

No approval is needed: the `production` environment is there for its secrets. Deploys are manual because
automatic deploys were paused at the cutover; spec 020's T062 decides when they return.

Before it touches the host, the workflow resolves the target with `deploy/scripts/deploy-decision.sh` and
refuses one it cannot resolve: a sha outside the history, a tag that does not exist, or a missing image
(FR-014). Then it:

1. Joins the tailnet as a temporary `tag:ci` node, checks the host's pinned ed25519 key, and connects over
   SSH as `jordylab-deploy` (Chapter 3's counterpart: the host has no public SSH for CI).
2. Mirrors `deploy/compose` to `/opt/jordylab/compose`; renders `prod.env` from `secrets.sops.yaml` on the
   runner, so the plaintext never reaches a log or your checkout (Chapter 6); writes it on the host, keeping
   the previous file as `prod.env.previous`.
3. Runs `deploy-release.sh deploy <release>`: it pulls and restarts only backend, frontend and keycloak,
   checks the site through Caddy on the host, and records the release in
   `/opt/jordylab/state/current-release`. Postgres, ntfy and Caddy are not restarted by a release.
4. Smoke-checks the site from the runner: home 200, an unauthenticated API call 401, Keycloak discovery 200.
5. If anything after the environment file fails, asks the host script to restore the previous release and
   `prod.env.previous`. The job ends red, and its summary names what is live.

## Rollback

By hand: run the workflow with an older commit sha or release tag. An older target is allowed, which is
what makes it a rollback. A failed deploy rolls back by itself (step 5). A rollback stays live until the
next deploy, because automatic deploys are off; revert the bad commit so the next deploy does not put it
back.

## Hands-on

Push a trivial change, watch `build.yml` publish the images, then run *Deploy to Production (Compose)* with
the merge commit's sha and follow its steps live. Compare `sudo docker ps` on the host and `/actuator/info`
before and after.
