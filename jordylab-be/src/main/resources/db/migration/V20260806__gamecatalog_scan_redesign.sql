CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- Wipe dev data: the old Python ingest pipeline (config.yaml, /sync endpoint, static
-- bearer token) is gone. New flow auto-registers sources from a downloaded script.
TRUNCATE scan_source, game, sync_report;

-- Drop the path column (paths are now runtime-selected on the host machine; the
-- script sends a directory listing at scan time).
ALTER TABLE scan_source DROP COLUMN path;

-- Drop the sequence column (per-source monotonic sequence was a property of the
-- polling Python sidecar; ad-hoc shell-script invocations don't need ordering).
ALTER TABLE scan_source DROP COLUMN last_sequence;
ALTER TABLE sync_report DROP COLUMN sequence;

-- Add hostname. Every ScanSource is now keyed on (hostname, source_type) — the
-- script reads the system hostname via `hostnamectl`/`hostname` and the backend
-- auto-creates a source if (hostname, libraryType) is unseen.
ALTER TABLE scan_source ADD COLUMN hostname TEXT NOT NULL DEFAULT '';
ALTER TABLE scan_source ALTER COLUMN hostname DROP DEFAULT;
ALTER TABLE scan_source ADD CONSTRAINT uq_scan_source_host_type UNIQUE (hostname, source_type);
