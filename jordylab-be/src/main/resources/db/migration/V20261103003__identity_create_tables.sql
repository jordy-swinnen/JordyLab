-- Identity module (spec 019): accounts, app grants, passkey credentials, password-reset requests,
-- machine tokens and the Spring Session JDBC tables. Replaces what Keycloak stored in the keycloak
-- schema; migrated users keep their Keycloak subject as the account id.
CREATE SCHEMA IF NOT EXISTS identity;

SET search_path TO identity;

CREATE TABLE user_account
(
    id              UUID PRIMARY KEY,
    username        TEXT        NOT NULL,
    display_name    TEXT,
    password_hash   TEXT,
    role            TEXT,
    status          TEXT        NOT NULL,
    failed_attempts INTEGER     NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    CONSTRAINT uq_user_account_username UNIQUE (username),
    CONSTRAINT chk_user_account_role CHECK (role IS NULL OR role IN ('ADMIN', 'GUEST')),
    CONSTRAINT chk_user_account_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED'))
);

CREATE TABLE user_app_grant
(
    user_account_id UUID NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    app             TEXT NOT NULL,
    CONSTRAINT pk_user_app_grant PRIMARY KEY (user_account_id, app),
    CONSTRAINT chk_user_app_grant_app CHECK (app IN ('GAME_CATALOG'))
);

-- Spring Security's JdbcUserCredentialRepository contract (spring-security-webauthn 7.1.1,
-- verified against the jar's SQL: keyed by credential_id and user_entity_user_id, with the
-- WebAuthn flags and attestation material; only public-key material is stored). created and
-- last_used are required by the row mapper.
CREATE TABLE user_credentials
(
    credential_id                VARCHAR(1000) NOT NULL PRIMARY KEY,
    user_entity_user_id          BYTEA         NOT NULL,
    public_key                   BYTEA         NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN,
    authenticator_transports     TEXT,
    public_key_credential_type   TEXT,
    backup_state                 BOOLEAN,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMPTZ   NOT NULL,
    last_used                    TIMESTAMPTZ   NOT NULL,
    label                        VARCHAR(255)
);

CREATE INDEX ix_user_credentials_user_entity_user_id ON user_credentials (user_entity_user_id);

CREATE TABLE password_reset_request
(
    id                      UUID PRIMARY KEY,
    user_account_id         UUID        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    requested_password_hash TEXT        NOT NULL,
    status                  TEXT        NOT NULL,
    decided_at              TIMESTAMPTZ,
    decided_by              UUID,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,
    CONSTRAINT chk_password_reset_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- At most one pending request per account (spec 019 D15).
CREATE UNIQUE INDEX uq_password_reset_request_pending
    ON password_reset_request (user_account_id) WHERE status = 'PENDING';

CREATE TABLE machine_token
(
    id           UUID PRIMARY KEY,
    name         TEXT        NOT NULL,
    token_hash   TEXT        NOT NULL,
    scope        TEXT        NOT NULL,
    created_by   UUID        NOT NULL REFERENCES user_account (id),
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ,
    CONSTRAINT uq_machine_token_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_machine_token_scope CHECK (scope IN ('INGEST'))
);

-- Spring Session JDBC (spec 019 D3). Auto-DDL stays off; these are the module's standard
-- PostgreSQL tables, schema-qualified via spring.session.jdbc.table-name-prefix=identity.
CREATE TABLE spring_session
(
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT   NOT NULL,
    last_access_time      BIGINT   NOT NULL,
    max_inactive_interval INTEGER  NOT NULL,
    expiry_time           BIGINT   NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes
(
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
