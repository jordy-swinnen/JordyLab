-- Release B of the Keycloak cutover (spec 019 US7, D10). Runs only after release A has copied every person into identity and
-- the owner took the verified backup. No-ops with a notice on databases that never had Keycloak (fresh installs, e2e).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'keycloak') THEN
        RAISE NOTICE 'Dropping the Keycloak schema (release B).';
    ELSE
        RAISE NOTICE 'Keycloak schema absent; nothing to drop.';
    END IF;
END $$;

DROP SCHEMA IF EXISTS keycloak CASCADE;
