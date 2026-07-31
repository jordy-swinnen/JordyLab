CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance;

CREATE TABLE feed (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(100) NOT NULL,
    url     VARCHAR(500) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE article (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id      UUID NOT NULL REFERENCES feed(id),
    title        VARCHAR(500),
    url          VARCHAR(500) NOT NULL UNIQUE,
    content_hash VARCHAR(64),
    full_content TEXT,
    published_at TIMESTAMPTZ,
    scraped_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE portfolio_position (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker                VARCHAR(20) NOT NULL UNIQUE,
    share_count           NUMERIC(12,4) NOT NULL,
    last_price            NUMERIC(12,4),
    last_price_fetched_at TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE briefing (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generated_at TIMESTAMPTZ NOT NULL,
    content      TEXT NOT NULL,
    model_used   VARCHAR(100)
);
