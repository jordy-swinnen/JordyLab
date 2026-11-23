# Owner steps for spec 022

Only Jordy can do these: they need the OVH account, the GitHub account settings, or the decrypted secrets in IntelliJ.
The agent never opens decrypted secret content. Each step is a HANDOFF block; a step is done when its "Done when" line
holds. Status is updated here, with the date.

## Remaining steps, in order (2026-11-18)

### Confirmed live state (read-only checks, 2026-11-18)

- Production runs `75e7da7` (the Build deploy job succeeded), `https://kaneo.jordylab.be/api/health` answers, the MCP
  endpoint is live and refuses a request without a token (401). T025 is done.
- The workspace `JordyLab` has one member (Jordy, owner) and two projects: `JOR` (93 tasks, numbers up to `JOR-123`, 59%
  done, columns ideas/refinement/todo/in-progress/in-review/done/canceled) and `RA` (Roblox AI, 0 tasks, private).
- The Linear import already ran in production: the Linear-era tickets (`JOR-8`, `JOR-14`, `JOR-35`, ...) are there. What
  is missing is the `Spec:` first line on the spec tickets (the fix-up has not run); `JOR-85`'s first line is still the
  original text.
- `JOR` was readable without signing in through the public-board route; it is now private (the public route answers 403),
  so the lock-down check T027 holds.
- Repository variables: `BACKLOG_PROVIDER=linear` (cutover pending), `KANEO_DISABLE_REGISTRATION=true`,
  `KANEO_MEM_LIMIT=768m`, `AUTO_DEPLOY=true`; workspace creation stays at its `true` default. Production environment
  secrets: no `KANEO_API_KEY` yet, so the idea box is not wired to Kaneo.
- GitHub sign-in is configured on the instance. The GitHub App itself cannot be checked from outside (its API needs App
  credentials), and `GITHUB_APP_NAME` is not set in `compose.yaml`.
- The SOPS keys are present by name and the deployed instance uses them; the owner still confirms the values are the
  intended ones.

### Then what is left

1. `Spec:` links: run the fix-up. Its mapping now targets the JOR-111 columns directly (Triage, Backlog and Drafted ->
   `ideas`; Specified -> `todo`) and it creates no columns, so a dry run is safe to review and `--apply` only moves
   tickets and writes the `Spec:` first lines.
2. Saved views or filter recipes (T038).
3. The backend API key (HANDOFF-04) and the live client check (T052, T053).
4. The GitHub App (HANDOFF-03), `GITHUB_APP_NAME`, the repository connection and automations (T069, T070), then the
   matrix on a scratch repository (T010, T071).
5. Claude Code and Claude app MCP sign-in (T009).
6. Capacity record and restore drill (T028, T029).
7. Relink (T054), `BACKLOG_PROVIDER=kaneo` and a redeploy (T072), Linear off (T073), the cutover record (T074), then the
   30-day follow-up (T076-T079).

The ordered checklist below is the original plan, kept for the reasoning.

**Before the merge**
1. Confirm the Kaneo secrets in the encrypted file are the live ones (HANDOFF-02). The local patch saved on 2026-11-12
   was superseded by the merged keys; nothing to apply.
2. Create the GitHub App and add its three values (HANDOFF-03).
3. Confirm `BACKLOG_PROVIDER=linear` and `KANEO_DISABLE_REGISTRATION=true` (HANDOFF-05), then merge the pull request and
   let the deploy run (T025).

**After the first deploy**
4. Open the board and create the owner account; check that a second sign-up is refused (T026, T027).
5. Generate the GitHub OAuth client secret, add the two OAuth keys (HANDOFF-06), and sign in with GitHub.
6. Install the GitHub App on `jordy-swinnen/JordyLab`, connect the repository to project `JOR`, set the branch regex and
   the automations with merged -> Don't move (T069, T070).
7. Sign in from Claude Code (`/mcp`) and from the Claude app custom connector (T009); run the GitHub App matrix on a
   scratch repository (T010) and fix the written rules if it differs (T071).
