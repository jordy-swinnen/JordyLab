CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance;

-- Fixes V20260928005, which was missing the CREATE SCHEMA IF NOT EXISTS guard required by every
-- migration (jordylab-be/AGENTS.md's Flyway rules) — it only happened to work because an earlier
-- migration had already created the `finance` schema. V20260928005 itself cannot be edited (an
-- already-applied migration), so this is a no-op today and only guards against a from-scratch
-- database where V20260928005 would otherwise run before the schema exists.
