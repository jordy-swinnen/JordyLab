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
| 2026-09-30 | `jordylab-backend` gets a client scope mapping for `realm-management` → `view-users`, `manage-users`, `view-realm` | The client has `fullScopeAllowed=false`, so without the mapping its service-account token carried none of these roles and the Admin REST API answered 403 (Settings → Users, spec 011 BUG-031). |
| 2026-09-30 | `jordylab-backend` service account + scope mapping: `view-realm` instead of `view-roles` | `view-roles` is not a built-in `realm-management` role — the import created an empty custom role. Reading a realm role (`GET /roles/guest`, needed to approve a user) requires `view-realm`; without it approve/revoke answered 403 (spec 011 BUG-033). Remove the stray `view-roles` client role afterwards. |
| 2026-09-30 | New role `mobile-release-publisher`; clients `jordylab-mobile` and `mobile-release-ci`; the CI service account gets the role (scope-mapped) | Android release pipeline and native login (spec 007 D13). Needs the `MOBILE_RELEASE_CI_CLIENT_SECRET` env var in the Keycloak pod (from `jordylab-secrets`), so run it after the deploy that wires it. |
| 2026-10-01 | Required action `UPDATE_EMAIL` enabled | Users change their own email from the account menu (006 US5 / FR-008, spec 011 BUG-011). With email as username, Keycloak 26.4+ only lets users change email through this action (supported feature since 26.4). **Not in the realm files**: listing any `requiredActions` in an import replaces Keycloak's defaults, so re-run this after a fresh import too. Command below. |
| 2026-10-04 | Realm `ssoSessionIdleTimeout` 30 min → 30 days, `ssoSessionMaxLifespan` → 90 days (access token stays 30 min, refreshed silently) | The owner was signed out far too often: the web app restores its session from the SSO cookie, which expired after 30 idle minutes. Applied live with `kcadm.sh update realms/jordylab -s ssoSessionIdleTimeout=2592000 -s ssoSessionMaxLifespan=7776000` and mirrored in both realm files. |

Enable `UPDATE_EMAIL` (same session, after `config credentials`; check with `get authentication/required-actions/UPDATE_EMAIL`):

```bash
kcadm.sh update authentication/required-actions/UPDATE_EMAIL -r jordylab -s enabled=true --config /tmp/kc.cfg
```

For the local Podman Keycloak use `podman exec jordylab-be-keycloak-1 …` with `--server http://localhost:8080`
(no `/auth` path locally).

The scope mapping for `jordylab-backend` (same session, after `config credentials`):

```bash
ID=$(kcadm.sh get clients -r jordylab -q clientId=jordylab-backend --fields id --config /tmp/kc.cfg | sed -n 's/.*"id" : "\(.*\)".*/\1/p')
RM=$(kcadm.sh get clients -r jordylab -q clientId=realm-management --fields id --config /tmp/kc.cfg | sed -n 's/.*"id" : "\(.*\)".*/\1/p')
ROLES=$(for r in view-users manage-users view-realm; do kcadm.sh get clients/$RM/roles/$r -r jordylab --fields id,name --config /tmp/kc.cfg; done | tr -d '\n' | sed 's/} *{/},{/g')
kcadm.sh create clients/$ID/scope-mappings/clients/$RM -r jordylab -b "[$ROLES]" --config /tmp/kc.cfg
```

The `jordylab-mobile` (public, PKCE, App Link callback) and `mobile-release-ci` (CI publisher,
`${MOBILE_RELEASE_CI_CLIENT_SECRET}`) clients and the `mobile-release-publisher` role were added on
2026-09-30 after Jordy signed off on the Android application id `be.jordylab.app` (spec 007 D13/D14,
spec 011 Q-06).

The mobile clients (same session; `$MOBILE_RELEASE_CI_CLIENT_SECRET` is already in the pod's environment):

```bash
kcadm.sh create roles -r jordylab -s name=mobile-release-publisher -s 'description=Publish Android releases — CI identity only' --config /tmp/kc.cfg
kcadm.sh create clients -r jordylab --config /tmp/kc.cfg -s clientId=jordylab-mobile -s publicClient=true -s standardFlowEnabled=true -s directAccessGrantsEnabled=false -s 'redirectUris=["https://jordylab.be/mobile/callback"]' -s 'webOrigins=["https://localhost"]' -s 'attributes."pkce.code.challenge.method"=S256' -s 'attributes."post.logout.redirect.uris"=https://jordylab.be/mobile/*'
kcadm.sh create clients -r jordylab --config /tmp/kc.cfg -s clientId=mobile-release-ci -s publicClient=false -s serviceAccountsEnabled=true -s standardFlowEnabled=false -s directAccessGrantsEnabled=false -s fullScopeAllowed=false -s "secret=$MOBILE_RELEASE_CI_CLIENT_SECRET"
kcadm.sh add-roles -r jordylab --uusername service-account-mobile-release-ci --rolename mobile-release-publisher --config /tmp/kc.cfg
CI=$(kcadm.sh get clients -r jordylab -q clientId=mobile-release-ci --fields id --config /tmp/kc.cfg | sed -n 's/.*"id" : "\(.*\)".*/\1/p')
kcadm.sh create clients/$CI/scope-mappings/realm -r jordylab -b "[$(kcadm.sh get roles/mobile-release-publisher -r jordylab --fields id,name --config /tmp/kc.cfg)]" --config /tmp/kc.cfg
```
