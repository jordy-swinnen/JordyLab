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

## Changes applied to the live realm after the first import

Each entry is idempotent and runs inside the Keycloak pod with the admin credentials already in its
environment (nothing is typed or printed):

```bash
kubectl -n jordylab exec deploy/keycloak -- sh -c '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" --config /tmp/kc.cfg && /opt/keycloak/bin/kcadm.sh add-roles -r jordylab --rname admin --rolename guest --rolename gamecatalog-scanner --config /tmp/kc.cfg; rm -f /tmp/kc.cfg'
```

| Date | Change | Why |
|---|---|---|
| 2026-09-30 | `admin` becomes a composite of `guest` + `gamecatalog-scanner` | The owner holds every JordyLab role and can run the scanner without a hand-assigned role (spec 011 BUG-028). `mobile-release-publisher` stays CI-only (007 FR-004). |
| 2026-09-30 | `jordylab-backend` gets a client scope mapping for `realm-management` → `view-users`, `manage-users`, `view-roles` | The client has `fullScopeAllowed=false`, so without the mapping its service-account token carried none of these roles and the Admin REST API answered 403 (Settings → Users, spec 011 BUG-031). |

For the local Podman Keycloak use `podman exec jordylab-be-keycloak-1 …` with `--server http://localhost:8080`
(no `/auth` path locally).

The scope mapping for `jordylab-backend` (same session, after `config credentials`):

```bash
ID=$(kcadm.sh get clients -r jordylab -q clientId=jordylab-backend --fields id --config /tmp/kc.cfg | sed -n 's/.*"id" : "\(.*\)".*/\1/p')
RM=$(kcadm.sh get clients -r jordylab -q clientId=realm-management --fields id --config /tmp/kc.cfg | sed -n 's/.*"id" : "\(.*\)".*/\1/p')
ROLES=$(for r in view-users manage-users view-roles; do kcadm.sh get clients/$RM/roles/$r -r jordylab --fields id,name --config /tmp/kc.cfg; done | tr -d '\n' | sed 's/} *{/},{/g')
kcadm.sh create clients/$ID/scope-mappings/clients/$RM -r jordylab -b "[$ROLES]" --config /tmp/kc.cfg
```

The `jordylab-mobile` and `mobile-release-ci` clients (spec 007) are **not** in this file — they
are their own stop-and-report gate per `jordylab-be/AGENTS.md`, requiring Jordy's sign-off on the
Android application id before they're added.
