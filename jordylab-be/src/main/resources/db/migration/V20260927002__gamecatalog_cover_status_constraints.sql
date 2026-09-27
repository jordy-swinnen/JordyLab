CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 004 follow-up: cover_status was added nullable when the slot was split from artwork_status.
-- Restore the NOT NULL DEFAULT 'PENDING' invariant the old artwork_status column carried, so the
-- schema matches Game.GameBuilder (which always defaults the slot to PENDING).
ALTER TABLE game ALTER COLUMN cover_status SET NOT NULL;
ALTER TABLE game ALTER COLUMN cover_status SET DEFAULT 'PENDING';
