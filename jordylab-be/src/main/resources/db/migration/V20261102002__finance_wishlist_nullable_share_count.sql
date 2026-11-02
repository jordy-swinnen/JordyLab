CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance;

-- Wishlist stocks are tracked without owning them (spec 015): a row with share_count IS NULL is a wishlist item,
-- a row with a positive share_count is a held position. Keeping both in one table gives one unique ticker across
-- both lists and lets a promotion keep the already-resolved price symbol and cached price.
ALTER TABLE portfolio_position ALTER COLUMN share_count DROP NOT NULL;

ALTER TABLE portfolio_position ADD CONSTRAINT chk_portfolio_position_share_count_positive
    CHECK (share_count IS NULL OR share_count > 0);
