CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 003: the resident Python client asks whether a scan is needed via POST /ingest/check
-- before uploading. It sends an opaque metadata fingerprint; the server stores that
-- fingerprint next to its own ingest-logic version, so a server-side parsing/validation
-- change forces re-ingestion regardless of the client's fingerprint.
ALTER TABLE scan_source ADD COLUMN machine_id TEXT;
ALTER TABLE scan_source ADD COLUMN last_client_digest TEXT;
ALTER TABLE scan_source ADD COLUMN ingest_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_source ADD COLUMN last_checked_at TIMESTAMPTZ;

-- A machine is identified by a stable generated id; hostname remains the fallback during
-- cutover (existing rows keep machine_id NULL until a client-003 scan adopts them).
CREATE UNIQUE INDEX uq_scan_source_machine_type
    ON scan_source (machine_id, source_type)
    WHERE machine_id IS NOT NULL;
