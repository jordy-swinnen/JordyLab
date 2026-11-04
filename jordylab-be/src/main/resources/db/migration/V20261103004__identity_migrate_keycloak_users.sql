-- Release A of the Keycloak cutover (spec 019 US7, D10). Copies every person from the still-present Keycloak schema into
-- identity, keeping the Keycloak subject as the account id so every owner column and foreign key keeps working. Passwords
-- are deliberately not copied: each migrated person signs in after an admin-approved reset. Service accounts (scanners,
-- CI) are not people and are skipped; the realm itself is limited to `jordylab`. The whole block no-ops with a notice on
-- databases that never had Keycloak (fresh installs, e2e).
--
-- Release B (a later migration, only after the owner-verified backup) drops the `keycloak` schema.
DO $$
DECLARE
    migrated_accounts INTEGER;
    migrated_grants   INTEGER;
    jordylab_realm    VARCHAR(255);
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'keycloak') THEN
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
      -- A username already present in identity (dev databases that ran the new module first, or a re-run) is left alone.
      AND NOT EXISTS (SELECT 1
                      FROM identity.user_account existing
                      WHERE existing.id = person.id::uuid
                         OR existing.username = lower(person.username))
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS migrated_accounts = ROW_COUNT;

    -- The app grant: a direct `app-gamecatalog` realm role, or the `admin` composite that includes it. The grant attaches
    -- by username too, so it lands on an account that already existed under the same name with a different id.
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

    RAISE NOTICE 'Keycloak cutover: migrated % account(s) and % app grant(s).', migrated_accounts, migrated_grants;
END $$;
