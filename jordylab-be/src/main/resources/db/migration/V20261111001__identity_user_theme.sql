-- Account-wide theme preference (spec 024 US3, ticket JOR-78 D6).
CREATE SCHEMA IF NOT EXISTS identity;
SET search_path TO identity;

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS theme TEXT NOT NULL DEFAULT 'system';
