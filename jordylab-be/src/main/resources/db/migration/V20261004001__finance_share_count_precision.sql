CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance;

-- Share counts kept 4 decimals, so a crypto position such as 0.002106 BTC was stored as 0.0021 (spec 011 BUG-058).
-- Widening the scale never changes an existing value; the integer part keeps 10 digits.
ALTER TABLE portfolio_position ALTER COLUMN share_count TYPE NUMERIC(20, 10);
