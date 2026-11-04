---
name: flyway-migration
description: Create a properly named Flyway migration file with schema targeting and convention checks.
---

# Flyway Migration

## Gather Input

Ask the user for:
- **Target schema** (finance, gamecatalog, garmin, recipe, or new)
- **Description** of what the migration does (e.g., "add_portfolio_table")

## Create Migration

1. Determine the next version number using today's date: `V<yyyyMMdd>__<description>.sql`

2. Check for version conflicts:
   ```bash
   ls jordylab-be/src/main/resources/db/migration/V$(date +%Y%m%d)__* 2>/dev/null
   ```
   If a conflict exists, append a sequential suffix (e.g., `V20260315_2__...`).

3. Create the migration file at `jordylab-be/src/main/resources/db/migration/`:
   ```sql
   CREATE SCHEMA IF NOT EXISTS <schema>;
   SET search_path TO <schema>;

   -- Migration: <description>
   ```

4. If the migration involves vector columns, use `vector(1536)` and create an index with `vector_cosine_ops`.

5. Remind the user:
   - Never modify already-applied migrations
   - All schemas are owned by jordylab-be, including `garmin`
   - Spring Boot 4 requires `spring-boot-starter-flyway` dependency
   - The migration must work with the previous release's code still running (see Compatibility below)

## Compatibility with the previous release

A code rollback does not roll back the schema (spec 019, FR-019). The previous release is still running while the new
migration is applied, and it is what runs again after a rollback. So a migration must not break that release:

- **Expand, don't replace.** Adding a table, or a nullable column, or a column with a default is safe in one release.
- **Contract in a later release.** Dropping or renaming a column, table or index that the previous release still reads
  is two migrations: an expand step now (new column, backfill, the code reads the new one) and a contract step in a
  later release, once no running release reads the old one.
- **Required columns need a default or a backfill first.** A `NOT NULL` column without a default breaks every insert the
  previous release makes.
- **Say so in the migration header** when a step is deliberately expand-only, so the contract step is not forgotten.

## Verify

- Confirm the file parses as valid SQL
- Check that no existing migration with the same version exists
- List all migrations for the target schema for context
