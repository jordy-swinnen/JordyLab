CREATE SCHEMA IF NOT EXISTS settings;
SET
search_path TO settings;

-- 014: the admin's guest chat budget. A row with no user_subject is the saved default (at most one); a row with a
-- user_subject is one person's personal override, which replaces the default for that person only.
-- Saved values are stored as given: 0 is a real limit that blocks chat, unlike the environment default, which
-- SettingsProperties coerces back to 20.
CREATE TABLE guest_chat_limit
(
    id                 UUID PRIMARY KEY,
    user_subject       TEXT,
    daily_limit        INTEGER     NOT NULL CHECK (daily_limit >= 0),
    updated_by_subject TEXT        NOT NULL,
    created_at         TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ,
    CONSTRAINT uq_guest_chat_limit_user_subject UNIQUE (user_subject)
);

-- At most one saved default row. NULLs are distinct under a plain unique constraint, so the default needs its own
-- partial unique index on a constant expression.
CREATE UNIQUE INDEX uq_guest_chat_limit_one_default ON guest_chat_limit ((true)) WHERE user_subject IS NULL;
