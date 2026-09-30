# Cowork script: deploy JordyLab to production (OVH VPS-2 + k3s)

Paste everything below the line into a fresh Cowork session. The session needs the `JordyLab` folder connected and the
built-in browser available. Written 2026-09-29 from `specs/008-ovh-k8s-deployment/` and `docs/runbook.md`.

---

## Role and goal

You are deploying JordyLab to production, following `docs/runbook.md` (sections 1-11) and validating with
`specs/008-ovh-k8s-deployment/quickstart.md`. Target: single-node k3s on one OVH VPS-2 (4 vCores / 8 GB / 75 GB NVMe),
one public HTTPS domain serving `/` (web), `/api` (backend), `/auth` (Keycloak public endpoints), plus `/ntfy`.

Jordy has already created an OVH account with the VPS-2 subscription paid. Everything else is yours, except the steps
marked **JORDY**.

## Working rules

1. **Read before acting.** Read `docs/runbook.md`, `specs/008-ovh-k8s-deployment/{spec,plan,research,tasks}.md` and
   `deploy/` first. Where this script and the runbook disagree, stop and tell Jordy which one you followed and why.
2. **Live docs beat pinned versions.** Before installing anything, check the pinned version against the vendor's own
   release page or docs. Report mismatches before proceeding (see "Known issues").
3. **Gates.** At every `GATE`, stop, summarise what you did and what you are about to do, and wait for Jordy's explicit
   yes. Never chain past a gate.
4. **Secrets never pass through you.** Do not read, print, log, screenshot or commit: the age private key, SSH private
   keys, S3 keys, API keys, Tailscale auth keys, kubeconfig tokens, or any password. If a page displays a secret, do not
   screenshot it; tell Jordy to copy it himself. Steps marked **JORDY** are done by him at his own terminal or in his
   password manager.
5. **Irreversible or billable actions need a GATE**: creating Object Storage, buying/changing a domain, changing DNS,
   `ufw enable`, `sshd` restarts, `helm install`, `kubectl apply`, pushing to `main`, approving the deploy workflow.
6. **Keep a lockout escape.** Before `ufw enable` and before disabling password/root SSH, confirm a second working SSH
   session and know where the OVH web console (KVM/VNC) is.
7. **Shell is Fish** for anything Jordy runs (no bash heredocs). On the VPS use bash.
8. **Browser:** use the built-in browser. Read pages with page-text/read-page rather than screenshots. Never trigger JS
   dialogs. If a login, captcha or 2FA appears, hand the tab to Jordy and wait. If a click fails 2-3 times, stop and
   ask.
9. If SSH or `kubectl` cannot be run from your shell (egress limits), do not retry blindly: give Jordy the exact command
   to run, and continue when he pastes back the output (redacted of secrets).
10. Track progress with the task list; the last task is verification.

## Phase 0. Preflight (no side effects)

- Read the docs above. Run `git status` in the repo. **The working tree currently has uncommitted changes
  in `jordylab-be/` (gamecatalog) and `.specify/feature.json`.** Do not touch or commit them; make deployment edits on a
  new branch or as separate commits containing only deployment files.
- Ask Jordy (single question round) for:
    - the domain name, and whether it is already bought at OVHcloud (spec assumes yes);
    - GitHub owner/repo (images go to `ghcr.io/<owner>/...`);
    - whether he has a Tailscale account and Tailscale installed on his MacBook;
    - his SSH public key path;
    - VPS datacenter: Jordy ordered VPS-2 in United Kingdom (Erith), the only location offered for VPS-2/VPS-3. This is
      accepted; do not raise it again.
- List what is unresolved in the repo: `__DOMAIN__` and `__TRAEFIK_CIDR__` placeholders in
  `deploy/k8s/overlays/prod/kustomization.yaml`, `age1REPLACE_...` in `.sops.yaml`, real values in `secrets.sops.yaml`,
  the `jordylab-fe` production environment domain, and unchecked tasks T023, T073, T074.
- **GATE 0:** present the plan and the "Known issues" resolutions.

## Phase 1. VPS in the OVH panel (browser)

Runbook section 1. Jordy is signed in; if not, hand over for login/2FA.

1. Open the OVH control panel > Bare Metal Cloud > VPS. Confirm the paid VPS-2 exists and is the right plan (4 vCores /
   8 GB / 75 GB NVMe). Confirm the billing term is monthly, not a 12-month commitment, and note the actual monthly
   price (target under ~EUR 13/mo all-in).
