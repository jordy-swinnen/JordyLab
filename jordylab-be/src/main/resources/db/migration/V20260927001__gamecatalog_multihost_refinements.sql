CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 004: multi-host model. Per-host state (external ref, presence, seen/grace) moves off
-- `game` into `game_installation`; `game` becomes the host-independent catalog entry.

CREATE TABLE game_installation (
    id             UUID PRIMARY KEY,
    game_id        UUID        NOT NULL REFERENCES game (id),
    source_id      UUID        NOT NULL REFERENCES scan_source (id),
    external_ref   TEXT        NOT NULL,
    presence       TEXT        NOT NULL DEFAULT 'INSTALLED',
    first_seen_at  TIMESTAMPTZ NOT NULL,
    last_seen_at   TIMESTAMPTZ NOT NULL,
    uninstalled_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ,
    CONSTRAINT uq_game_installation_source_ref UNIQUE (source_id, external_ref)
);

CREATE INDEX idx_game_installation_game ON game_installation (game_id);
CREATE INDEX idx_game_installation_source ON game_installation (source_id);
CREATE INDEX idx_game_installation_presence_uninstalled ON game_installation (presence, uninstalled_at);

-- Backfill: one installation per existing game row. (game.source_id, game.external_ref) was
-- unique by constraint, so the unique key below cannot collide.
INSERT INTO game_installation (id, game_id, source_id, external_ref, presence,
                               first_seen_at, last_seen_at, uninstalled_at)
SELECT gen_random_uuid(), id, source_id, external_ref, presence,
       first_seen_at, last_seen_at, uninstalled_at
FROM game;

-- Reshape `game`: deterministic metadata, two artwork slots, deterministic Steam identity.
ALTER TABLE game ADD COLUMN steam_app_id TEXT;
ALTER TABLE game ADD COLUMN genres TEXT;
ALTER TABLE game ADD COLUMN developer TEXT;
ALTER TABLE game ADD COLUMN publisher TEXT;
ALTER TABLE game ADD COLUMN release_year INTEGER CHECK (release_year BETWEEN 1950 AND 2028);
ALTER TABLE game ADD COLUMN metadata_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE game ADD COLUMN metadata_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE game ADD COLUMN cover_status TEXT;
ALTER TABLE game ADD COLUMN cover_ref TEXT;
ALTER TABLE game ADD COLUMN banner_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE game ADD COLUMN banner_ref TEXT;

UPDATE game SET cover_status = artwork_status, cover_ref = artwork_ref;

-- Steam covers resolved to the old 460x215 header must re-resolve to portrait library art.
UPDATE game SET cover_status = 'PENDING', cover_ref = NULL
WHERE cover_status = 'EXTERNAL_URL' AND platform = 'Steam';

-- Deterministic Steam identity for cross-host adoption + artwork/metadata lookups.
UPDATE game SET steam_app_id = external_ref WHERE platform = 'Steam';

-- Indexes that depend on columns being moved off `game` must go before the columns.
DROP INDEX IF EXISTS idx_game_presence_platform;
DROP INDEX IF EXISTS idx_game_presence_uninstalled_at;

-- Legacy single-source state now lives on game_installation.
ALTER TABLE game DROP COLUMN source_id;
ALTER TABLE game DROP COLUMN external_ref;
ALTER TABLE game DROP COLUMN presence;
ALTER TABLE game DROP COLUMN first_seen_at;
ALTER TABLE game DROP COLUMN last_seen_at;
ALTER TABLE game DROP COLUMN uninstalled_at;
ALTER TABLE game DROP COLUMN artwork_status;
ALTER TABLE game DROP COLUMN artwork_ref;

CREATE INDEX idx_game_steam_app_id ON game (steam_app_id) WHERE steam_app_id IS NOT NULL;
CREATE INDEX idx_game_metadata_pending ON game (metadata_status) WHERE metadata_status = 'PENDING';
