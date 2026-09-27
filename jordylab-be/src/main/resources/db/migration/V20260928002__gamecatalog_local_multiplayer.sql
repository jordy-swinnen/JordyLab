CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 006: deterministic local-multiplayer metadata, derived from Steam store categories and
-- IGDB multiplayer modes instead of LLM inference. `multiplayer_source` records provenance
-- (steam | igdb | unknown) so unresolved games can be re-checked later without re-querying
-- everything; `multiplayer_attempts` parks games that never resolve (mirrors the metadata
-- retry pattern) so the backlog cannot spin.

ALTER TABLE game ADD COLUMN local_multiplayer BOOLEAN;
ALTER TABLE game ADD COLUMN split_screen BOOLEAN;
ALTER TABLE game ADD COLUMN multiplayer_source TEXT NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE game ADD COLUMN multiplayer_attempts INTEGER NOT NULL DEFAULT 0;

-- Every shown player count must be provably deterministic: drop the legacy AI-guessed values.
-- They are re-derived from Steam categories (Steam games) or IGDB (ROMs / fallback) on the next pass.
UPDATE game SET max_local_players = NULL;

CREATE INDEX idx_game_multiplayer_backlog ON game (multiplayer_source, multiplayer_attempts)
    WHERE multiplayer_source = 'UNKNOWN' AND multiplayer_attempts < 3;