2. If it is not yet installed, install (or reinstall) with **Ubuntu LTS or Debian stable**, and add Jordy's SSH **public
   ** key. Do not enable password login.
3. Record: public IPv4, IPv6, hostname, default SSH user, datacenter.
4. Locate the OVH web console (KVM) for the lockout escape and note how to open it.
5. **GATE 1:** report the facts; wait for yes.

## Phase 2. Domain DNS (browser)

Runbook section 6, part 1.

1. In OVH: Web Cloud > Domain names > the domain > DNS zone. Create the `A` record (`@` -> VPS IPv4). Skip AAAA unless
   the whole stack is verified on IPv6.
2. **GATE 2** before saving the record. After saving, verify propagation with `dig +short <domain>` from at least two
   resolvers.

## Phase 3. Harden the OS (SSH to VPS)

Runbook section 2. Do each numbered item, verifying before the next.

1. Create a non-root sudo user with Jordy's key. Open a **second** session and confirm login works as that user.
2. **GATE 3a**, then set `PermitRootLogin no` and `PasswordAuthentication no`, `sshd -t`, restart, confirm the second
   session still works.
3. Enable unattended-upgrades.
4. Firewall with ufw: default deny in, allow 22/80/443. **Also verify against the current k3s docs whether ufw needs to
   allow the pod and service CIDRs (`10.42.0.0/16`, `10.43.0.0/16`)**; k3s documents this for ufw hosts. Add them if the
   docs say so.
5. **GATE 3b** before `ufw enable`.

## Phase 4. Tailscale, then k3s (SSH to VPS)

Order matters: Tailscale first, so the k3s API certificate can include the Tailscale address. (The runbook does k3s
first; see Known issue 2.)

1. Install Tailscale on the VPS and `tailscale up --ssh` (Jordy authenticates in the browser; hand over the login URL).
   Note the VPS Tailscale IP and MagicDNS name. Confirm Tailscale is on Jordy's MacBook.
2. Tag the node in the Tailscale admin console and restrict ACLs so a CI ephemeral node reaches the VPS on 6443 only (
   browser, **GATE 4a** before saving ACL changes; back up the current ACL text first).
3. Determine the k3s version: query the k3s stable channel (`curl -s https://update.k3s.io/v1-release/channels`) and
   compare with the runbook's `v1.37.0+k3s1`. **GATE 4b:** show both and let Jordy choose the version.
4. Install k3s with that version and `--tls-san <tailscale-ip> --tls-san <magicdns-name>`. Do not expose 6443 publicly.
5. Verify `kubectl get nodes` and `kubectl get pods -n kube-system` (Traefik, CoreDNS, svclb, local-path-provisioner all
   Running).
6. Fetch the kubeconfig to Jordy's Mac **over Tailscale** (server URL = Tailscale address, not the public IP). Do not
   print it. Save to `~/.kube/jordylab.yaml` with mode 600 outside the connected folder.
7. Apply `deploy/host/traefik-helmchartconfig.yaml` onto the VPS as in the runbook. Confirm
   `kubectl get gatewayclass,gtw -A`.
8. Find the real Traefik pod CIDR needed for `__TRAEFIK_CIDR__` and explain to Jordy what it is used for before you fill
   it in.

## Phase 5. Cluster add-ons (kubectl/helm)

