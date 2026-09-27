CREATE SCHEMA IF NOT EXISTS settings;
SET
search_path TO settings;

-- 006: the admin's model choice per AI feature, the last call outcome per feature,
-- and the per-guest daily chat budget.

CREATE TABLE ai_feature_model_setting
(
    id                 UUID PRIMARY KEY,
    feature_key        TEXT NOT NULL,
    model_id           TEXT NOT NULL,
    updated_by_subject TEXT NOT NULL,
    created_at         TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ,
    CONSTRAINT uq_ai_feature_model_setting_feature_key UNIQUE (feature_key)
);

CREATE TABLE ai_feature_last_run
(
    id             UUID PRIMARY KEY,
    feature_key    TEXT        NOT NULL,
    provider       TEXT        NOT NULL,
    model          TEXT        NOT NULL,
    fallback_used  BOOLEAN     NOT NULL DEFAULT FALSE,
    outcome        TEXT        NOT NULL,
    failure_reason TEXT,
    ran_at         TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ,
    CONSTRAINT uq_ai_feature_last_run_feature_key UNIQUE (feature_key)
);

CREATE TABLE guest_chat_usage
(
    id            UUID PRIMARY KEY,
    user_subject  TEXT    NOT NULL,
    usage_date    DATE    NOT NULL,
    message_count INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uq_guest_chat_usage_subject_date UNIQUE (user_subject, usage_date)
);
