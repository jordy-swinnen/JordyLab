-- Bootstrap the dedicated schema for the Keycloak server inside the shared pgvector container.
-- Compose runs this file once at first init via /docker-entrypoint-initdb.d/.

CREATE SCHEMA IF NOT EXISTS keycloak;
GRANT ALL ON SCHEMA keycloak TO jordylab;
