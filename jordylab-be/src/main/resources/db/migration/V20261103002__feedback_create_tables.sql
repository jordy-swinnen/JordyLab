CREATE SCHEMA IF NOT EXISTS feedback;
SET
search_path TO feedback;

-- 017: the idea box. One table: a guest feedback ticket is the aggregate, and the sender is
-- referenced by Keycloak subject only (no cross-schema foreign keys).

CREATE TABLE feedback_ticket
(
    id                  UUID PRIMARY KEY,
    app                 TEXT        NOT NULL,
    type                TEXT        NOT NULL,
    title               TEXT        NOT NULL,
    description         TEXT        NOT NULL,
    sender_subject      TEXT        NOT NULL,
    sender_display_name TEXT,
    state               TEXT        NOT NULL,
    reply               TEXT,
    private_note        TEXT,
    linear_issue_id     TEXT,
    linear_issue_url    TEXT,
    accept_error        TEXT,
    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ
);

-- Serves the guest's own-ticket list and the daily-limit count.
CREATE INDEX idx_feedback_ticket_sender_created ON feedback_ticket (sender_subject, created_at);
