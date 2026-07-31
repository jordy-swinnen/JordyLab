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

## Verify

- Confirm the file parses as valid SQL
- Check that no existing migration with the same version exists
- List all migrations for the target schema for context
