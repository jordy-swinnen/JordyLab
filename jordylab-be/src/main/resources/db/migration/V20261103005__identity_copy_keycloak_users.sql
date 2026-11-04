-- Release A follow-up (spec 019 US7, D10). The 004 copy checked `information_schema.schemata`, which hides a schema the app
-- role has no privilege on — on the Compose database the `keycloak` schema is owned by its own role, so 004 saw nothing and
-- inserted 0 rows. The app role now has USAGE + SELECT on the cutover tables (granted by the operator as part of release A);
-- this migration uses `pg_namespace`, which every role can see, and repeats the idempotent copy. It no-ops on databases
-- without the schema (fresh installs, e2e) and where the rows already exist.
DO $$
DECLARE
    migrated_accounts INTEGER;
    migrated_grants   INTEGER;
    jordylab_realm    VARCHAR(255);
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'keycloak') THEN
        RAISE NOTICE 'Keycloak schema absent; no users to migrate.';
        RETURN;
    END IF;

    SELECT id INTO jordylab_realm FROM keycloak.realm WHERE name = 'jordylab';
    IF jordylab_realm IS NULL THEN
        RAISE NOTICE 'Keycloak realm jordylab absent; no users to migrate.';
        RETURN;
    END IF;

    INSERT INTO identity.user_account (id, username, display_name, password_hash, role, status, failed_attempts,
                                       created_at, updated_at)
    SELECT person.id::uuid,
           lower(person.username),
           NULLIF(btrim(concat_ws(' ', person.first_name, person.last_name)), ''),
           NULL,
           CASE
               WHEN EXISTS (SELECT 1
                            FROM keycloak.user_role_mapping mapping
                                     JOIN keycloak.keycloak_role role ON role.id = mapping.role_id
                            WHERE mapping.user_id = person.id
                              AND role.realm_id = jordylab_realm
                              AND role.name = 'admin') THEN 'ADMIN'
               WHEN EXISTS (SELECT 1
                            FROM keycloak.user_role_mapping mapping
                                     JOIN keycloak.keycloak_role role ON role.id = mapping.role_id
                            WHERE mapping.user_id = person.id
                              AND role.realm_id = jordylab_realm
                              AND role.name = 'guest') THEN 'GUEST'
               ELSE NULL
               END,
           'APPROVED',
           0,
           to_timestamp(person.created_timestamp / 1000.0),
           now()
    FROM keycloak.user_entity person
    WHERE person.realm_id = jordylab_realm
      AND person.service_account_client_link IS NULL
      AND NOT EXISTS (SELECT 1
                      FROM identity.user_account existing
                      WHERE existing.id = person.id::uuid
                         OR existing.username = lower(person.username))
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS migrated_accounts = ROW_COUNT;

    INSERT INTO identity.user_app_grant (user_account_id, app)
    SELECT DISTINCT account.id, 'GAME_CATALOG'
    FROM keycloak.user_entity person
             JOIN identity.user_account account
                  ON account.id = person.id::uuid OR account.username = lower(person.username)
    WHERE person.realm_id = jordylab_realm
      AND person.service_account_client_link IS NULL
      AND (EXISTS (SELECT 1
                   FROM keycloak.user_role_mapping mapping
                            JOIN keycloak.keycloak_role role ON role.id = mapping.role_id
                   WHERE mapping.user_id = person.id
                     AND role.realm_id = jordylab_realm
                     AND role.name IN ('app-gamecatalog', 'admin')))
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS migrated_grants = ROW_COUNT;

    RAISE NOTICE 'Keycloak cutover (follow-up): migrated % account(s) and % app grant(s).',
        migrated_accounts, migrated_grants;
END $$;