8. Capacity record, lock-down check and restore drill (T028, T029, T027).

**Import (after the freeze)**
9. Announce the freeze (T033) and take the full Linear export off the VPS (T034).
10. Import Linear once in Kaneo (T035). Probe the description write once, then run the fix-up dry run and `--apply`
    (HANDOFF-04, T036), and fold Triage/Drafted/Specified into `ideas`/`todo`.
11. Run the verification checklist (T037), create the saved views (T038), create the backend API key and run the live
    client check (T052, T053).

**Cutover**
12. Relink the stored idea-box links (T054), set `BACKLOG_PROVIDER=kaneo` and redeploy, then run the ten accepted ideas
    (T072).
13. Switch Linear off, revoke the keys and announce it (T073); record the cutover (T074).
14. At the 30-day mark, confirm and open the follow-up pull request that removes Linear (T076-T079).

## HANDOFF-01: DNS record for kaneo.jordylab.be

- **Where:** OVH control panel, domain `jordylab.be`, DNS zone.
- **What:** add an `A` record for `kaneo` pointing at the VPS IPv4 `57.129.163.110`.
- **When:** before the merge, so the first deploy can issue the certificate.
- **Done when:** `dig +short kaneo.jordylab.be @1.1.1.1` prints `57.129.163.110`.
- **Status:** done (2026-11-07). Added in OVH as an A record through Actions → Add sub-domain (the zone has no separate
  "Add an entry" button in this view). Checked through 1.1.1.1 and 8.8.8.8; the apex record was not changed.

## HANDOFF-02: SOPS secrets for Kaneo (edit in IntelliJ)

- **Where:** a terminal in the repository root. Jordy runs this, not the agent:

  ```bash
  SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml
  ```

- **Add these keys** (values stay in the editor, never in chat):
  - `KANEO_AUTH_SECRET`: generate with `openssl rand -hex 32`.
  - `KANEO_DB_PASSWORD`: generate with `openssl rand -hex 16`.
  - `KANEO_GITHUB_APP_ID`, `KANEO_GITHUB_WEBHOOK_SECRET`, `KANEO_GITHUB_PRIVATE_KEY_BASE64`: from HANDOFF-03.
- **Afterwards:** clear IntelliJ's Local History for the temporary file, then check the file decrypts (output discarded):

  ```bash
  sops -d deploy/k8s/overlays/prod/secrets.sops.yaml >/dev/null && echo DECRYPT_OK
  ```

- **Done when:** the keys above exist in the encrypted file and `DECRYPT_OK` prints.
- **Status:** done: the keys are in the encrypted file (merged 2026-11-07/08). The owner's local edit saved on 2026-11-12
  was superseded by those merged values and does not apply; nothing else to add. Confirm the values are the live ones.

## HANDOFF-03: GitHub App for Kaneo

- **Where:** GitHub, account `jordy-swinnen`: Settings → Developer settings → GitHub Apps → New GitHub App. GitHub asks
  for the account password (sudo mode) first; that is entered by you in the browser, never in chat.
- **Settings, as Kaneo's setup page gives them** (read 2026-11-07 from kaneo.app/docs, setup.md):
  - Name: unique on GitHub, for example `kaneo-jordylab`. Homepage URL: `https://kaneo.jordylab.be`.
  - Webhook URL: `https://kaneo.jordylab.be/api/github-integration/webhook` (this path, not `/api/github/webhook`).
  - Webhook secret: a random value you generate; the same value goes into `KANEO_GITHUB_WEBHOOK_SECRET`.
  - Repository permissions: Issues read and write; Pull requests read; Contents read; Metadata read.
  - Events to subscribe: Issues, Issue comments, Pull requests, Push.
  - Keep the webhook active. Kaneo's docs say GitHub must reach the endpoint without signing in to the proxy.
  - Account permissions: none documented.
