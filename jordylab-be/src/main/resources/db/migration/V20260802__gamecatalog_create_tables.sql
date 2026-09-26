CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

CREATE TABLE scan_source (
    id                UUID PRIMARY KEY,
    source_key        TEXT        NOT NULL UNIQUE,
    path              TEXT        NOT NULL,
    source_type       TEXT        NOT NULL,
    platform          TEXT        NOT NULL,
    enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    last_attempt_at   TIMESTAMPTZ,
    last_success_at   TIMESTAMPTZ,
    last_outcome      TEXT,
    last_sequence     BIGINT      NOT NULL DEFAULT 0,
    last_payload_hash TEXT,
    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ
);

CREATE TABLE game (
    id                        UUID PRIMARY KEY,
    source_id                 UUID        NOT NULL REFERENCES scan_source (id),
    platform                  TEXT        NOT NULL,
    external_ref              TEXT        NOT NULL,
    title                     TEXT        NOT NULL,
    genre                     TEXT,
    max_local_players         INTEGER     CHECK (max_local_players BETWEEN 1 AND 64),
    online_multiplayer        BOOLEAN,
    single_player             BOOLEAN,
    description               TEXT,
    enrichment_status         TEXT        NOT NULL DEFAULT 'PENDING',
    enrichment_attempts       INTEGER     NOT NULL DEFAULT 0,
    artwork_status            TEXT        NOT NULL DEFAULT 'PENDING',
    artwork_ref               TEXT,
    artwork_fallback_requests INTEGER     NOT NULL DEFAULT 0,
    presence                  TEXT        NOT NULL DEFAULT 'INSTALLED',
    first_seen_at             TIMESTAMPTZ NOT NULL,
    last_seen_at              TIMESTAMPTZ NOT NULL,
    uninstalled_at            TIMESTAMPTZ,
    created_at                TIMESTAMPTZ,
    updated_at                TIMESTAMPTZ,
    CONSTRAINT uq_game_source_ref UNIQUE (source_id, external_ref)
);

CREATE INDEX idx_game_presence_platform ON game (presence, platform);
CREATE INDEX idx_game_lower_title ON game (lower(title));
CREATE INDEX idx_game_enrichment_pending ON game (enrichment_status) WHERE enrichment_status = 'PENDING';
CREATE INDEX idx_game_presence_uninstalled_at ON game (presence, uninstalled_at);

CREATE TABLE sync_report (
    id              UUID PRIMARY KEY,
    source_id       UUID        NOT NULL REFERENCES scan_source (id),
    received_at     TIMESTAMPTZ NOT NULL,
    sequence        BIGINT      NOT NULL,
    outcome         TEXT        NOT NULL,
    payload_hash    TEXT,
    games_submitted INTEGER     NOT NULL DEFAULT 0,
    games_added     INTEGER     NOT NULL DEFAULT 0,
    games_updated   INTEGER     NOT NULL DEFAULT 0,
    games_removed   INTEGER     NOT NULL DEFAULT 0,
    games_rejected  INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_sync_report_source ON sync_report (source_id, received_at DESC);
