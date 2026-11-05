-- MCP module (spec 021): the connector's OAuth 2.1 authorization server. Spring Authorization Server's
-- JDBC services keep authorizations and consents here, so a deploy does not sign Claude out. The one
-- registered client lives in configuration, not in a table. Types follow the library's PostgreSQL note:
-- blob becomes text and timestamp becomes timestamptz.
CREATE SCHEMA IF NOT EXISTS mcp;

SET search_path TO mcp;

CREATE TABLE oauth2_authorization
(
    id                            VARCHAR(100) PRIMARY KEY,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    TEXT          DEFAULT NULL,
    state                         VARCHAR(500)  DEFAULT NULL,
    authorization_code_value      TEXT          DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_expires_at TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_metadata   TEXT          DEFAULT NULL,
    access_token_value            TEXT          DEFAULT NULL,
    access_token_issued_at        TIMESTAMPTZ   DEFAULT NULL,
    access_token_expires_at       TIMESTAMPTZ   DEFAULT NULL,
    access_token_metadata         TEXT          DEFAULT NULL,
    access_token_type             VARCHAR(100)  DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           TEXT          DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_metadata        TEXT          DEFAULT NULL,
    refresh_token_value           TEXT          DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_metadata        TEXT          DEFAULT NULL,
    user_code_value               TEXT          DEFAULT NULL,
    user_code_issued_at           TIMESTAMPTZ   DEFAULT NULL,
    user_code_expires_at          TIMESTAMPTZ   DEFAULT NULL,
    user_code_metadata            TEXT          DEFAULT NULL,
    device_code_value             TEXT          DEFAULT NULL,
    device_code_issued_at         TIMESTAMPTZ   DEFAULT NULL,
    device_code_expires_at        TIMESTAMPTZ   DEFAULT NULL,
    device_code_metadata          TEXT          DEFAULT NULL
);

CREATE TABLE oauth2_authorization_consent
(
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
