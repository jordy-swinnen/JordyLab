CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog, public;

-- 013, migration 3 of 3: after the Java migration has canonicalised platforms, filled title_key and merged duplicates.

-- ---------------------------------------------------------------- identity
ALTER TABLE game ALTER COLUMN title_key SET NOT NULL;
CREATE INDEX idx_game_title_key ON game (title_key);

ALTER TABLE game_installation ALTER COLUMN platform SET NOT NULL;

-- A game has no single platform any more: platforms come from its places.
DROP INDEX IF EXISTS uq_game_platform_igdb_game_id;
ALTER TABLE game DROP COLUMN platform;
-- An IGDB id identifies a game across platforms.
CREATE UNIQUE INDEX uq_game_igdb_game_id ON game (igdb_game_id) WHERE igdb_game_id IS NOT NULL;

-- ---------------------------------------------------------------- who wrote the description
ALTER TABLE game ADD COLUMN description_source TEXT;
ALTER TABLE game ADD COLUMN description_model TEXT;
ALTER TABLE game ADD COLUMN description_requested_model TEXT;
ALTER TABLE game ADD COLUMN description_written_at TIMESTAMPTZ;

UPDATE game SET description_source = 'AI' WHERE enrichment_status = 'ENRICHED' AND description IS NOT NULL;
UPDATE game SET description_source = 'STEAM' WHERE description_source IS NULL AND description IS NOT NULL;

-- ---------------------------------------------------------------- marks: one per user per game
CREATE TABLE game_mark (
    id           UUID PRIMARY KEY,
    game_id      UUID NOT NULL REFERENCES game (id) ON DELETE CASCADE,
    user_subject TEXT NOT NULL,
    mark         TEXT NOT NULL CHECK (mark IN ('WANT_TO_PLAY', 'PLAYED_LIKED', 'PLAYED_DISLIKED')),
    created_at   TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ,
    CONSTRAINT uq_game_mark_game_user UNIQUE (game_id, user_subject)
);

CREATE INDEX idx_game_mark_game_mark ON game_mark (game_id, mark);
CREATE INDEX idx_game_mark_user ON game_mark (user_subject);

-- ---------------------------------------------------------------- admin bulk refresh runs
CREATE TABLE refresh_run (
    id              UUID PRIMARY KEY,
    kind            TEXT        NOT NULL CHECK (kind IN ('DATA', 'AI')),
    status          TEXT        NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'STOPPED', 'INTERRUPTED')),
    total           INTEGER     NOT NULL DEFAULT 0,
    processed       INTEGER     NOT NULL DEFAULT 0,
    failed          INTEGER     NOT NULL DEFAULT 0,
    stop_requested  BOOLEAN     NOT NULL DEFAULT FALSE,
    failure_summary TEXT,
    started_by      TEXT        NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ
);

-- At most one running refresh per kind.
CREATE UNIQUE INDEX uq_refresh_run_running_per_kind ON refresh_run (kind) WHERE status = 'RUNNING';
CREATE INDEX idx_refresh_run_kind_started ON refresh_run (kind, started_at DESC);

-- ---------------------------------------------------------------- the semantic index (LibBot)
CREATE TABLE game_embedding (
    game_id      UUID PRIMARY KEY REFERENCES game (id) ON DELETE CASCADE,
    model        TEXT         NOT NULL,
    content_hash TEXT         NOT NULL,
    embedding    vector(1536) NOT NULL,
    embedded_at  TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ
);

CREATE INDEX idx_game_embedding_cosine ON game_embedding USING hnsw (embedding vector_cosine_ops);
