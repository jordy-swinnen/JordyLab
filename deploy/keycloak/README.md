# `realm-prod.json`

The production Keycloak realm import. No dev users, no localhost redirect URIs (spec.md US2
acceptance scenario 4).

Two values are resolved at Keycloak startup via its `${env.VAR}` property substitution, **not**
edited into this file:

| Placeholder | Resolves to | Consumer |
|---|---|---|
| `${PRODUCTION_DOMAIN}` | The real domain, e.g. `jordylab.be` | `redirectUris`, `webOrigins`, `rootUrl`, `baseUrl`, `post.logout.redirect.uris` on the `jordylab-host` client |
| `${KEYCLOAK_BACKEND_CLIENT_SECRET}` | The `jordylab-backend` service-account client secret | `contracts/secrets-schema.md` — comes from `secrets.sops.yaml`, decrypted at deploy time |

Keycloak resolves `${VAR}` placeholders in import files from the container's environment; no
allowlist is needed (Keycloak "Importing and exporting realms" guide, checked 2026-09-29). The earlier
`${env.VAR}` spelling and `spi-admin-allowed-system-variables` note were replaced by this.

How it's wired: the Keycloak image copies this file to `/opt/keycloak/data/import/` and starts with
`--import-realm`; `PRODUCTION_DOMAIN` comes from the `keycloak-config` ConfigMap (prod overlay) and
`KEYCLOAK_BACKEND_CLIENT_SECRET` from `jordylab-secrets`.

**Import runs once.** If the `jordylab` realm already exists, Keycloak skips the import on startup, so
edits to this file after the first deploy must be applied through the admin console or `kcadm.sh`.

The `jordylab-mobile` and `mobile-release-ci` clients (spec 007) are **not** in this file — they
are their own stop-and-report gate per `jordylab-be/AGENTS.md`, requiring Jordy's sign-off on the
Android application id before they're added.