- **For the matrix now:** create the App with the webhook **inactive** and no secret typed; it is switched on with the URL and
  secret when the production stack is up (T010). The webhook URL needs a public address, which the trial could not give.
- **Install** the App on `jordy-swinnen/kaneo-matrix-scratch` (the scratch repository) for T010, and later on
  `jordy-swinnen/JordyLab` for the production connection.
- **Keep:** the App ID, the app slug (`GITHUB_APP_NAME`), the webhook secret, and the private key (PEM). Generate the key on
  the App's settings page, then encode the PEM as base64 in your terminal (`base64 -i key.pem`) and add it with HANDOFF-02.
  Do not paste any of them into chat.
- **Done when:** the App is installed on the repository and its three values are in the encrypted file.
- **Status:** after the first deploy (2026-11-07). It is needed for the GitHub check (T010), not for the deploy. Confirm
  when the compose service is written that Kaneo starts without these values.

## HANDOFF-04: Kaneo API key for the backend (after the first deploy)

- **Where:** Kaneo, Settings → Account → API keys, once the owner account exists on production.
- **Store it** as a production environment secret, piped in a terminal (never printed):

  ```bash
  gh secret set KANEO_API_KEY --env production
  ```

- **Done when:** `gh secret list --env production` shows `KANEO_API_KEY`.
- **Status:** open, after the deploy. Before the fix-up's `--apply`, probe the write path once on a throwaway ticket:
  read it by ticket id (does the reply carry its description, or `descriptionDeferred`?), `PUT /task/description/<id>`
  with the same text plus a marker, read it back, then restore the original text and delete the ticket. This confirms
  that the pinned Kaneo version supports the description write the fix-up uses.

## HANDOFF-05: Repository variables (done by the agent)

- `BACKLOG_PROVIDER=linear` (stays `linear` until cutover), `KANEO_DISABLE_REGISTRATION=true`, `KANEO_MEM_LIMIT=768m`.
- Set with `gh variable set` on 2026-11-07. The deploy workflow reads them once the Kaneo step is merged (T020).
- **Status:** done.

## HANDOFF-06: GitHub sign-in for Kaneo (the owner's secret, in IntelliJ)

Sign-in with GitHub is separate from the GitHub App. Kaneo needs it before a repository can be connected to a project
(Project → Settings → Integrations → GitHub asks for it).

- **Done already (by the agent, 2026-11-08):** the GitHub OAuth App **Kaneo JordyLab** is registered on the owner's account,
  with homepage `https://kaneo.jordylab.be` and callback `https://kaneo.jordylab.be/api/auth/callback/github`. Its client ID
  is `Ov23li01xDaCdsuTnxA9`. The client secret has **not** been generated.
- **Owner:** on GitHub → Settings → Developer settings → OAuth Apps → Kaneo JordyLab, click **Generate a new client secret**
  and copy it once. Do not paste it into chat.
- **Owner:** in the repository root, edit the encrypted file in IntelliJ:

  ```bash
  SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml
  ```

  Add `KANEO_GITHUB_OAUTH_CLIENT_ID` (the client ID above) and `KANEO_GITHUB_OAUTH_CLIENT_SECRET` (the new secret) to
  `jordylab-secrets`, then save.
- **Then:** the change reaches production on the next deploy (the Kaneo step recreates Kaneo with the new variables).
  Test sign-in in a private window: GitHub button on `https://kaneo.jordylab.be`.
- **Done when:** the GitHub button appears on the Kaneo sign-in page and sign-in returns to Kaneo.
- **Status:** open. The repository change that passes these variables to Kaneo is in its PR, and the deploy must run before
  they take effect.

## Note on the GitHub App check (T010)

GitHub sends the App's webhooks to a public HTTPS address. The trial ran on `127.0.0.1`, so the check cannot run there. It
runs on production, after the merge and the first deploy, against a scratch repository, or it waits for a tunnel (a decision
for Jordy). Until T010 is run, the GitHub linking rules in the instructions stay provisional.
