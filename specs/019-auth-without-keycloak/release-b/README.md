# Release B — shipped 2026-11-05

The drop shipped as `V20261103006__drop_keycloak_schema.sql`; the original staged copy here was the source. Release A
created `identity`, copied the persons and `app-gamecatalog` grants, and kept Keycloak idle for rollback. Release B removed
the Keycloak runtime and dropped the schema.

## Compose production prerequisites (already applied)

The Compose app role is not the owner of the `keycloak` schema, so the operator ran these as the Postgres superuser before
the cutover:

```sql
GRANT CREATE ON DATABASE jordylab TO jordylab;
GRANT USAGE ON SCHEMA keycloak TO jordylab;
GRANT SELECT ON keycloak.realm, keycloak.user_entity, keycloak.keycloak_role, keycloak.user_role_mapping TO jordylab;
ALTER SCHEMA keycloak OWNER TO jordylab;   -- release B: lets the app role drop the schema
```

The remaining cleanup is the unused legacy keys and DB role Secret in `secrets.sops.yaml` / the cluster; nothing reads them.
