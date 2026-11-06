CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- A game the admin removed from the catalog (JOR-97). It keeps its installations and library entries, so a re-scan
-- does not bring it back; the visibility predicate hides it from the grid, search, detail, LibBot and health counts.
ALTER TABLE game ADD COLUMN excluded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE game ADD COLUMN excluded_at TIMESTAMPTZ;

CREATE INDEX idx_game_excluded ON game (excluded) WHERE excluded = TRUE;
