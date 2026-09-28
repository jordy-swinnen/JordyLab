CREATE SCHEMA IF NOT EXISTS mobile;
SET search_path TO mobile;

-- 007: one row per published, signed Android release (data-model.md).

CREATE TABLE mobile_release
(
    id                          UUID PRIMARY KEY,
    version_name                TEXT        NOT NULL,
    version_code                INTEGER     NOT NULL,
    release_notes               TEXT        NOT NULL,
    sha256                      TEXT        NOT NULL,
    size_bytes                  BIGINT      NOT NULL,
    min_supported_version_code  INTEGER     NOT NULL,
    storage_key                 TEXT        NOT NULL,
    published_at                TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ,
    CONSTRAINT uq_mobile_release_version_code UNIQUE (version_code)
);
