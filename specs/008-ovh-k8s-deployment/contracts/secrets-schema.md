# Contract: Secrets Schema

The interface between the SOPS-encrypted secrets file and the workloads that consume its values. This is a
contract in the sense that adding a workload that needs a new secret means adding a key here *and* to
`deploy/k8s/overlays/prod/secrets.sops.yaml` — the two must stay in sync, and this table is what
`/speckit-implement` and the runbook's rotation procedure both check against.

**Source of truth for the implementation**: `deploy/k8s/overlays/prod/secrets.sops.yaml` (ciphertext, committed),
decrypted only in `deploy-prod.yml` (see `research.md` §7 for the SOPS + age decision).

| Key | Consumer | Rotation trigger | Notes |
|---|---|---|---|
| `jordylab-db-app` Secret (`username`=jordylab, `password`) | CNPG `Cluster` (initdb owner), backend (`POSTGRES_USER`/`POSTGRES_PASSWORD`) | same | App DB credential; one password, two consumers |
| `jordylab-db-keycloak` Secret (`username`=keycloak, `password`) | CNPG `Cluster` (`managed.roles`), Keycloak (`KC_DB_USERNAME`/`KC_DB_PASSWORD`) | same | Keycloak's own role; owns the `keycloak` schema |
| `KEYCLOAK_ADMIN_BOOTSTRAP_PASSWORD` | Keycloak (first-boot only) | same | Not used after initial realm import |
| `ANTHROPIC_API_KEY` | backend (fna, gamecatalog AI routing) | same | Existing local `.env` value, moved to SOPS for prod |
| `OPENROUTER_API_KEY` (006) | backend | same | New in 006, carried into prod secrets here |
| `KEYCLOAK_BACKEND_CLIENT_SECRET` (006) | backend (as `KEYCLOAK_BACKEND_SECRET`, client-credentials flow), Keycloak realm import | same | Realm file references it as `${KEYCLOAK_BACKEND_CLIENT_SECRET}`; no allowlist needed (deploy/keycloak/README.md) |
| S3 access key + secret key | Barman Cloud Plugin `ObjectStore` | same, plus immediately if OVH Object Storage credentials are rotated | Backup target credentials |
| `STEAM_WEB_API_KEY`, `STEAM_ID` (005) | backend (`SteamOwnedGamesClient`, `@Value`) | same | Steam owned-library sync; unset → sync reports "not configured" |
| `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` (005/009) | backend (`IgdbClient`, `@Value`) | same | Twitch client-credentials for IGDB; unset → graceful no-op |
| `ntfy` auth token (if ntfy is configured with auth) | backend (publish), 007 mobile app (subscribe) | same | Scope depends on `/speckit-implement`'s ntfy auth choice |

**Not in this file** — the APK signing key (spec 007) stays in GitHub Actions secrets directly, not SOPS, per the
drafts' research (§6: "The APK signing key (007) stays in GitHub Actions secrets"), since it's a CI-time signing
step rather than a running workload's config.

**Local development** (`local` profile) does not use this file at all — those same keys come from the gitignored
`jordylab-be/.env`, unchanged from today (US3 scenario 5, FR-002's environment boundary).

**Invariant** (SC-003): none of the values behind these keys ever appear in git history, a container image, or a
ConfigMap — only in `secrets.sops.yaml` as ciphertext, and as decrypted Kubernetes `Secret` objects inside the
cluster after CI applies them.
