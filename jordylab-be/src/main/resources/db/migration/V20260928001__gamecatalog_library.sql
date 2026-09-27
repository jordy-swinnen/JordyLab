CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 005: Steam library membership. A game becomes visible when it is installed OR in a
-- library (owned / family). Steam identity must be unique so concurrent sources cannot
-- create duplicate games (and duplicate AI work).

-- Guard: enforce uniqueness only if the existing data already satisfies it.
DO $$
DECLARE duplicate_groups INTEGER;
BEGIN
    SELECT COUNT(*) INTO duplicate_groups FROM (
        SELECT steam_app_id
        FROM game
        WHERE steam_app_id IS NOT NULL
        GROUP BY steam_app_id
        HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION 'Cannot create unique index uq_game_steam_app_id: % duplicate steam_app_id group(s) exist. Resolve them before migrating.', duplicate_groups;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_game_steam_app_id;
CREATE UNIQUE INDEX uq_game_steam_app_id ON game (steam_app_id) WHERE steam_app_id IS NOT NULL;

-- Title authority: LIBRARY > MANIFEST = ROM. A lower-authority source never overwrites.
ALTER TABLE game ADD COLUMN title_source TEXT;
UPDATE game SET title_source = CASE WHEN platform = 'Steam' THEN 'MANIFEST' ELSE 'ROM' END
WHERE title_source IS NULL;

-- One library entry per game per library source (OWNED | FAMILY).
CREATE TABLE game_library_entry (
    id                 UUID PRIMARY KEY,
    game_id            UUID        NOT NULL REFERENCES game (id),
    library_source     TEXT        NOT NULL,
    first_seen_at      TIMESTAMPTZ NOT NULL,
    last_seen_at       TIMESTAMPTZ NOT NULL,
    removed_at         TIMESTAMPTZ,
    family_owner_names TEXT,
    created_at         TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ,
    CONSTRAINT uq_game_library_entry_game_source UNIQUE (game_id, library_source)
);

CREATE INDEX idx_game_library_entry_source_removed ON game_library_entry (library_source, removed_at);
CREATE INDEX idx_game_library_entry_game ON game_library_entry (game_id);

-- One row per owned or family sync run, with the cost it caused.
CREATE TABLE library_sync_run (
    id                UUID PRIMARY KEY,
    library_source    TEXT        NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ NOT NULL,
    outcome           TEXT        NOT NULL,
    content_hash      TEXT,
    entries_submitted INTEGER     NOT NULL DEFAULT 0,
    entries_added     INTEGER     NOT NULL DEFAULT 0,
    entries_removed   INTEGER     NOT NULL DEFAULT 0,
    metadata_calls    INTEGER     NOT NULL DEFAULT 0,
    ai_calls          INTEGER     NOT NULL DEFAULT 0,
    error_code        TEXT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ
);

CREATE INDEX idx_library_sync_run_source_finished ON library_sync_run (library_source, finished_at DESC);

-- One-off cleanup: Steam tools/runtimes are not games and must not be catalogued.
DELETE FROM game_installation
WHERE game_id IN (
    SELECT id FROM game WHERE steam_app_id IN ('228980', '1070560', '1391110', '1628350', '1493710')
);
DELETE FROM game WHERE steam_app_id IN ('228980', '1070560', '1391110', '1628350', '1493710');
