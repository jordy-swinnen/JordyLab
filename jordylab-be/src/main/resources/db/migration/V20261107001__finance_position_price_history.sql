CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance;

-- One euro value per price symbol per trading day (spec 023, research section 3). The backfill writes ten years of daily
-- closes; each refresh cycle writes today's row, so the newest point equals the price the Portfolio page shows. Keyed by the
-- Yahoo symbol, so positions that share a symbol share rows.
CREATE TABLE IF NOT EXISTS position_price_history (
    id            UUID PRIMARY KEY,
    price_symbol  VARCHAR(30) NOT NULL,
    price_date    DATE NOT NULL,
    price_in_euro NUMERIC(12,4) NOT NULL CHECK (price_in_euro > 0),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uq_position_price_history_symbol_date UNIQUE (price_symbol, price_date)
);
