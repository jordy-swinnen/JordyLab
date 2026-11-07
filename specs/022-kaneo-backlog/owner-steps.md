# Owner steps for spec 022

Only Jordy can do these: they need the OVH account, the GitHub account settings, or the decrypted secrets in IntelliJ.
The agent never opens decrypted secret content. Each step is a HANDOFF block; a step is done when its "Done when" line
holds. Status is updated here, with the date.

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
- **Status:** open. The first two can be added before HANDOFF-03; the GitHub values need HANDOFF-03 first.

## HANDOFF-03: GitHub App for Kaneo

- **Where:** GitHub, account `jordy-swinnen`: Settings → Developer settings → GitHub Apps → New GitHub App.
- **Webhook URL:** the Kaneo instance's address, `https://kaneo.jordylab.be`, with the webhook path Kaneo's GitHub App page
  gives. **This path is not confirmed yet.** It is checked in T010, which needs a reachable address (see the note below).
- **Permissions and events:** the ones Kaneo's GitHub App page lists. Not read yet; read them at creation.
- **Install** the App on `jordy-swinnen/JordyLab`.
- **Keep:** the App ID, the webhook secret, and the private key (PEM). Turn the PEM into base64 in your terminal and add it
  with HANDOFF-02. Do not paste any of them into chat.
- **Done when:** the App is installed on the repository and its three values are in the encrypted file.
- **Status:** open.

## HANDOFF-04: Kaneo API key for the backend (after the first deploy)

- **Where:** Kaneo, Settings → Account → API keys, once the owner account exists on production.
- **Store it** as a production environment secret, piped in a terminal (never printed):

  ```bash
  gh secret set KANEO_API_KEY --env production
  ```

- **Done when:** `gh secret list --env production` shows `KANEO_API_KEY`.
- **Status:** open, after the deploy.

## HANDOFF-05: Repository variables (done by the agent)

- `BACKLOG_PROVIDER=linear` (stays `linear` until cutover), `KANEO_DISABLE_REGISTRATION=true`, `KANEO_MEM_LIMIT=768m`.
- Set with `gh variable set` on 2026-11-07. The deploy workflow reads them once the Kaneo step is merged (T020).
- **Status:** done.

## Note on the GitHub App check (T010)

GitHub sends the App's webhooks to a public HTTPS address. The trial ran on `127.0.0.1`, so the check cannot run there. It
runs on production, after the merge and the first deploy, against a scratch repository, or it waits for a tunnel (a decision
for Jordy). Until T010 is run, the GitHub linking rules in the instructions stay provisional.
