CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 009: manual Nintendo Switch game tracking. Games can be matched to IGDB (platform 130) or
-- entered as custom titles; both are protected from scanner overwrites by TitleSource.MANUAL.

-- IGDB identity for Switch games. Nullable so custom manual entries can exist without a match;
-- a unique partial index prevents duplicate IGDB matches on the same platform.
ALTER TABLE game ADD COLUMN igdb_game_id TEXT;
CREATE UNIQUE INDEX uq_game_platform_igdb_game_id ON game (platform, igdb_game_id)
    WHERE igdb_game_id IS NOT NULL;

-- Manual installations carry a physical/digital format and are never subject to scan-based
-- reconciliation or purge. The virtual Switch source is identified by source_type = 'SWITCH'.
ALTER TABLE game_installation ADD COLUMN manual BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE game_installation ADD COLUMN format TEXT;

-- A manual installation must always declare its format; scanner-created installations leave
-- format null.
ALTER TABLE game_installation ADD CONSTRAINT chk_manual_installation_requires_format
    CHECK (manual = FALSE OR format IS NOT NULL);

CREATE INDEX idx_game_installation_manual_format ON game_installation (manual, format)
    WHERE manual = TRUE;

-- Well-known virtual source for the Nintendo Switch host. It is never created or adopted by
-- scan clients (source_type = 'SWITCH'), so its installations are invisible to scan flows.
INSERT INTO scan_source (id, source_key, hostname, source_type, platform, enabled,
                         machine_id, created_at, updated_at)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Nintendo Switch', 'Nintendo Switch', 'SWITCH',
        'Nintendo Switch', true, 'manual', now(), now())
ON CONFLICT (source_key) DO NOTHING;
