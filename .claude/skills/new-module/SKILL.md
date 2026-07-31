---
name: new-module
description: Scaffold a new Spring Modulith module with DDD package structure, Flyway migration, and frontend libs.
---

# New Module Scaffold

## Gather Input

Ask the user for:
- **Module name** (lowercase, e.g., `recipe`)
- **Schema name** (usually same as module name)
- **One-line description** of the module's purpose

## Backend Scaffold

1. Create the full DDD package structure under `jordylab-be/src/main/java/dev/jordy/jordylab/<module>/`:
   ```
   <module>/
   ├── <Module>Facade.java              # Public API — facade class
   ├── <Module>Dto.java                 # Public API — DTO record (if shared across modules)
   ├── domain/
   │   ├── repository/
   │   │   └── <Module>Repository.java  # Spring Data repository interface
   │   └── <Module>Entity.java          # JPA entity (read entity skill first)
   ├── rest/
   │   ├── client/                      # Outbound HTTP clients (RestClient-based) — create if needed
   │   └── controller/
   │       ├── model/                   # Request/response records scoped to this controller
   │       └── <Module>Controller.java
   ├── service/
   │   └── <Module>Service.java         # Application service orchestrating domain + clients
   └── util/                            # Stateless helpers (@UtilityClass) — create if needed
   ```

2. Create the initial Flyway migration at `jordylab-be/src/main/resources/db/migration/`:
   - Filename: `V<yyyyMMdd>__create_<schema>_schema.sql`
   - Content:
     ```sql
     CREATE SCHEMA IF NOT EXISTS <schema>;
     SET search_path TO <schema>;

     -- Initial table creation goes here
     ```

3. Create a placeholder test at `jordylab-be/src/test/java/dev/jordy/jordylab/<module>/`:
   - `<Module>IntegrationTest.java` annotated with `@ApplicationModuleTest`

## Entity

Read `.claude/skills/entity/SKILL.md` before creating the entity.

## Frontend Scaffold

1. Generate Nx libraries:
   ```bash
   cd jordylab-fe
   bunx nx generate @nx/angular:library --name=<module>-ui --directory=libs/<module>/ui --tags="scope:<module>,type:ui" --standalone
   bunx nx generate @nx/angular:library --name=<module>-api --directory=libs/<module>/api --tags="scope:<module>,type:api" --standalone
   ```

## Verify

Run the modularity-check skill to confirm boundaries are intact.