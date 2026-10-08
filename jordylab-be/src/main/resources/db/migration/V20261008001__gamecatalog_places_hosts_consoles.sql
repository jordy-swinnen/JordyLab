CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 013, migration 1 of 3: one game, many places.
-- Hosts (a machine with an optional display name), consoles (registered by the admin), console game entries, the
-- platform and ROM status of each installed copy, and the retirement of the virtual "Nintendo Switch" scan source.
-- The pgvector extension is created in the public schema so its types resolve for every schema; in production it is
-- provisioned by the cluster (a CNPG Database resource) before this runs, which makes the statement a no-op there.
CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;

-- ---------------------------------------------------------------- hosts
CREATE TABLE host (
    id           UUID PRIMARY KEY,
    hostname     TEXT NOT NULL,
    display_name TEXT,
    created_at   TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_host_hostname_ci ON host (lower(hostname));
CREATE UNIQUE INDEX uq_host_display_name_ci ON host (lower(display_name)) WHERE display_name IS NOT NULL;

-- ---------------------------------------------------------------- consoles
CREATE TABLE console (
    id         UUID PRIMARY KEY,
    platform   TEXT NOT NULL,
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_console_name_ci ON console (lower(name));

CREATE TABLE console_game_entry (
    id         UUID PRIMARY KEY,
    game_id    UUID NOT NULL REFERENCES game (id),
    console_id UUID NOT NULL REFERENCES console (id),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_console_game_entry UNIQUE (game_id, console_id)
);

CREATE INDEX idx_console_game_entry_console ON console_game_entry (console_id);
CREATE INDEX idx_console_game_entry_game ON console_game_entry (game_id);

-- ---------------------------------------------------------------- the virtual Switch source becomes a console
-- Only when it actually holds games: an empty virtual source does not create a console nobody asked for.
INSERT INTO console (id, platform, name, created_at, updated_at)
SELECT gen_random_uuid(), 'Nintendo Switch', 'Nintendo Switch', now(), now()
WHERE EXISTS (SELECT 1
              FROM game_installation gi
                       JOIN scan_source s ON s.id = gi.source_id
              WHERE s.source_type = 'SWITCH');

INSERT INTO console_game_entry (id, game_id, console_id, created_at, updated_at)
SELECT gen_random_uuid(), gi.game_id, (SELECT c.id FROM console c WHERE c.name = 'Nintendo Switch'), now(), now()
FROM game_installation gi
         JOIN scan_source s ON s.id = gi.source_id
WHERE s.source_type = 'SWITCH'
ON CONFLICT (game_id, console_id) DO NOTHING;

-- The old Switch flow gave every Switch game an OWNED library entry so it would show up. Library entries are Steam
-- library membership only; a console entry now makes the game visible, so these entries would wrongly label the
-- games "Steam (Owned)".
DELETE FROM game_library_entry
WHERE game_id IN (SELECT game_id FROM console_game_entry)
  AND game_id IN (SELECT id FROM game WHERE steam_app_id IS NULL);

DELETE FROM game_installation
WHERE source_id IN (SELECT id FROM scan_source WHERE source_type = 'SWITCH');
DELETE FROM sync_report
WHERE source_id IN (SELECT id FROM scan_source WHERE source_type = 'SWITCH');
DELETE FROM scan_source WHERE source_type = 'SWITCH';

-- Physical or digital is no longer tracked anywhere (013 US6).
ALTER TABLE game_installation DROP CONSTRAINT IF EXISTS chk_manual_installation_requires_format;
DROP INDEX IF EXISTS idx_game_installation_manual_format;
ALTER TABLE game_installation DROP COLUMN manual;
ALTER TABLE game_installation DROP COLUMN format;

-- ---------------------------------------------------------------- scan sources point at a host
INSERT INTO host (id, hostname, created_at, updated_at)
SELECT gen_random_uuid(), min(hostname), now(), now()
FROM scan_source
GROUP BY lower(hostname);

ALTER TABLE scan_source ADD COLUMN host_id UUID REFERENCES host (id);
UPDATE scan_source s SET host_id = h.id FROM host h WHERE lower(s.hostname) = lower(h.hostname);
ALTER TABLE scan_source ALTER COLUMN host_id SET NOT NULL;
ALTER TABLE scan_source DROP CONSTRAINT IF EXISTS uq_scan_source_host_type;
ALTER TABLE scan_source DROP COLUMN hostname;
CREATE UNIQUE INDEX uq_scan_source_host_type ON scan_source (host_id, source_type);
CREATE INDEX idx_scan_source_host ON scan_source (host_id);

-- ---------------------------------------------------------------- each installed copy knows its platform and ROM status
-- platform is filled from the game's platform now and made NOT NULL in migration 3, after the Java migration has
-- canonicalised the names; rom_status exists only for copies on an emulation source.
ALTER TABLE game_installation ADD COLUMN platform TEXT;
UPDATE game_installation gi SET platform = g.platform FROM game g WHERE g.id = gi.game_id;

ALTER TABLE game_installation ADD COLUMN rom_status TEXT;
UPDATE game_installation gi SET rom_status = 'UNKNOWN'
FROM scan_source s WHERE s.id = gi.source_id AND s.source_type = 'EMUDECK';

-- ---------------------------------------------------------------- identity helpers on game (title_key is filled by migration 2)
ALTER TABLE game ADD COLUMN title_key TEXT;
ALTER TABLE game ADD COLUMN facts_checked_at TIMESTAMPTZ;
ALTER TABLE game ADD COLUMN artwork_checked_at TIMESTAMPTZ;
