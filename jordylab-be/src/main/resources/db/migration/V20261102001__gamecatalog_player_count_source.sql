CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 014: the source of a player count is kept apart from the source of the multiplayer flags, so a Steam-flagged game
-- whose count came from IGDB no longer reads as "Steam". `player_count_gap` records why the second source left a
-- console game without a count (NO_MATCH, ENTRIES_DISAGREE, COUNT_BELOW_TWO, CONSOLE_NOT_LISTED).
ALTER TABLE game ADD COLUMN max_local_players_source TEXT;
ALTER TABLE game ADD COLUMN player_count_gap TEXT;

-- Only IGDB has ever written a count: Steam passes none and the AI-guessed values were removed earlier.
UPDATE game SET max_local_players_source = 'IGDB' WHERE max_local_players IS NOT NULL;
