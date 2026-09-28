# `realm-prod.json`

The production Keycloak realm import. No dev users, no localhost redirect URIs (spec.md US2
acceptance scenario 4).

Two values are resolved at Keycloak startup via its `${env.VAR}` property substitution, **not**
edited into this file:

| Placeholder | Resolves to | Consumer |
|---|---|---|
| `${env.PRODUCTION_DOMAIN}` | The real domain, e.g. `jordylab.be` | `redirectUris`, `webOrigins`, `rootUrl`, `baseUrl`, `post.logout.redirect.uris` on the `jordylab-host` client |
| `${env.KEYCLOAK_BACKEND_CLIENT_SECRET}` | The `jordylab-backend` service-account client secret | `contracts/secrets-schema.md` — comes from `secrets.sops.yaml`, decrypted at deploy time |

Both env var names **must** be present in Keycloak's `spi-admin-allowed-system-variables` allowlist
or the realm import will silently fail to resolve them (research.md §9) — set on the container via:

```
KC_SPI_ADMIN_REALM_RESTAPI_EXTENSION_ADMIN_ALLOWED_SYSTEM_VARIABLES=PRODUCTION_DOMAIN,KEYCLOAK_BACKEND_CLIENT_SECRET
```

(exact SPI/env-var spelling should be verified against the live Keycloak 26.7.4 docs before the
first real import — flagged as moderate-confidence in `research.md` §9, since `keycloak.org` was
unreachable during research).

The `jordylab-mobile` and `mobile-release-ci` clients (spec 007) are **not** in this file — they
are their own stop-and-report gate per `jordylab-be/AGENTS.md`, requiring Jordy's sign-off on the
Android application id before they're added.
