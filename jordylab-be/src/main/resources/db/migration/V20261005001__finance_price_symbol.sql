CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance;

-- The Yahoo symbol a position is priced with, resolved from what the user typed (BTC -> BTC-EUR, MEUD -> MEUD.PA).
-- Nullable: positions that were typed with an exact symbol, or not resolved yet, are resolved on the next refresh.
ALTER TABLE portfolio_position ADD COLUMN IF NOT EXISTS price_symbol VARCHAR(30);
