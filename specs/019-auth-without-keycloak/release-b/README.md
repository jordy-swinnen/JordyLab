# Release B — after identity is verified

This folder holds the schema drop that must **not** ship with release A. Release A creates `identity` and copies the
persons and `app-gamecatalog` grants from the `keycloak` schema, while Keycloak stays running (idle) for rollback.
Only after the owner verifies the migrated accounts, every person has reset their password and enrolled a passkey, and
the verified backup exists, move `V20261103005__drop_keycloak_schema.sql` into
`jordylab-be/src/main/resources/db/migration/` and ship release B. Release B also removes the Keycloak runtime
artifacts (the Compose service and `/auth` route, the image build, the realm files and the theme).

## Compose production prerequisites

The Compose database's app role (`jordylab`) is not the owner of the `keycloak` schema, so the operator must run these once
(superuser) before release A's copy and again before release B's drop:

```sql
GRANT USAGE ON SCHEMA keycloak TO jordylab;
GRANT SELECT ON keycloak.realm, keycloak.user_entity, keycloak.keycloak_role, keycloak.user_role_mapping TO jordylab;
-- before release B, either hand the schema over or run the drop as the superuser:
ALTER SCHEMA keycloak OWNER TO jordylab;
```