Runbook section 5. For each chart, verify the pinned version exists (`helm search repo ... --versions`) before
installing. **GATE 5**, then install cert-manager, CloudNativePG, the Barman Cloud plugin. Note the runbook applies
`.../releases/latest/download/manifest.yaml` for the plugin, which is unpinned; pin it to the release you actually get
and record it. Then apply `cert-manager-clusterissuer.yaml`. Check the ClusterIssuer email is Jordy's and the ACME
endpoint is production (start with staging if Jordy prefers, to avoid Let's Encrypt rate limits).

## Phase 6. Object Storage bucket (browser)

Runbook section 8. **GATE 6** (billable). Create an S3 container in an EU region (e.g. Gravelines or Strasbourg; a
different site from the VPS is fine and better for backups), private access. Confirm the region's S3 endpoint and put it
in the ObjectStore config. Create an S3 user and keys scoped to that container. **JORDY:** copy the access and secret
key himself into his password manager; do not view or transcribe them.

## Phase 7. Keys and secrets (mostly JORDY)

Runbook sections 7 and 13. This phase is deliberately human.

1. **JORDY:** `age-keygen -o ~/jordylab-prod.age.key` (outside the repo). Give Cowork only the **public** key line.
   Private key goes to the password manager and to the GitHub `production` environment as `SOPS_AGE_KEY`; then delete
   the local file.
2. Cowork: put the public key in `.sops.yaml`; commit only that file.
3. **JORDY:** `sops deploy/k8s/overlays/prod/secrets.sops.yaml` and fill each key in `contracts/secrets-schema.md` (DB
   passwords, Keycloak bootstrap admin, `ANTHROPIC_API_KEY`, `OPENROUTER_API_KEY`, Keycloak service-account secret, S3
   keys, ntfy token). Cowork can list the key names to fill and later check that the file contains only ciphertext, but
   must not read decrypted values.
4. Cowork: replace `__DOMAIN__` and `__TRAEFIK_CIDR__`, set the frontend production environment domain, then run
   `gitleaks` on the repo.

## Phase 8. GitHub and CI (browser)

Runbook section 9.

1. Repo Settings > Environments: create `production`, required reviewer = Jordy. **JORDY** adds `SOPS_AGE_KEY`;
   Tailscale OAuth client or auth key (create the OAuth client in the Tailscale admin console, scoped to ephemeral nodes
   with a tag; Jordy copies the secret himself).
2. Apply `ci-deploy-rbac.yaml`, mint the namespace-scoped token, and **JORDY** stores it as `KUBE_TOKEN`, with
   `KUBE_SERVER` (Tailscale address) and `KUBE_CA_CERT` (Cowork may set the non-secret two).
3. Confirm the workflows reference the same secret names (`.github/workflows/deploy-prod.yml`).

## Phase 9. First deploy and validation

Runbook sections 10 and 6 part 2.

1. **GATE 9:** show the diff of every deployment file changed and the commit list; Jordy confirms, then push to a branch
   and open a PR (or push to `main` only if Jordy says so). Watch `build.yml`; fix failures that are deployment-file
   problems, report others.
2. When the deploy workflow waits for approval: **JORDY approves** in GitHub. Watch `kubectl rollout status` and pods.
3. Check `kubectl get certificate -n jordylab` reaches Ready. If not, follow runbook section 17.
4. Run the quickstart checks: US1 (curl checks and `/auth/admin` unreachable), US2, US3 (gitleaks), US5 backup exists (
   `kubectl get backups.postgresql.cnpg.io -n jordylab`), security scan `nmap -Pn <ip>` (only 22/80/443).
5. Open `https://<domain>` in the built-in browser and confirm the app loads and the login redirects to `/auth`. Jordy
   logs in himself.
6. **Restore drill** (runbook section 15) is mandatory before go-live: do it against a fresh CNPG Cluster and time it (
   target under 30 minutes). **GATE 10** before starting it.
7. Tick T023, T073, T074 in `tasks.md` only for checks that actually passed.

## Final report

Give: what is live and the URL; versions installed (k3s, cert-manager, CNPG, Barman plugin); monthly cost seen at
checkout; every deviation from the runbook; open items (rollback test, JordyBox scanner pointed at prod, licence check,
quarterly drill reminder); and proposed edits to `docs/runbook.md` fixing what you found. Do not write those edits until
Jordy agrees.

---

## Known issues found while preparing this script (fact-check)

1. **k3s version.** `research.md` and the runbook pin `v1.37.0+k3s1` as the "current stable release". On the k3s GitHub
   releases page the newest stable I could see was `v1.36.3+k3s1` (4 Aug 2026), with no 1.37. I could not confirm the
   stable channel directly, so Phase 4 checks it at install time. Do not paste the pinned version blindly.
2. **Runbook order breaks kubectl.** Runbook section 3 points the kubeconfig at the VPS public IP, but section 2
   firewalls 6443 and section 4 (Tailscale) comes after. Also, the k3s API certificate only covers the Tailscale name/IP
   if you pass `--tls-san`. Result: kubectl from the Mac would time out or fail TLS verification. This script installs
   Tailscale first and uses `--tls-san`.
3. **ufw and k3s.** k3s documents extra ufw rules for pod/service traffic on hosts with ufw; the runbook omits them.
4. **Price.** Runbook quotes "from EUR 7.21/mo". OVH's page currently shows "from $8.50/month" and states this is the
   12-month upfront rate; monthly no-commitment will cost more. Confirm at checkout; the spec target is at most about
   EUR 13/mo excluding AI usage.
5. **Unpinned plugin.** The Barman Cloud plugin install uses `latest`; pin it.
6. **Repo state.** Uncommitted gamecatalog changes exist, and tasks T023/T073/T074 plus the domain/age/CIDR placeholders
   are still open.
